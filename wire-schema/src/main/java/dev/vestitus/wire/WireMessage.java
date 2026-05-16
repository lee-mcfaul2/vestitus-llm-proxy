package dev.vestitus.wire;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = AgentRequest.class, name = "AgentRequest"),
    @JsonSubTypes.Type(value = ToolCall.class, name = "ToolCall"),
    @JsonSubTypes.Type(value = ToolResult.class, name = "ToolResult"),
    @JsonSubTypes.Type(value = ResponsePromptEnvelope.class, name = "ResponsePromptEnvelope")
})
public sealed interface WireMessage
        permits AgentRequest, ToolCall, ToolResult, ResponsePromptEnvelope {}
