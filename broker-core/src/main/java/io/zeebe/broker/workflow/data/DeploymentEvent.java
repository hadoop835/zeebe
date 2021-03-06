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
package io.zeebe.broker.workflow.data;

import static io.zeebe.broker.workflow.data.WorkflowInstanceEvent.PROP_STATE;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.*;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.msgpack.value.ArrayValue;
import io.zeebe.msgpack.value.ArrayValueIterator;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class DeploymentEvent extends UnpackedObject
{
    protected static final DirectBuffer EMPTY_ARRAY = new UnsafeBuffer(MsgPackHelper.EMPTY_ARRAY);

    private final EnumProperty<DeploymentState> stateProp = new EnumProperty<>(PROP_STATE, DeploymentState.class);

    private final BinaryProperty resourceProp = new BinaryProperty("resource");
    private final EnumProperty<ResourceType> resourceTypeProp = new EnumProperty<ResourceType>("resourceType", ResourceType.class, ResourceType.BPMN_XML);

    private final ArrayProperty<DeployedWorkflow> deployedWorkflowsProp = new ArrayProperty<>(
            "deployedWorkflows",
            new ArrayValue<>(),
            new ArrayValue<>(EMPTY_ARRAY, 0, EMPTY_ARRAY.capacity()),
            new DeployedWorkflow());

    private final StringProperty errorMessageProp = new StringProperty("errorMessage", "");

    public DeploymentEvent()
    {
        this.declareProperty(stateProp)
            .declareProperty(resourceProp)
            .declareProperty(resourceTypeProp)
            .declareProperty(deployedWorkflowsProp)
            .declareProperty(errorMessageProp);
    }

    public DeploymentState getState()
    {
        return stateProp.getValue();
    }

    public DeploymentEvent setState(DeploymentState event)
    {
        this.stateProp.setValue(event);
        return this;
    }

    public DirectBuffer getResource()
    {
        return resourceProp.getValue();
    }

    public DeploymentEvent setResource(DirectBuffer resource)
    {
        return setResource(resource, 0, resource.capacity());
    }

    public DeploymentEvent setResource(DirectBuffer resource, int offset, int length)
    {
        this.resourceProp.setValue(resource, offset, length);
        return this;
    }

    public ResourceType getResourceType()
    {
        return resourceTypeProp.getValue();
    }

    public DeploymentEvent setResourceName(ResourceType resourceType)
    {
        this.resourceTypeProp.setValue(resourceType);
        return this;
    }

    public ArrayValueIterator<DeployedWorkflow> deployedWorkflows()
    {
        return deployedWorkflowsProp;
    }

    public DirectBuffer getErrorMessage()
    {
        return errorMessageProp.getValue();
    }

    public DeploymentEvent setErrorMessage(String errorMessage)
    {
        this.errorMessageProp.setValue(errorMessage);
        return this;
    }

    public DeploymentEvent setErrorMessage(DirectBuffer errorMessage, int offset, int length)
    {
        this.errorMessageProp.setValue(errorMessage, offset, length);
        return this;
    }
}
