package dev.vestitus.inspect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdentifierTest {

    @Test
    void stageIdRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new StageId(null));
        assertThrows(IllegalArgumentException.class, () -> new StageId("  "));
        assertEquals("cred-floor", new StageId("cred-floor").name());
    }

    @Test
    void reasonCodeRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new ReasonCode(null));
        assertThrows(IllegalArgumentException.class, () -> new ReasonCode(""));
        assertEquals("pii.us_ssn", new ReasonCode("pii.us_ssn").code());
    }

    @Test
    void enumsExposeTheSpecifiedConstants() {
        assertEquals(2, SpanFidelity.values().length);
        assertEquals(2, SemanticAction.values().length);
        assertEquals(3, FindingKind.values().length);
        assertEquals(2, IncidentKind.values().length);
        assertNotNull(SpanFidelity.valueOf("SPAN_PRESERVING"));
        assertNotNull(SemanticAction.valueOf("INCIDENT"));
        assertNotNull(FindingKind.valueOf("CREDENTIAL"));
        assertNotNull(IncidentKind.valueOf("CREDENTIAL_DETECTED"));
    }
}
