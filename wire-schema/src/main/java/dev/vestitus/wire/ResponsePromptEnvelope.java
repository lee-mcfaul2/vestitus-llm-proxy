package dev.vestitus.wire;

import java.util.List;

public record ResponsePromptEnvelope(SchemaVersion schemaVersion, List<ToolResult> contents)
        implements WireMessage {
    public ResponsePromptEnvelope {
        if (schemaVersion == null) throw new IllegalArgumentException("schemaVersion required");
        contents = List.copyOf(contents);
    }
}
