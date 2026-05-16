package dev.vestitus.authz;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ResourceRefTest {
    @Test
    void buildsAndCopiesTags() {
        Map<String, String> tags = new HashMap<>(Map.of("piiType", "SSN"));
        ResourceRef r = new ResourceRef("mcp-fin", "find_customer", "ssn", tags);
        tags.put("x", "y");
        assertEquals("mcp-fin", r.mcpId());
        assertEquals("find_customer", r.tool());
        assertEquals("ssn", r.field());
        assertEquals(Map.of("piiType", "SSN"), r.tags());
    }

    @Test
    void rejectsBlankAndNull() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceRef(" ", "t", "f", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ResourceRef("m", " ", "f", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ResourceRef("m", "t", " ", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ResourceRef("m", "t", "f", null));
    }
}
