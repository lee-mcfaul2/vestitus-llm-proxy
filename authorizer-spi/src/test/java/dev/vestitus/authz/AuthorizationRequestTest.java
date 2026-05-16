package dev.vestitus.authz;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class AuthorizationRequestTest {
    private static AuthorizationRequest sample() {
        return new AuthorizationRequest(
            new Principal("u1", Set.of("read"), Map.of()),
            "read",
            new ResourceRef("mcp", "tool", "field", Map.of()),
            Map.of("traceId", "abc"));
    }

    @Test
    void buildsAndExposesParts() {
        AuthorizationRequest req = sample();
        assertEquals("u1", req.principal().id());
        assertEquals("read", req.action());
        assertEquals("field", req.resource().field());
        assertEquals("abc", req.context().get("traceId"));
    }

    @Test
    void rejectsNullsAndBlankAction() {
        Principal p = new Principal("u", Set.of(), Map.of());
        ResourceRef r = new ResourceRef("m", "t", "f", Map.of());
        assertThrows(IllegalArgumentException.class, () -> new AuthorizationRequest(null, "read", r, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new AuthorizationRequest(p, " ", r, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new AuthorizationRequest(p, "read", null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new AuthorizationRequest(p, "read", r, null));
    }
}
