package dev.vestitus.authz;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegistryEntryTest {

    @Test
    void validEntryHoldsItsValues() {
        Authorizer a = new DenyAllAuthorizer();
        RegistryEntry e = new RegistryEntry("mcp-a", a);
        assertEquals("mcp-a", e.mcpId());
        assertSame(a, e.authorizer());
    }

    @Test
    void blankMcpIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new RegistryEntry("   ", new DenyAllAuthorizer()));
        assertThrows(IllegalArgumentException.class,
            () -> new RegistryEntry("", new DenyAllAuthorizer()));
    }

    @Test
    void nullMcpIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new RegistryEntry(null, new DenyAllAuthorizer()));
    }

    @Test
    void nullAuthorizerIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new RegistryEntry("mcp-a", null));
    }
}
