package dev.vestitus.wire;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FailClosedParseTest {
    @Test
    void unknownKindThrowsTypedFailure() {
        assertThrows(WireParseException.class,
            () -> WireJson.read("{\"kind\":\"NotAThing\",\"tool\":\"x\"}"));
    }

    @Test
    void missingKindThrowsTypedFailure() {
        assertThrows(WireParseException.class, () -> WireJson.read("{\"tool\":\"x\"}"));
    }

    @Test
    void garbageThrowsTypedFailure() {
        assertThrows(WireParseException.class, () -> WireJson.read("not json"));
    }

    @Test
    void trailingTokensRejected() {
        String valid = WireJson.write(new ToolCall("t", java.util.Map.of()));
        assertThrows(WireParseException.class, () -> WireJson.read(valid + " {}"));
    }
}
