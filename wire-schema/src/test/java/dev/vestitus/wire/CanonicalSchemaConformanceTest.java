package dev.vestitus.wire;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalSchemaConformanceTest {
    @Test
    void registryEnumeratesEveryPermittedSubtype() {
        var permitted = java.util.Arrays.stream(WireMessage.class.getPermittedSubclasses())
            .map(Class::getSimpleName).sorted().toList();
        var registered = CanonicalSchema.rootTypes().stream()
            .map(Class::getSimpleName).sorted().toList();
        assertEquals(permitted, registered,
            "CanonicalSchema drifted from the sealed WireMessage permits clause");
    }

    @Test
    void everyRegisteredTypeRoundTripsStably() {
        List<WireMessage> samples = List.of(
            new AgentRequest("r1", "hi", Map.of()),
            new ToolCall("t", Map.of()),
            new ToolResult("t", Map.of()),
            new ResponsePromptEnvelope(SchemaVersion.CURRENT, List.of()));
        for (WireMessage m : samples) {
            assertEquals(m, WireJson.read(WireJson.write(m)));
        }
    }

    @Test
    void emitterSeamReceivesEveryRootType() {
        var seen = new java.util.ArrayList<String>();
        SchemaEmitter collector = t -> seen.add(t.getSimpleName());
        CanonicalSchema.emitAll(collector);
        assertEquals(CanonicalSchema.rootTypes().size(), seen.size());
    }
}
