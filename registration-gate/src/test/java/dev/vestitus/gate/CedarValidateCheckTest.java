package dev.vestitus.gate;

import dev.vestitus.mcpschema.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CedarValidateCheckTest {

    // Lifted verbatim from authorizer-cedar CedarNativeTest#validateBindingWorksOverRealLib
    // (asserted code()==2 / Valid against the real vendored .so).
    private static final String GOOD_SCHEMA =
        "entity User; entity Resource; "
        + "action \"view\" appliesTo { principal: User, resource: Resource };";
    private static final String GOOD_POLICY =
        "permit(principal == User::\"alice\", action == Action::\"view\", resource == Resource::\"doc1\");";

    private static McpSchema schema(String cedarSchema, String ruleset) {
        return new McpSchema(
            McpSchemaVersion.CURRENT, "crm-mcp",
            List.of(new ToolDecl("findContact", "Find a contact",
                List.of(new FieldDecl("email", PiiType.DIRECT_IDENTIFIER,
                    new IamBinding("crm:read"))))),
            new CedarRuleset(ruleset),
            new CedarSchemaText(cedarSchema));
    }

    @Test
    void knownGoodSchemaAndPolicyPasses() {
        GateVerdict v = CedarValidateCheck.check(schema(GOOD_SCHEMA, GOOD_POLICY));
        assertTrue(v.passed(), "known-good (code 2 / Valid) must pass");
    }

    @Test
    void typeBrokenPolicyIsRejectedWithCodeInReason() {
        // References an undeclared action -> cedar validate returns Invalid (code 3).
        String broken =
            "permit(principal == User::\"alice\", action == Action::\"nope\", resource == Resource::\"doc1\");";
        GateVerdict v = CedarValidateCheck.check(schema(GOOD_SCHEMA, broken));
        assertFalse(v.passed());
        GateVerdict.Reject r = (GateVerdict.Reject) v;
        assertEquals(1, r.reasons().size());
        assertTrue(r.reasons().get(0).contains("cedar validate failed"),
            "reason should name the failing check");
        assertTrue(r.reasons().get(0).contains("code="),
            "reason should carry the raw CedarResult code");
    }

    @Test
    void garbagePolicyIsRejected() {
        GateVerdict v = CedarValidateCheck.check(schema(GOOD_SCHEMA, "this is not cedar"));
        assertFalse(v.passed());
    }
}
