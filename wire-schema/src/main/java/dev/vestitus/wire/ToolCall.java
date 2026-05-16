package dev.vestitus.wire;

import java.util.Map;

public record ToolCall(String tool, Map<String, String> args) implements WireMessage {
    public ToolCall {
        if (tool == null || tool.isBlank())
            throw new IllegalArgumentException("tool required");
        args = Map.copyOf(args);
    }
}
