/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.benchmarks.msgpack;

import java.util.concurrent.TimeUnit;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import io.zeebe.broker.taskqueue.data.TaskEvent;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
public class POJOMappingBenchmark
{

    @Benchmark
    @Threads(1)
    public void performReadingOptimalOrder(POJOMappingContext ctx)
    {
        final TaskEvent taskEvent = ctx.getTaskEvent();
        final DirectBuffer encodedTaskEvent = ctx.getOptimalOrderEncodedTaskEvent();

        taskEvent.reset();
        taskEvent.wrap(encodedTaskEvent, 0, encodedTaskEvent.capacity());
    }

    @Benchmark
    @Threads(1)
    public void performReadingReverseOrder(POJOMappingContext ctx)
    {
        final TaskEvent taskEvent = ctx.getTaskEvent();
        final DirectBuffer encodedTaskEvent = ctx.getReverseOrderEncodedTaskEvent();

        taskEvent.reset();
        taskEvent.wrap(encodedTaskEvent, 0, encodedTaskEvent.capacity());
    }

    @Benchmark
    @Threads(1)
    public void performMappingCycleOptimalEncodedOrder(POJOMappingContext ctx) throws Exception
    {
        final TaskEvent taskEvent = ctx.getTaskEvent();
        final DirectBuffer encodedTaskEvent = ctx.getOptimalOrderEncodedTaskEvent();
        final MutableDirectBuffer writeBuffer = ctx.getWriteBuffer();

        taskEvent.reset();
        taskEvent.wrap(encodedTaskEvent, 0, encodedTaskEvent.capacity());
        taskEvent.write(writeBuffer, 0);
    }

    @Benchmark
    @Threads(1)
    public void performMappingCycleReverseEncodedOrder(POJOMappingContext ctx) throws Exception
    {
        final TaskEvent taskEvent = ctx.getTaskEvent();
        final DirectBuffer encodedTaskEvent = ctx.getReverseOrderEncodedTaskEvent();
        final MutableDirectBuffer writeBuffer = ctx.getWriteBuffer();

        taskEvent.reset();
        taskEvent.wrap(encodedTaskEvent, 0, encodedTaskEvent.capacity());
        taskEvent.write(writeBuffer, 0);
    }


}
