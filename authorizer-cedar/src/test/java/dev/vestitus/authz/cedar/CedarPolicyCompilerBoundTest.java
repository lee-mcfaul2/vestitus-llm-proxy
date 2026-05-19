package dev.vestitus.authz.cedar;

import dev.vestitus.authz.PolicyCompileException;
import dev.vestitus.mcpschema.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CedarPolicyCompilerBoundTest {

    private static McpSchema schema(String ruleset) {
        return new McpSchema(McpSchemaVersion.CURRENT, "crm",
            List.of(new ToolDecl("t", "d",
                List.of(new FieldDecl("email", PiiType.NONE, new IamBinding("x"))))),
            new CedarRuleset(ruleset),
            new CedarSchemaText("entity User;"));
    }

    @Test
    void overLongRulesetIsRejectedBeforeNative() {
        // seam ctor: maxLen=10, maxStatements=100
        var c = new CedarPolicyCompiler(10, 100);
        var ex = assertThrows(PolicyCompileException.class,
            () -> c.compile(schema("permit(principal == User::\"a\", action, resource);")));
        assertTrue(ex.getMessage().toLowerCase().contains("length"));
    }

    @Test
    void tooManyStatementsIsRejectedBeforeNative() {
        var c = new CedarPolicyCompiler(100_000, 1);
        var ex = assertThrows(PolicyCompileException.class,
            () -> c.compile(schema(
                "permit(principal == User::\"a\", action, resource);"
              + "permit(principal == User::\"b\", action, resource);")));
        assertTrue(ex.getMessage().toLowerCase().contains("statement")
            || ex.getMessage().toLowerCase().contains("count"));
    }

    @Test
    void defaultBoundsAreLenientForNormalRulesets() {
        // Within both default bounds: must NOT throw the bound exception
        // (it may proceed to native; that path is the Itest). Here we only
        // assert the bound itself does not fire for a small ruleset by using
        // the seam with generous limits and a fake-free check on the guard.
        var c = new CedarPolicyCompiler(20_000, 256);
        assertDoesNotThrow(() -> {
            try { c.compile(schema("permit(principal == User::\"a\", action, resource);")); }
            catch (PolicyCompileException e) {
                if (e.getMessage() != null
                    && (e.getMessage().toLowerCase().contains("length")
                     || e.getMessage().toLowerCase().contains("statement"))) {
                    throw e; // a BOUND failure is a test failure here
                }
                // any native-load issue is out of scope for this unit test
            }
        });
    }
}
