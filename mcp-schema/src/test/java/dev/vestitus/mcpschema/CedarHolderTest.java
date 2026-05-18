package dev.vestitus.mcpschema;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CedarHolderTest {
    @Test
    void rulesetHoldsText() {
        var r = new CedarRuleset("permit(principal, action, resource);");
        assertEquals("permit(principal, action, resource);", r.text());
    }

    @Test
    void rulesetRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new CedarRuleset(null));
    }

    @Test
    void rulesetRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new CedarRuleset("  "));
    }

    @Test
    void cedarSchemaTextHoldsText() {
        var s = new CedarSchemaText("entity User;");
        assertEquals("entity User;", s.text());
    }

    @Test
    void cedarSchemaTextRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new CedarSchemaText(null));
    }

    @Test
    void cedarSchemaTextRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new CedarSchemaText(" "));
    }
}
