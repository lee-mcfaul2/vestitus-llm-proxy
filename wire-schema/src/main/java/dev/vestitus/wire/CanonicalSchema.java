package dev.vestitus.wire;

import java.util.List;

/** The single enumerated source of the wire contract's root types. */
public final class CanonicalSchema {
    private CanonicalSchema() {}

    public static List<Class<? extends WireMessage>> rootTypes() {
        return List.of(AgentRequest.class, ToolCall.class,
                        ToolResult.class, ResponsePromptEnvelope.class);
    }

    public static void emitAll(SchemaEmitter emitter) {
        for (var t : rootTypes()) emitter.emit(t);
    }
}
