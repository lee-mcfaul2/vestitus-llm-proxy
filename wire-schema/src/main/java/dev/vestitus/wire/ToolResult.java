package dev.vestitus.wire;

import java.util.Map;

public record ToolResult(String tool, Map<String, String> fields) implements WireMessage {
    public ToolResult {
        if (tool == null || tool.isBlank())
            throw new IllegalArgumentException("tool required");
        if (fields == null)
            throw new IllegalArgumentException("fields required");
        fields = Map.copyOf(fields);
    }
}
