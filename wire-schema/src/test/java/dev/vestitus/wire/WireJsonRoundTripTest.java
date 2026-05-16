package dev.vestitus.wire;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class WireJsonRoundTripTest {
    @Test
    void roundTripsEveryVariant() {
        List<WireMessage> samples = List.of(
            new AgentRequest("r1", "hi", Map.of("role", "analyst")),
            new ToolCall("find", Map.of("email", "a@b.c")),
            new ToolResult("find", Map.of("id", "42")),
            new ResponsePromptEnvelope(SchemaVersion.CURRENT,
                List.of(new ToolResult("find", Map.of("id", "42")))));
        for (WireMessage m : samples) {
            String json = WireJson.write(m);
            WireMessage back = WireJson.read(json);
            assertEquals(m, back, "round-trip mismatch for " + m.getClass().getSimpleName());
        }
    }

    @Test
    void discriminatorIsPresent() {
        assertTrue(WireJson.write(new ToolCall("t", Map.of())).contains("\"kind\":\"ToolCall\""));
    }
}
