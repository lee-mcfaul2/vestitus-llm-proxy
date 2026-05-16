package dev.vestitus.wire;

public sealed interface WireMessage
        permits AgentRequest, ToolCall, ToolResult, ResponsePromptEnvelope {}
