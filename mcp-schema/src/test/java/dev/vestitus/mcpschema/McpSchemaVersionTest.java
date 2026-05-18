package dev.vestitus.mcpschema;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class McpSchemaVersionTest {
    @Test
    void currentIsStableAndParseable() {
        assertEquals("1.0.0", McpSchemaVersion.CURRENT.value());
        assertEquals(McpSchemaVersion.CURRENT, McpSchemaVersion.parse("1.0.0"));
    }

    @Test
    void parseTrims() {
        assertEquals(McpSchemaVersion.CURRENT, McpSchemaVersion.parse("  1.0.0  "));
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> McpSchemaVersion.parse(" "));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> McpSchemaVersion.parse(null));
    }
}
