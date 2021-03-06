/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
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
package io.zeebe.perftest;

import static io.zeebe.perftest.CommonProperties.DEFAULT_TOPIC_NAME;

import java.util.Properties;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import io.zeebe.client.WorkflowsClient;
import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.perftest.helper.MaxRateThroughputTest;


public class StartWorkflowInstanceThroughputTest extends MaxRateThroughputTest
{


    @Override
    protected void executeSetup(Properties properties, ZeebeClient client)
    {
        final WorkflowsClient workflowsClient = client.workflows();

        final WorkflowDefinition workflow = Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .serviceTask("serviceTask", t -> t.taskType("foo").taskRetries(3))
                .endEvent()
                .done();

        // create deployment
        workflowsClient
            .deploy(DEFAULT_TOPIC_NAME)
            .workflowModel(workflow)
            .execute();

        try
        {
            Thread.sleep(1000);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected Supplier<Future> requestFn(ZeebeClient client)
    {
        final WorkflowsClient workflows = client.workflows();

        return () ->
        {
            return workflows.create(DEFAULT_TOPIC_NAME)
                .bpmnProcessId("process")
                .executeAsync();
        };
    }

    public static void main(String[] args)
    {
        new StartWorkflowInstanceThroughputTest().run();
    }
}
