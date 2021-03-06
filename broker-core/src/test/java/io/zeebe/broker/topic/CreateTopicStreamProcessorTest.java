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
package io.zeebe.broker.topic;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.zeebe.broker.clustering.management.Partition;
import io.zeebe.broker.clustering.management.PartitionManager;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.system.log.PartitionEvent;
import io.zeebe.broker.system.log.PartitionState;
import io.zeebe.broker.system.log.PartitionsIndex;
import io.zeebe.broker.system.log.ResolvePendingPartitionsCommand;
import io.zeebe.broker.system.log.SystemPartitionManager;
import io.zeebe.broker.system.log.TopicEvent;
import io.zeebe.broker.system.log.TopicState;
import io.zeebe.broker.system.log.TopicsIndex;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;
import io.zeebe.util.buffer.BufferUtil;

public class CreateTopicStreamProcessorTest
{

    public static final String STREAM_NAME = "stream";

    public TemporaryFolder tempFolder = new TemporaryFolder();
    public AutoCloseableRule closeables = new AutoCloseableRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(tempFolder).around(closeables);

    @Mock
    public ServerOutput output;

    public PartitionManagerImpl partitionManager;
    protected TestStreams streams;

    private TypedStreamProcessor streamProcessor;
    private ResolvePendingPartitionsCommand checkPartitionsCmd;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        when(output.sendResponse(any())).thenReturn(true);

        final ActorScheduler scheduler = ActorSchedulerBuilder.createDefaultScheduler("foo");
        closeables.manage(scheduler);

        streams = new TestStreams(tempFolder.getRoot(), closeables, scheduler);
        final LogStream stream = streams.createLogStream(STREAM_NAME);

        this.partitionManager = new PartitionManagerImpl();

        final TopicsIndex topicsIndex = new TopicsIndex();
        final PartitionsIndex partitionsIndex = new PartitionsIndex();

        final TypedStreamEnvironment streamEnvironment = new TypedStreamEnvironment(stream, output)
            .withEventType(EventType.TOPIC_EVENT, TopicEvent.class)
            .withEventType(EventType.PARTITION_EVENT, PartitionEvent.class);

        streamEnvironment.buildStreamWriter();

        checkPartitionsCmd = new ResolvePendingPartitionsCommand(partitionsIndex, partitionManager, streamEnvironment.buildStreamWriter());

        streamProcessor = SystemPartitionManager.buildSystemStreamProcessor(streamEnvironment, partitionManager, topicsIndex, partitionsIndex);
    }


    /**
     * Tests the case where the stream processor is slower than the interval in which
     * we check the gossip state for the leader of any pending partitions.
     */
    @Test
    public void shouldRejectSecondPartitionCompleteCommand()
    {
        // given
        // stream processor is registered and active; configured to block on first partition creating event
        final StreamProcessorControl processorControl = streams.runStreamProcessor(STREAM_NAME, streamProcessor);

        processorControl.blockAfterEvent(e ->
            Events.isPartitionEvent(e) &&
            Events.asPartitionEvent(e).getState() == PartitionState.CREATING);
        processorControl.unblock();

        // issuing create topic command
        streams.newEvent(STREAM_NAME)
            .event(createTopic("foo", 1))
            .write();

        // waiting for partition creating event => the stream processor is now suspended
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING)
            .findFirst()
            .isPresent());

        partitionManager.makePartitionAvailable("foo", 0);

        // calling check pending partition once
        streamProcessor.runAsync(checkPartitionsCmd);

        // waiting for partition creation complete command
        waitUntil(() -> partitionEventsInState(PartitionState.CREATE_COMPLETE)
            .findFirst()
            .isPresent());

        // when
        // calling check pending partition again
        streamProcessor.runAsync(checkPartitionsCmd);

        // waiting for partition creation complete command
        waitUntil(() -> partitionEventsInState(PartitionState.CREATE_COMPLETE)
            .count() == 2);

        // and resuming stream processing
        processorControl.unblock();

        // then
        waitUntil(() -> partitionEventsInState(PartitionState.CREATE_COMPLETE_REJECTED)
            .findFirst()
            .isPresent());

        final List<PartitionEvent> partitionEvents = streams.events(STREAM_NAME)
            .filter(Events::isPartitionEvent)
            .map(Events::asPartitionEvent)
            .collect(Collectors.toList());

        assertThat(partitionEvents).extracting("state")
            .containsExactly(
                    PartitionState.CREATE,
                    PartitionState.CREATING,
                    PartitionState.CREATE_COMPLETE,
                    PartitionState.CREATE_COMPLETE,
                    PartitionState.CREATED,
                    PartitionState.CREATE_COMPLETE_REJECTED);
    }

    @Test
    public void shouldNotCreatePartitionsOnRejection()
    {
        // given
        final StreamProcessorControl processorControl = streams.runStreamProcessor(STREAM_NAME, streamProcessor);
        processorControl.unblock();

        // a first topic is created
        final TopicEvent createTopicCommand = createTopic("foo", 1);
        streams.newEvent(STREAM_NAME)
            .event(createTopicCommand)
            .write();

        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).findFirst().isPresent());
        partitionManager.makePartitionAvailable("foo", 0);
        streamProcessor.runAsync(checkPartitionsCmd);

        waitUntil(() -> topicEventsInState(TopicState.CREATED).findFirst().isPresent());

        // when creating the same topic again
        streams.newEvent(STREAM_NAME)
            .event(createTopicCommand)
            .write();

        // then
        waitUntil(() -> topicEventsInState(TopicState.CREATE_REJECTED).findFirst().isPresent());

        // only the first create topic command resulted in a partition
        assertThat(partitionEventsInState(PartitionState.CREATE).count()).isEqualTo(1);
    }

    @Test
    @Ignore("https://github.com/zeebe-io/zeebe/issues/415")
    public void shouldNotResendPartitionRequestOnRecovery() throws InterruptedException
    {
        // given
        final StreamProcessorControl processorControl = streams.runStreamProcessor(STREAM_NAME, streamProcessor);

        processorControl.blockAfterEvent(e ->
            Events.isTopicEvent(e) &&
            Events.asTopicEvent(e).getState() == TopicState.CREATE);
        processorControl.unblock();

        streams.newEvent(STREAM_NAME)
            .event(createTopic("foo", 1))
            .write();

        // stream processor has processed CREATE and triggered the cluster manager to create a partition
        waitUntil(() -> partitionEventsInState(PartitionState.CREATE).findFirst().isPresent());

        // when restarting the stream processor
        processorControl.close();
        processorControl.start();
        processorControl.unblock();

        // then the same partition has not been created after restart
        // (because we cannot be sure the partition has not been created before yet)
        Thread.sleep(500L); // not explicity condition we can wait for
        assertThat(partitionManager.getPartitionRequests()).isEqualTo(0);
    }

    @Test
    public void shouldNotSendResponseTwiceOnInterleavingPartitionCompletion()
    {
        // given
        final StreamProcessorControl processorControl = streams.runStreamProcessor(STREAM_NAME, streamProcessor);

        // wait after partition CREATE events have been written
        processorControl.blockAfterEvent(e ->
            Events.isTopicEvent(e) &&
            Events.asTopicEvent(e).getState() == TopicState.CREATE);
        processorControl.unblock();

        streams.newEvent(STREAM_NAME)
            .event(createTopic("foo", 2))
            .write();

        waitUntil(() -> partitionEventsInState(PartitionState.CREATE).count() == 2);
        final LoggedEvent secondCreateEvent = streams.events(STREAM_NAME)
            .filter(e -> Events.isPartitionEvent(e))
            .skip(1)
            .findFirst()
            .get();

        // wait after create event has been processed (=> and added to partition index singalling a pending partition)
        processorControl.blockAfterEvent(e -> e.getPosition() == secondCreateEvent.getPosition());
        processorControl.unblock();
        waitUntil(() -> processorControl.isBlocked());

        // when both partitions become available at once
        partitionManager.makePartitionAvailable("foo", 0);
        partitionManager.makePartitionAvailable("foo", 1);
        streamProcessor.runAsync(checkPartitionsCmd);
        waitUntil(() -> partitionEventsInState(PartitionState.CREATE_COMPLETE).findFirst().isPresent());

        // then the topic created response is sent once
        processorControl.unblock();
        waitUntil(() -> topicEventsInState(TopicState.CREATED).findFirst().isPresent());
        verify(output, times(1)).sendResponse(any());
    }

    protected Stream<PartitionEvent> partitionEventsInState(PartitionState state)
    {
        return streams.events(STREAM_NAME)
                .filter(Events::isPartitionEvent)
                .map(Events::asPartitionEvent)
                .filter(e -> e.getState() == state);
    }

    protected Stream<TopicEvent> topicEventsInState(TopicState state)
    {
        return streams.events(STREAM_NAME)
                .filter(Events::isTopicEvent)
                .map(Events::asTopicEvent)
                .filter(e -> e.getState() == state);
    }

    protected TopicEvent createTopic(String name, int partitions)
    {
        final TopicEvent event = new TopicEvent();
        event.setName(BufferUtil.wrapString(name));
        event.setPartitions(partitions);
        event.setState(TopicState.CREATE);

        return event;
    }

    protected class PartitionManagerImpl implements PartitionManager
    {

        protected AtomicInteger partitionRequests = new AtomicInteger(0);
        protected List<Partition> currentPartitions = new CopyOnWriteArrayList<>();

        @Override
        public void createPartitionAsync(DirectBuffer topicName, int partitionId)
        {
            partitionRequests.incrementAndGet();
        }

        public int getPartitionRequests()
        {
            return partitionRequests.get();
        }

        @Override
        public Iterator<Partition> getKnownPartitions()
        {
            return currentPartitions.iterator();
        }

        public void makePartitionAvailable(String topicName, int partitionId)
        {
            this.currentPartitions.add(new Partition()
            {
                @Override
                public DirectBuffer getTopicName()
                {
                    return BufferUtil.wrapString(topicName);
                }

                @Override
                public int getPartitionId()
                {
                    return partitionId;
                }
            });
        }

    }
}
