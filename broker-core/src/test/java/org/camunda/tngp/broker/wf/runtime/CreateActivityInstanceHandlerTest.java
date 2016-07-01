package org.camunda.tngp.broker.wf.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.bpmn.graph.BpmnEdgeTypes;
import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.bpmn.graph.transformer.BpmnModelInstanceTransformer;
import org.camunda.tngp.broker.test.util.FluentMock;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.log.idgenerator.impl.PrivateIdGenerator;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CreateActivityInstanceHandlerTest
{
    @FluentMock
    protected BpmnFlowElementEventWriter eventWriter;

    @Mock
    protected BpmnFlowElementEventReader flowEventReader;

    @Mock
    protected LogWriter logWriter;

    protected ProcessGraph process;
    protected FlowElementVisitor elementVisitor;

    protected IdGenerator idGenerator;

    @Before
    public void initMocks()
    {
        MockitoAnnotations.initMocks(this);
        idGenerator = new PrivateIdGenerator(0);
    }

    @Before
    public void createProcess()
    {
        final BpmnModelInstance model = Bpmn.createExecutableProcess("process")
                .startEvent("startEvent")
                .serviceTask("serviceTask")
                .endEvent("endEvent")
                .done();

        process = new BpmnModelInstanceTransformer().transformSingleProcess(model, 0L);
        elementVisitor = new FlowElementVisitor();
    }

    @Test
    public void testWriteCreateActivityInstanceEvent()
    {
        // given
        elementVisitor.init(process).moveToNode(process.intialFlowNodeId());
        final int sequenceFlowId = elementVisitor.traverseEdge(BpmnEdgeTypes.NODE_OUTGOING_SEQUENCE_FLOWS).nodeId();
        final int activityId = elementVisitor.traverseEdge(BpmnEdgeTypes.SEQUENCE_FLOW_TARGET_NODE).nodeId();

        when(flowEventReader.event()).thenReturn(ExecutionEventType.PROC_INST_CREATED);
        when(flowEventReader.flowElementId()).thenReturn(sequenceFlowId);
        when(flowEventReader.key()).thenReturn(1234L);
        when(flowEventReader.processId()).thenReturn(467L);
        when(flowEventReader.processInstanceId()).thenReturn(9876L);

        final CreateActivityInstanceHandler handler = new CreateActivityInstanceHandler();
        handler.setEventWriter(eventWriter);

        // when
        handler.handle(flowEventReader, process, logWriter, idGenerator);

        // then
        verify(eventWriter).eventType(ExecutionEventType.ACT_INST_CREATED);
        verify(eventWriter).flowElementId(activityId);
        verify(eventWriter).key(anyLong());
        verify(eventWriter).processId(467L);
        verify(eventWriter).processInstanceId(9876L);

        verify(logWriter).write(eventWriter);
    }

    @Test
    public void testHandledAspect()
    {
        // given
        final CreateActivityInstanceHandler handler = new CreateActivityInstanceHandler();

        // when
        final BpmnAspect bpmnAspect = handler.getHandledBpmnAspect();

        // then
        assertThat(bpmnAspect).isEqualTo(BpmnAspect.CREATE_ACTIVITY_INSTANCE);
    }

}