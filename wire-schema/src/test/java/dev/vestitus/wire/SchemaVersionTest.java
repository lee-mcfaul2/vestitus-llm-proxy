package dev.vestitus.wire;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SchemaVersionTest {
    @Test
    void currentIsStableAndParseable() {
        assertEquals("1.0.0", SchemaVersion.CURRENT.value());
        assertEquals(SchemaVersion.CURRENT, SchemaVersion.parse("1.0.0"));
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> SchemaVersion.parse(" "));
    }
}
