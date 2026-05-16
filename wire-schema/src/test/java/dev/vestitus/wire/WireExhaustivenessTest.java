package dev.vestitus.wire;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WireExhaustivenessTest {
    private static String tag(WireMessage m) {
        return switch (m) {                       // no default: exhaustiveness enforced at compile time
            case AgentRequest a -> "request:" + a.requestId();
            case ToolCall t -> "call:" + t.tool();
            case ToolResult r -> "result:" + r.tool();
            case ResponsePromptEnvelope e -> "envelope:" + e.schemaVersion().value();
        };
    }

    @Test
    void everyVariantIsTaggable() {
        assertEquals("request:r1", tag(new AgentRequest("r1", "hello", Map.of())));
        assertEquals("call:find", tag(new ToolCall("find", Map.of("email", "a@b.c"))));
        assertEquals("result:find", tag(new ToolResult("find", Map.of("id", "42"))));
        assertEquals("envelope:1.0.0", tag(new ResponsePromptEnvelope(
            SchemaVersion.CURRENT, List.of(new ToolResult("find", Map.of("id", "42"))))));
    }
}
