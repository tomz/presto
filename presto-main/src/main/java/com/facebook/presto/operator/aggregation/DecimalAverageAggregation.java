/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.operator.aggregation;

import com.facebook.presto.bytecode.DynamicClassLoader;
import com.facebook.presto.metadata.BoundVariables;
import com.facebook.presto.metadata.FunctionRegistry;
import com.facebook.presto.metadata.Signature;
import com.facebook.presto.metadata.SqlAggregationFunction;
import com.facebook.presto.operator.aggregation.state.AccumulatorState;
import com.facebook.presto.operator.aggregation.state.AccumulatorStateSerializer;
import com.facebook.presto.operator.aggregation.state.BigIntegerAndLongState;
import com.facebook.presto.operator.aggregation.state.BigIntegerAndLongStateFactory;
import com.facebook.presto.operator.aggregation.state.BigIntegerAndLongStateSerializer;
import com.facebook.presto.spi.block.Block;
import com.facebook.presto.spi.block.BlockBuilder;
import com.facebook.presto.spi.type.DecimalType;
import com.facebook.presto.spi.type.LongDecimalType;
import com.facebook.presto.spi.type.ShortDecimalType;
import com.facebook.presto.spi.type.Type;
import com.facebook.presto.spi.type.TypeManager;
import com.google.common.collect.ImmutableList;

import java.lang.invoke.MethodHandle;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import static com.facebook.presto.operator.aggregation.AggregationMetadata.ParameterMetadata;
import static com.facebook.presto.operator.aggregation.AggregationMetadata.ParameterMetadata.ParameterType.BLOCK_INDEX;
import static com.facebook.presto.operator.aggregation.AggregationMetadata.ParameterMetadata.ParameterType.BLOCK_INPUT_CHANNEL;
import static com.facebook.presto.operator.aggregation.AggregationMetadata.ParameterMetadata.ParameterType.STATE;
import static com.facebook.presto.operator.aggregation.AggregationUtils.generateAggregationName;
import static com.facebook.presto.spi.type.LongDecimalType.unscaledValueToBigInteger;
import static com.facebook.presto.spi.type.StandardTypes.DECIMAL;
import static com.facebook.presto.util.Reflection.methodHandle;
import static com.google.common.base.Preconditions.checkArgument;
import static java.math.BigDecimal.ROUND_HALF_UP;

public class DecimalAverageAggregation
        extends SqlAggregationFunction
{
    public static final DecimalAverageAggregation DECIMAL_AVERAGE_AGGREGATION = new DecimalAverageAggregation();
    private static final String NAME = "avg";
    private static final MethodHandle SHORT_DECIMAL_INPUT_FUNCTION = methodHandle(DecimalAverageAggregation.class, "inputShortDecimal", Type.class, BigIntegerAndLongState.class, Block.class, int.class);
    private static final MethodHandle LONG_DECIMAL_INPUT_FUNCTION = methodHandle(DecimalAverageAggregation.class, "inputLongDecimal", Type.class, BigIntegerAndLongState.class, Block.class, int.class);

    private static final MethodHandle SHORT_DECIMAL_OUTPUT_FUNCTION = methodHandle(DecimalAverageAggregation.class, "outputShortDecimal", ShortDecimalType.class, BigIntegerAndLongState.class, BlockBuilder.class);
    private static final MethodHandle LONG_DECIMAL_OUTPUT_FUNCTION = methodHandle(DecimalAverageAggregation.class, "outputLongDecimal", LongDecimalType.class, BigIntegerAndLongState.class, BlockBuilder.class);

    private static final MethodHandle COMBINE_FUNCTION = methodHandle(DecimalAverageAggregation.class, "combine", BigIntegerAndLongState.class, BigIntegerAndLongState.class);

    public DecimalAverageAggregation()
    {
        super(NAME, ImmutableList.of(Signature.withVariadicBound("T", DECIMAL)), ImmutableList.of(), "T", ImmutableList.of("T"));
    }

    @Override
    public String getDescription()
    {
        return "Calculates the average value";
    }

    @Override
    public InternalAggregationFunction specialize(BoundVariables boundVariables, int arity, TypeManager typeManager, FunctionRegistry functionRegistry)
    {
        Type type = boundVariables.getTypeVariable("T");
        return generateAggregation(type);
    }

    private static InternalAggregationFunction generateAggregation(Type type)
    {
        checkArgument(type instanceof DecimalType, "type must be Decimal");
        DynamicClassLoader classLoader = new DynamicClassLoader(DecimalAverageAggregation.class.getClassLoader());
        List<Type> inputTypes = ImmutableList.of(type);
        MethodHandle inputFunction;
        MethodHandle outputFunction;
        Class<? extends AccumulatorState> stateInterface = BigIntegerAndLongState.class;
        AccumulatorStateSerializer<?> stateSerializer = new BigIntegerAndLongStateSerializer();

        if (((DecimalType) type).isShort()) {
            inputFunction = SHORT_DECIMAL_INPUT_FUNCTION;
            outputFunction = SHORT_DECIMAL_OUTPUT_FUNCTION;
        }
        else {
            inputFunction = LONG_DECIMAL_INPUT_FUNCTION;
            outputFunction = LONG_DECIMAL_OUTPUT_FUNCTION;
        }
        inputFunction = inputFunction.bindTo(type);
        outputFunction = outputFunction.bindTo(type);

        AggregationMetadata metadata = new AggregationMetadata(
                generateAggregationName(NAME, type, inputTypes),
                createInputParameterMetadata(type),
                inputFunction,
                null,
                null,
                COMBINE_FUNCTION,
                outputFunction,
                stateInterface,
                stateSerializer,
                new BigIntegerAndLongStateFactory(),
                type,
                false);

        Type intermediateType = stateSerializer.getSerializedType();
        GenericAccumulatorFactoryBinder factory = new AccumulatorCompiler().generateAccumulatorFactoryBinder(metadata, classLoader);
        return new InternalAggregationFunction(NAME, inputTypes, intermediateType, type, true, false, factory);
    }

    private static List<ParameterMetadata> createInputParameterMetadata(Type type)
    {
        return ImmutableList.of(new ParameterMetadata(STATE), new ParameterMetadata(BLOCK_INPUT_CHANNEL, type), new ParameterMetadata(BLOCK_INDEX));
    }

    public static void inputShortDecimal(Type type, BigIntegerAndLongState state, Block block, int position)
    {
        accumulateValueInState(BigInteger.valueOf(type.getLong(block, position)), state);
    }

    public static void inputLongDecimal(Type type, BigIntegerAndLongState state, Block block, int position)
    {
        accumulateValueInState(unscaledValueToBigInteger(type.getSlice(block, position)), state);
    }

    private static void accumulateValueInState(BigInteger value, BigIntegerAndLongState state)
    {
        initializeIfNeeded(state);
        state.setBigInteger(state.getBigInteger().add(value));
        state.setLong(state.getLong() + 1);
    }

    private static void initializeIfNeeded(BigIntegerAndLongState state)
    {
        if (state.getBigInteger() == null) {
            state.setBigInteger(BigInteger.valueOf(0));
        }
    }

    public static void combine(BigIntegerAndLongState state, BigIntegerAndLongState otherState)
    {
        state.setLong(state.getLong() + otherState.getLong());
        if (state.getBigInteger() == null) {
            state.setBigInteger(otherState.getBigInteger());
        }
        else {
            state.setBigInteger(state.getBigInteger().add(otherState.getBigInteger()));
        }
    }

    public static void outputShortDecimal(ShortDecimalType type, BigIntegerAndLongState state, BlockBuilder out)
    {
        if (state.getLong() == 0) {
            out.appendNull();
        }
        else {
            type.writeLong(out, average(state, type).unscaledValue().longValueExact());
        }
    }

    public static void outputLongDecimal(LongDecimalType type, BigIntegerAndLongState state, BlockBuilder out)
    {
        if (state.getLong() == 0) {
            out.appendNull();
        }
        else {
            type.writeBigDecimal(out, average(state, type));
        }
    }

    private static BigDecimal average(BigIntegerAndLongState state, DecimalType type)
    {
        BigDecimal sum = new BigDecimal(state.getBigInteger(), type.getScale());
        BigDecimal count = BigDecimal.valueOf(state.getLong());
        return sum.divide(count, type.getScale(), ROUND_HALF_UP);
    }
}
