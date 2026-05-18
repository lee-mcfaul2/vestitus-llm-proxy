package dev.vestitus.mcpschema;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IamBindingTest {
    @Test
    void validEntitlementIsHeld() {
        assertEquals("crm:read:contacts", new IamBinding("crm:read:contacts").entitlement());
    }

    @Test
    void rejectsNullEntitlement() {
        assertThrows(IllegalArgumentException.class, () -> new IamBinding(null));
    }

    @Test
    void rejectsBlankEntitlement() {
        assertThrows(IllegalArgumentException.class, () -> new IamBinding("  "));
    }
}
