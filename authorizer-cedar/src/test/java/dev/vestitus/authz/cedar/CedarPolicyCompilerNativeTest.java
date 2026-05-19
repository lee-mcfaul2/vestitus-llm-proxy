package dev.vestitus.authz.cedar;

import dev.vestitus.authz.*;
import dev.vestitus.mcpschema.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CedarPolicyCompilerNativeTest {

    private static McpSchema schema(String ruleset) {
        return new McpSchema(McpSchemaVersion.CURRENT, "crm",
            List.of(new ToolDecl("files", "d",
                List.of(new FieldDecl("contents", PiiType.NONE, new IamBinding("x"))))),
            new CedarRuleset(ruleset),
            new CedarSchemaText("entity User;"));
    }

    private static AuthorizationRequest req(String principalId) {
        return new AuthorizationRequest(
            new Principal(principalId, Set.of("read"), Map.of()),
            "read",
            new ResourceRef("crm", "files", "contents", Map.of()),
            Map.of());
    }

    @Test
    void compilesAMinimalValidRulesetToAWorkingAuthorizerViaRealNative() {
        var compiler = new CedarPolicyCompiler();
        Authorizer a = compiler.compile(schema(
            "permit(principal == User::\"alice\", action, resource);"));
        assertTrue(a.authorize(req("alice")).allowed(),
            "real native engine should allow the pinned principal");
        assertFalse(a.authorize(req("bob")).allowed(),
            "real native engine should deny a non-matching principal fail-closed");
    }

    @Test
    void preNativeBoundRejectsAnOverLargeRulesetWithoutInvokingNative() {
        var compiler = new CedarPolicyCompiler(8, 256);
        var ex = assertThrows(PolicyCompileException.class,
            () -> compiler.compile(schema(
                "permit(principal == User::\"alice\", action, resource);")));
        assertTrue(ex.getMessage().toLowerCase().contains("length"));
    }
}
