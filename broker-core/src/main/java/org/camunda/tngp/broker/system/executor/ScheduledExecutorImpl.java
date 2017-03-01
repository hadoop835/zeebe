/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.broker.system.executor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.camunda.tngp.util.agent.AgentRunnerService;
import org.camunda.tngp.util.time.ClockUtil;

public class ScheduledExecutorImpl implements Agent, ScheduledExecutor
{
    protected static final String NAME = "scheduled-executor";

    protected final List<ScheduledCommandImpl> scheduledCommands = new ArrayList<>();
    protected final List<ScheduledCommandImpl> reScheduledCommands = new ArrayList<>();

    protected final ManyToOneConcurrentArrayQueue<Runnable> cmdQueue = new ManyToOneConcurrentArrayQueue<>(100);
    protected final Consumer<Runnable> cmdConsumer = Runnable::run;

    protected final AtomicBoolean isRunning = new AtomicBoolean(false);
    protected final AgentRunnerService agentRunnerService;

    public ScheduledExecutorImpl(AgentRunnerService agentRunnerService)
    {
        this.agentRunnerService = agentRunnerService;
    }

    @Override
    public ScheduledCommand schedule(Runnable command, Duration delay)
    {
        final long dueDate = ClockUtil.getCurrentTimeInMillis() + delay.toMillis();
        final ScheduledCommandImpl scheduledCommand = new ScheduledCommandImpl(command, dueDate);

        cmdQueue.add(() -> scheduledCommands.add(scheduledCommand));

        return scheduledCommand;
    }

    @Override
    public ScheduledCommand scheduleAtFixedRate(Runnable command, Duration period)
    {
        final long dueDate = ClockUtil.getCurrentTimeInMillis();
        final ScheduledCommandImpl scheduledCommand = new ScheduledCommandImpl(command, dueDate, period.toMillis());

        cmdQueue.add(() -> scheduledCommands.add(scheduledCommand));

        return scheduledCommand;
    }

    @Override
    public ScheduledCommand scheduleAtFixedRate(Runnable command, Duration initialDelay, Duration period)
    {
        final long dueDate = ClockUtil.getCurrentTimeInMillis() + initialDelay.toMillis();
        final ScheduledCommandImpl scheduledCommand = new ScheduledCommandImpl(command, dueDate, period.toMillis());

        cmdQueue.add(() -> scheduledCommands.add(scheduledCommand));

        return scheduledCommand;
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = 0;

        workCount += cmdQueue.drain(cmdConsumer);

        final long now = ClockUtil.getCurrentTimeInMillis();

        final Iterator<ScheduledCommandImpl> iterator = scheduledCommands.iterator();
        while (iterator.hasNext() && isRunning.get())
        {
            final ScheduledCommandImpl scheduledCommand = iterator.next();

            if (scheduledCommand.getDueDate() <= now)
            {
                workCount += 1;

                iterator.remove();

                final boolean reSchedule = executeCommand(scheduledCommand);

                if (reSchedule)
                {
                    reScheduledCommands.add(scheduledCommand);
                }
            }
        }

        scheduledCommands.addAll(reScheduledCommands);
        reScheduledCommands.clear();

        return workCount;
    }

    protected boolean executeCommand(final ScheduledCommandImpl scheduledCommand)
    {
        boolean reSchedule = false;

        if (!scheduledCommand.isCancelled())
        {
            try
            {
                scheduledCommand.getCommand().run();

                final long period = scheduledCommand.getPeriod();
                if (period >= 0)
                {
                    final long nextDueDate = ClockUtil.getCurrentTimeInMillis() + period;
                    scheduledCommand.setDueDateInMillis(nextDueDate);

                    reSchedule = true;
                }
            }
            catch (Exception e)
            {
                System.err.println("Failed to execute scheduled command.");
                e.printStackTrace();
            }
        }

        return reSchedule;
    }

    public void start()
    {
        if (isRunning.compareAndSet(false, true))
        {
            agentRunnerService.run(this);
        }
    }

    public void stop()
    {
        stopAsync().join();
    }

    public CompletableFuture<Void> stopAsync()
    {
        final CompletableFuture<Void> future = new CompletableFuture<>();

        if (isRunning.compareAndSet(true, false))
        {
            cmdQueue.add(() ->
            {
                agentRunnerService.remove(this);

                future.complete(null);
            });
        }
        else
        {
            future.complete(null);
        }

        return future;
    }

    @Override
    public String roleName()
    {
        return NAME;
    }

    class ScheduledCommandImpl implements ScheduledCommand
    {
        protected final Runnable command;
        protected final long periodInMillis;

        protected long dueDateInMillis;

        protected boolean isCancelled = false;

        ScheduledCommandImpl(Runnable command, long dueDateInMillis)
        {
            this(command, dueDateInMillis, -1L);
        }

        ScheduledCommandImpl(Runnable command, long dueDateInMillis, long periodInMillis)
        {
            this.command = command;
            this.periodInMillis = periodInMillis;
            this.dueDateInMillis = dueDateInMillis;
        }

        public Runnable getCommand()
        {
            return command;
        }

        @Override
        public long getPeriod()
        {
            return periodInMillis;
        }

        @Override
        public void cancel()
        {
            isCancelled = true;
        }

        @Override
        public boolean isCancelled()
        {
            return isCancelled;
        }

        @Override
        public long getDueDate()
        {
            return dueDateInMillis;
        }

        public void setDueDateInMillis(long dueDateInMillis)
        {
            this.dueDateInMillis = dueDateInMillis;
        }

    }

}