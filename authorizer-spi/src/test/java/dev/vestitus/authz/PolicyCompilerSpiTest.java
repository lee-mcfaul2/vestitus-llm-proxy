package dev.vestitus.authz;

import dev.vestitus.mcpschema.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PolicyCompilerSpiTest {

    private static McpSchema schema() {
        return new McpSchema(McpSchemaVersion.CURRENT, "crm",
            List.of(new ToolDecl("t", "d",
                List.of(new FieldDecl("email", PiiType.NONE, new IamBinding("x"))))),
            new CedarRuleset("permit(principal == User::\"a\", action, resource);"),
            new CedarSchemaText("entity User;"));
    }

    @Test
    void compilerIsAFunctionalSeamReturningAnAuthorizer() {
        PolicyCompiler c = s -> req -> AuthorizationDecision.deny("stub");
        Authorizer a = c.compile(schema());
        assertNotNull(a);
    }

    @Test
    void policyCompileExceptionCarriesMessageAndCause() {
        var cause = new IllegalStateException("native bound");
        var ex = new PolicyCompileException("ruleset too large", cause);
        assertEquals("ruleset too large", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertInstanceOf(RuntimeException.class, ex);
    }
}
