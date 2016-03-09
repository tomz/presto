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
package com.facebook.presto.operator;

import com.facebook.presto.RowPagesBuilder;
import com.facebook.presto.operator.HashBuilderOperator.HashBuilderOperatorFactory;
import com.facebook.presto.spi.Page;
import com.facebook.presto.spi.type.Type;
import com.facebook.presto.sql.planner.plan.PlanNodeId;
import com.facebook.presto.testing.TestingTaskContext;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import io.airlift.units.DataSize;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.VerboseMode;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import static com.facebook.presto.RowPagesBuilder.rowPagesBuilder;
import static com.facebook.presto.SessionTestUtils.TEST_SESSION;
import static com.facebook.presto.spi.type.BigintType.BIGINT;
import static com.facebook.presto.spi.type.VarcharType.VARCHAR;
import static com.facebook.presto.util.Threads.checkNotSameThreadExecutor;
import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static io.airlift.units.DataSize.Unit.GIGABYTE;
import static java.lang.String.format;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.openjdk.jmh.annotations.Mode.AverageTime;
import static org.openjdk.jmh.annotations.Scope.Thread;

@State(Thread)
@OutputTimeUnit(MILLISECONDS)
@BenchmarkMode(AverageTime)
@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
public class BenchmarkHashBuildAndJoinOperators
{
    private static final int HASH_BUILD_OPERATOR_ID = 1;
    private static final int HASH_JOIN_OPERATOR_ID = 2;
    private static final PlanNodeId TEST_PLAN_NODE_ID = new PlanNodeId("test");

    @State(Thread)
    public static class Context
    {
        private static int rowsNumber = 700_000;

        @Param({"varchar", "bigint", "all"})
        private String hashColumns;

        @Param({"false", "true"})
        private boolean buildHashEnabled;

        @Param({"1", "5"})
        private int valuesRepetition;

        private ExecutorService executor;
        private List<Page> pages;
        private Optional<Integer> hashChannel;
        private List<Type> types;
        private List<Integer> hashChannels;

        @Setup
        public void setup()
        {
            switch (hashColumns) {
                case "varchar":
                    hashChannels = Ints.asList(0);
                    break;
                case "bigint":
                    hashChannels = Ints.asList(1);
                    break;
                case "all":
                    hashChannels = Ints.asList(0, 1, 2);
                    break;
                default:
                    throw new UnsupportedOperationException(format("Unknown hashColumns value [%s]", hashColumns));
            }
            executor = newCachedThreadPool(daemonThreadsNamed("test-%s"));

            RowPagesBuilder pagesBuilder = rowPagesBuilder(buildHashEnabled, hashChannels, ImmutableList.of(VARCHAR, BIGINT, BIGINT));

            for (int i = 0; i < valuesRepetition; i++) {
                pagesBuilder.addSequencePage(rowsNumber / valuesRepetition, 20, 30, 40);
            }

            types = pagesBuilder.getTypes();
            pages = pagesBuilder.build();
            hashChannel = pagesBuilder.getHashChannel();
        }

        public TaskContext createTaskContext()
        {
            return TestingTaskContext.createTaskContext(
                    checkNotSameThreadExecutor(executor, "executor is null"),
                    TEST_SESSION,
                    new DataSize(2, GIGABYTE));
        }

        public List<Page> getPages()
        {
            return pages;
        }

        public Optional<Integer> getHashChannel()
        {
            return hashChannel;
        }

        public List<Integer> getHashChannels()
        {
            return hashChannels;
        }

        public List<Type> getTypes()
        {
            return types;
        }
    }

    @Benchmark
    public LookupSourceSupplier benchmarkBuildHash(Context context)
    {
        DriverContext driverContext = context.createTaskContext().addPipelineContext(true, true).addDriverContext();

        HashBuilderOperatorFactory hashBuilderOperatorFactory =
                new HashBuilderOperatorFactory(HASH_BUILD_OPERATOR_ID, TEST_PLAN_NODE_ID, context.getTypes(), context.getHashChannels(), context.getHashChannel(), 100);

        OperatorAssertion.toPages(hashBuilderOperatorFactory.createOperator(driverContext), context.getPages());

        return hashBuilderOperatorFactory.getLookupSourceSupplier();
    }

    @Benchmark
    public void benchmarkBuildAndJoinHash(Context context)
    {
        LookupSourceSupplier lookupSourceSupplier = benchmarkBuildHash(context);

        OperatorFactory joinOperatorFactory = LookupJoinOperators.innerJoin(
                HASH_JOIN_OPERATOR_ID,
                TEST_PLAN_NODE_ID,
                lookupSourceSupplier,
                context.getTypes(),
                context.getHashChannels(),
                context.getHashChannel());

        DriverContext driverContext = context.createTaskContext().addPipelineContext(true, true).addDriverContext();
        Operator joinOperator = joinOperatorFactory.createOperator(driverContext);

        OperatorAssertion.toPages(joinOperator, context.getPages());
    }

    public static void main(String[] args)
            throws RunnerException
    {
        Options options = new OptionsBuilder()
                .verbosity(VerboseMode.NORMAL)
                .include(".*" + BenchmarkHashBuildAndJoinOperators.class.getSimpleName() + ".*")
                .build();

        new Runner(options).run();
    }
}
