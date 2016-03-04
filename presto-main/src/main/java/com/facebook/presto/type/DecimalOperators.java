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
package com.facebook.presto.type;

import com.facebook.presto.metadata.Signature;
import com.facebook.presto.metadata.SqlScalarFunction;
import com.facebook.presto.metadata.SqlScalarFunctionBuilder;
import com.facebook.presto.metadata.SqlScalarFunctionBuilder.SpecializeContext;
import com.facebook.presto.operator.scalar.ScalarOperator;
import com.facebook.presto.spi.PrestoException;
import com.facebook.presto.spi.type.DecimalType;
import com.facebook.presto.spi.type.LongDecimalType;
import com.facebook.presto.spi.type.ShortDecimalType;
import com.google.common.collect.ImmutableList;
import io.airlift.slice.Slice;

import java.lang.invoke.MethodHandle;
import java.math.BigInteger;
import java.util.List;

import static com.facebook.presto.metadata.FunctionKind.SCALAR;
import static com.facebook.presto.metadata.OperatorType.ADD;
import static com.facebook.presto.metadata.OperatorType.DIVIDE;
import static com.facebook.presto.metadata.OperatorType.MODULUS;
import static com.facebook.presto.metadata.OperatorType.MULTIPLY;
import static com.facebook.presto.metadata.OperatorType.NEGATION;
import static com.facebook.presto.metadata.OperatorType.SUBTRACT;
import static com.facebook.presto.metadata.Signature.longVariableCalculation;
import static com.facebook.presto.spi.StandardErrorCode.DIVISION_BY_ZERO;
import static com.facebook.presto.util.DecimalUtils.checkOverflow;
import static java.lang.Integer.max;
import static java.math.BigInteger.ONE;
import static java.math.BigInteger.TEN;
import static java.math.BigInteger.ZERO;

public final class DecimalOperators
{
    public static final SqlScalarFunction DECIMAL_MULTIPLY_OPERATOR = decimalMultiplyOperator();
    public static final SqlScalarFunction DECIMAL_DIVIDE_OPERATOR = decimalDivideOperator();
    public static final SqlScalarFunction DECIMAL_MODULUS_OPERATOR = decimalModulusOperator();

    private DecimalOperators()
    {
    }

    @ScalarOperator(ADD)
    @LiteralParameters({"p", "s", "p2"})
    @LongVariableConstraint(variable = "p2", calculation = "min(38, p + 1)")
    @SqlType("decimal(p2, s)")
    public static long addShortShortShort(@SqlType("decimal(p, s)") long a, @SqlType("decimal(p, s)") long b)
    {
        return a + b;
    }

    @ScalarOperator(ADD)
    @LiteralParameters({"p", "s", "p2"})
    @LongVariableConstraint(variable = "p2", calculation = "min(38, p + 1)")
    @SqlType("decimal(p2, s)")
    public static Slice addShortShortLong(@SqlType("decimal(p, s)") long a, @SqlType("decimal(p, s)") long b)
    {
        BigInteger aBigInteger = BigInteger.valueOf(a);
        BigInteger bBigInteger = BigInteger.valueOf(b);
        BigInteger result = aBigInteger.add(bBigInteger);
        return LongDecimalType.unscaledValueToSlice(result);
    }

    @ScalarOperator(ADD)
    @LiteralParameters({"p", "s", "p2"})
    @LongVariableConstraint(variable = "p2", calculation = "min(38, p + 1)")
    @SqlType("decimal(p2, s)")
    public static Slice addLongLongLong(@SqlType("decimal(p, s)") Slice a, @SqlType("decimal(p, s)") Slice b)
    {
        BigInteger aBigInteger = LongDecimalType.unscaledValueToBigInteger(a);
        BigInteger bBigInteger = LongDecimalType.unscaledValueToBigInteger(b);
        BigInteger result = aBigInteger.add(bBigInteger);
        checkOverflow(result);
        return LongDecimalType.unscaledValueToSlice(result);
    }

    @ScalarOperator(SUBTRACT)
    @LiteralParameters({"p", "s", "p2"})
    @LongVariableConstraint(variable = "p2", calculation = "min(38, p + 1)")
    @SqlType("decimal(p2, s)")
    public static long subtractShortShortShort(@SqlType("decimal(p, s)") long a, @SqlType("decimal(p, s)") long b)
    {
        return a - b;
    }

    @ScalarOperator(SUBTRACT)
    @LiteralParameters({"p", "s", "p2"})
    @LongVariableConstraint(variable = "p2", calculation = "min(38, p + 1)")
    @SqlType("decimal(p2, s)")
    public static Slice subtractShortShortLong(@SqlType("decimal(p, s)") long a, @SqlType("decimal(p, s)") long b)
    {
        BigInteger aBigInteger = BigInteger.valueOf(a);
        BigInteger bBigInteger = BigInteger.valueOf(b);
        BigInteger result = aBigInteger.subtract(bBigInteger);
        return LongDecimalType.unscaledValueToSlice(result);
    }

    @ScalarOperator(SUBTRACT)
    @LiteralParameters({"p2", "p", "s"})
    @LongVariableConstraint(variable = "p2", calculation = "min(38, p + 1)")
    @SqlType("decimal(p2, s)")
    public static Slice subtractLongLongLong(@SqlType("decimal(p, s)") Slice a, @SqlType("decimal(p, s)") Slice b)
    {
        BigInteger aBigInteger = LongDecimalType.unscaledValueToBigInteger(a);
        BigInteger bBigInteger = LongDecimalType.unscaledValueToBigInteger(b);
        BigInteger result = aBigInteger.subtract(bBigInteger);
        checkOverflow(result);
        return LongDecimalType.unscaledValueToSlice(result);
    }

    private static SqlScalarFunction decimalMultiplyOperator()
    {
        Signature signature = Signature.builder()
                .kind(SCALAR)
                .operatorType(MULTIPLY)
                .literalParameters("a_precision", "a_scale", "b_precision", "b_scale", "r_precision", "r_scale")
                .longVariableConstraints(
                        longVariableCalculation("r_precision", "min(38, a_precision + b_precision)"),
                        longVariableCalculation("r_scale", "a_scale + b_scale")
                )
                .argumentTypes("decimal(a_precision, a_scale)", "decimal(b_precision, b_scale)")
                .returnType("decimal(r_precision, r_scale)")
                .build();
        return SqlScalarFunction.builder(DecimalOperators.class)
                .signature(signature)
                .methods("multiplyShortShortShort", "multiplyShortShortLong", "multiplyLongLongLong", "multiplyShortLongLong", "multiplyLongShortLong")
                .build();
    }

    public static long multiplyShortShortShort(long a, long b)
    {
        return a * b;
    }

    public static Slice multiplyShortShortLong(long a, long b)
    {
        BigInteger aBigInteger = BigInteger.valueOf(a);
        BigInteger bBigInteger = BigInteger.valueOf(b);
        return internalMultiplyLongLongLong(aBigInteger, bBigInteger);
    }

    public static Slice multiplyLongLongLong(Slice a, Slice b)
    {
        BigInteger aBigInteger = LongDecimalType.unscaledValueToBigInteger(a);
        BigInteger bBigInteger = LongDecimalType.unscaledValueToBigInteger(b);
        return internalMultiplyLongLongLong(aBigInteger, bBigInteger);
    }

    public static Slice multiplyShortLongLong(long a, Slice b)
    {
        BigInteger aBigInteger = BigInteger.valueOf(a);
        BigInteger bBigInteger = LongDecimalType.unscaledValueToBigInteger(b);
        return internalMultiplyLongLongLong(aBigInteger, bBigInteger);
    }

    public static Slice multiplyLongShortLong(Slice a, long b)
    {
        BigInteger aBigInteger = LongDecimalType.unscaledValueToBigInteger(a);
        BigInteger bBigInteger = BigInteger.valueOf(b);
        return internalMultiplyLongLongLong(aBigInteger, bBigInteger);
    }

    private static Slice internalMultiplyLongLongLong(BigInteger aBigInteger, BigInteger bBigInteger)
    {
        BigInteger result = aBigInteger.multiply(bBigInteger);
        checkOverflow(result);
        return LongDecimalType.unscaledValueToSlice(result);
    }

    private static SqlScalarFunction decimalDivideOperator()
    {
        // we extend target precision by b_scale. This is upper bound on how much division result will grow.
        // pessimistic case is a / 0.0000001
        // if scale of divisor is greater than scale of dividend we extend scale further as we
        // want result scale to be maximum of scales of divisor and dividend.
        Signature signature = Signature.builder()
                .kind(SCALAR)
                .operatorType(DIVIDE)
                .literalParameters("a_precision", "a_scale", "b_precision", "b_scale", "r_precision", "r_scale")
                .longVariableConstraints(
                        longVariableCalculation("r_precision", "min(38, a_precision + b_scale + max(b_scale - a_scale, 0))"),
                        longVariableCalculation("r_scale", "max(a_scale, b_scale)")
                )
                .argumentTypes("decimal(a_precision, a_scale)", "decimal(b_precision, b_scale)")
                .returnType("decimal(r_precision, r_scale)")
                .build();
        return SqlScalarFunction.builder(DecimalOperators.class)
                .signature(signature)
                .methods("divideShortShortShort")
                .extraParameters(DecimalOperators::shortDivideRescaleExtraParameter)
                .methods("divideShortShortLong", "divideLongLongLong", "divideShortLongLong", "divideLongShortLong")
                .extraParameters(DecimalOperators::longDivideRescaleExtraParameter)
                .build();
    }

    private static List<Object> shortDivideRescaleExtraParameter(SpecializeContext context)
    {
        int rescaleFactor = divideRescaleFactor(context);
        return ImmutableList.of(ShortDecimalType.tenToNth(rescaleFactor));
    }

    private static List<Object> longDivideRescaleExtraParameter(SpecializeContext context)
    {
        int rescaleFactor = divideRescaleFactor(context);
        return ImmutableList.of(LongDecimalType.tenToNth(rescaleFactor));
    }

    private static int divideRescaleFactor(SqlScalarFunctionBuilder.SpecializeContext context)
    {
        DecimalType returnType = (DecimalType) context.getReturnType();
        // +1 because we want to do computations with one extra decimal field to be able to handle rounding of the result.
        return (int) (returnType.getScale() - context.getLiteral("a_scale") + context.getLiteral("b_scale") + 1);
    }

    public static long divideShortShortShort(long a, long b, long aRescale)
    {
        try {
            long ret = a * aRescale / b;
            if (ret > 0) {
                if (ret % 10 >= 5) {
                    return ret / 10 + 1;
                }
                else {
                    return ret / 10;
                }
            }
            else {
                if (ret % 10 <= -5) {
                    return ret / 10 - 1;
                }
                else {
                    return ret / 10;
                }
            }
        }
        catch (ArithmeticException e) {
            throw new PrestoException(DIVISION_BY_ZERO, e);
        }
    }

    public static Slice divideShortShortLong(long a, long b, BigInteger aRescale)
    {
        BigInteger aBigInteger = BigInteger.valueOf(a).multiply(aRescale);
        BigInteger bBigInteger = BigInteger.valueOf(b);
        return internalDivideLongLongLong(aBigInteger, bBigInteger);
    }

    public static Slice divideLongLongLong(Slice a, Slice b, BigInteger aRescale)
    {
        BigInteger aBigInteger = LongDecimalType.unscaledValueToBigInteger(a).multiply(aRescale);
        BigInteger bBigInteger = LongDecimalType.unscaledValueToBigInteger(b);
        return internalDivideLongLongLong(aBigInteger, bBigInteger);
    }

    public static Slice divideShortLongLong(long a, Slice b, BigInteger aRescale)
    {
        BigInteger aBigInteger = BigInteger.valueOf(a).multiply(aRescale);
        BigInteger bBigInteger = LongDecimalType.unscaledValueToBigInteger(b);
        return internalDivideLongLongLong(aBigInteger, bBigInteger);
    }

    public static Slice divideLongShortLong(Slice a, long b, BigInteger aRescale)
    {
        BigInteger aBigInteger = LongDecimalType.unscaledValueToBigInteger(a).multiply(aRescale);
        BigInteger bBigInteger = BigInteger.valueOf(b);
        return internalDivideLongLongLong(aBigInteger, bBigInteger);
    }

    private boolean rescaleParamIsLong(MethodHandle baseMethodHandle)
    {
        return baseMethodHandle.type().parameterType(baseMethodHandle.type().parameterCount() - 1).isAssignableFrom(long.class);
    }

    private static Slice internalDivideLongLongLong(BigInteger aBigInteger, BigInteger bBigInteger)
    {
        try {
            BigInteger result = aBigInteger.divide(bBigInteger);
            BigInteger resultModTen = result.mod(TEN);
            if (result.signum() > 0) {
                if (resultModTen.compareTo(BigInteger.valueOf(5)) >= 0) {
                    result = result.divide(TEN).add(ONE);
                }
                else {
                    result = result.divide(TEN);
                }
            }
            else {
                if (resultModTen.compareTo(BigInteger.valueOf(5)) < 0 && !resultModTen.equals(ZERO)) {
                    result = result.divide(TEN).subtract(ONE);
                }
                else {
                    result = result.divide(TEN);
                }
            }
            checkOverflow(result);
            return LongDecimalType.unscaledValueToSlice(result);
        }
        catch (ArithmeticException e) {
            throw new PrestoException(DIVISION_BY_ZERO, e);
        }
    }

    private static SqlScalarFunction decimalModulusOperator()
    {
        Signature signature = Signature.builder()
                .kind(SCALAR)
                .operatorType(MODULUS)
                .literalParameters("a_precision", "a_scale", "b_precision", "b_scale", "r_precision", "r_scale")
                .longVariableConstraints(
                        longVariableCalculation("r_precision", "min(b_precision - b_scale, a_precision - a_scale) + max(a_scale, b_scale)"),
                        longVariableCalculation("r_scale", "max(a_scale, b_scale)")
                )
                .argumentTypes("decimal(a_precision, a_scale)", "decimal(b_precision, b_scale)")
                .returnType("decimal(r_precision, r_scale)")
                .build();
        return SqlScalarFunction.builder(DecimalOperators.class)
                .signature(signature)
                .methods("modulusShortShortShort", "modulusLongLongLong", "modulusShortLongLong", "modulusShortLongShort", "modulusLongShortShort", "modulusLongShortLong")
                .extraParameters(DecimalOperators::longRescaleExtraParameters)
                .build();
    }

    private static List<Object> longRescaleExtraParameters(SpecializeContext context)
    {
        BigInteger aRescale = LongDecimalType.tenToNth(rescaleFactor(context.getLiteral("a_scale"), context.getLiteral("b_scale")));
        BigInteger bRescale = LongDecimalType.tenToNth(rescaleFactor(context.getLiteral("b_scale"), context.getLiteral("a_scale")));
        return ImmutableList.of(aRescale, bRescale);
    }

    private static int rescaleFactor(long fromScale, long toScale)
    {
        return max(0, (int) toScale - (int) fromScale);
    }

    public static long modulusShortShortShort(long a, long b, BigInteger aRescale, BigInteger bRescale)
    {
        BigInteger aBigInteger = BigInteger.valueOf(a);
        BigInteger bBigInteger = BigInteger.valueOf(b);
        return internalModulusShortResult(aBigInteger, bBigInteger, aRescale, bRescale);
    }

    public static Slice modulusLongLongLong(Slice a, Slice b, BigInteger aRescale, BigInteger bRescale)
    {
        BigInteger aBigInteger = LongDecimalType.unscaledValueToBigInteger(a);
        BigInteger bBigInteger = LongDecimalType.unscaledValueToBigInteger(b);
        return internalModulusLongResult(aBigInteger, bBigInteger, aRescale, bRescale);
    }

    public static Slice modulusShortLongLong(long a, Slice b, BigInteger aRescale, BigInteger bRescale)
    {
        BigInteger aBigInteger = BigInteger.valueOf(a);
        BigInteger bBigInteger = LongDecimalType.unscaledValueToBigInteger(b);
        return internalModulusLongResult(aBigInteger, bBigInteger, aRescale, bRescale);
    }

    public static long modulusShortLongShort(long a, Slice b, BigInteger aRescale, BigInteger bRescale)
    {
        BigInteger aBigInteger = BigInteger.valueOf(a).multiply(aRescale);
        BigInteger bBigInteger = LongDecimalType.unscaledValueToBigInteger(b).multiply(bRescale);
        return internalModulusShortResult(aBigInteger, bBigInteger, aRescale, bRescale);
    }

    public static long modulusLongShortShort(Slice a, long b, BigInteger aRescale, BigInteger bRescale)
    {
        BigInteger aBigInteger = LongDecimalType.unscaledValueToBigInteger(a);
        BigInteger bBigInteger = BigInteger.valueOf(b);
        return internalModulusShortResult(aBigInteger, bBigInteger, aRescale, bRescale);
    }

    public static Slice modulusLongShortLong(Slice a, long b, BigInteger aRescale, BigInteger bRescale)
    {
        BigInteger aBigInteger = LongDecimalType.unscaledValueToBigInteger(a);
        BigInteger bBigInteger = BigInteger.valueOf(b);
        return internalModulusLongResult(aBigInteger, bBigInteger, aRescale, bRescale);
    }

    private static long internalModulusShortResult(BigInteger aBigInteger, BigInteger bBigInteger, BigInteger aRescale, BigInteger bRescale)
    {
        try {
            return aBigInteger.multiply(aRescale).remainder(bBigInteger.multiply(bRescale)).longValue();
        }
        catch (ArithmeticException e) {
            throw new PrestoException(DIVISION_BY_ZERO, e);
        }
    }

    private static Slice internalModulusLongResult(BigInteger aBigInteger, BigInteger bBigInteger, BigInteger aRescale, BigInteger bRescale)
    {
        try {
            BigInteger result = aBigInteger.multiply(aRescale).remainder(bBigInteger.multiply(bRescale));
            return LongDecimalType.unscaledValueToSlice(result);
        }
        catch (ArithmeticException e) {
            throw new PrestoException(DIVISION_BY_ZERO, e);
        }
    }

    @ScalarOperator(NEGATION)
    @LiteralParameters({"p", "s"})
    @SqlType("decimal(p, s)")
    public static long negate(@SqlType("decimal(p, s)") long arg)
    {
        return -arg;
    }

    @ScalarOperator(NEGATION)
    @LiteralParameters({"p", "s"})
    @SqlType("decimal(p, s)")
    public static Slice negate(@SqlType("decimal(p, s)") Slice arg)
    {
        BigInteger argBigInteger = LongDecimalType.unscaledValueToBigInteger(arg);
        return LongDecimalType.unscaledValueToSlice(argBigInteger.negate());
    }
}
