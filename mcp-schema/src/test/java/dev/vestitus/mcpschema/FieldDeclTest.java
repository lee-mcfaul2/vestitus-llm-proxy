package dev.vestitus.mcpschema;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FieldDeclTest {
    @Test
    void validFieldConstructs() {
        var f = new FieldDecl("email", PiiType.DIRECT_IDENTIFIER,
            new IamBinding("crm:read:contacts"));
        assertEquals("email", f.name());
        assertEquals(PiiType.DIRECT_IDENTIFIER, f.pii());
        assertEquals("crm:read:contacts", f.iam().entitlement());
    }

    @Test
    void missingPiiIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new FieldDecl("email", null, new IamBinding("crm:read:contacts")));
    }

    @Test
    void missingIamIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new FieldDecl("email", PiiType.NONE, null));
    }

    @Test
    void blankNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new FieldDecl("  ", PiiType.NONE, new IamBinding("x")));
    }

    @Test
    void nullNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new FieldDecl(null, PiiType.NONE, new IamBinding("x")));
    }
}
