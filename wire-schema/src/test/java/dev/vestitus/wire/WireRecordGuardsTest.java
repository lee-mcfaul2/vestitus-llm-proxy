package dev.vestitus.wire;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WireRecordGuardsTest {
    @Test
    void agentRequestRejectsNullInputAndAttributes() {
        assertThrows(IllegalArgumentException.class, () -> new AgentRequest("r", null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new AgentRequest("r", "i", null));
    }
    @Test
    void toolCallRejectsNullArgs() {
        assertThrows(IllegalArgumentException.class, () -> new ToolCall("t", null));
    }
    @Test
    void toolResultRejectsNullFields() {
        assertThrows(IllegalArgumentException.class, () -> new ToolResult("t", null));
    }
    @Test
    void responsePromptEnvelopeRejectsNullContents() {
        assertThrows(IllegalArgumentException.class,
            () -> new ResponsePromptEnvelope(SchemaVersion.CURRENT, null));
    }
}
