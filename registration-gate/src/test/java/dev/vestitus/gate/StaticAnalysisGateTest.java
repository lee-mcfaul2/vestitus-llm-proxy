package dev.vestitus.gate;

import dev.vestitus.mcpschema.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StaticAnalysisGateTest {

    // Known-good cedar schema+policy: lifted from authorizer-cedar
    // CedarNativeTest#validateBindingWorksOverRealLib (code 2 / Valid).
    private static final String GOOD_SCHEMA =
        "entity User; entity Resource; "
        + "action \"view\" appliesTo { principal: User, resource: Resource };";
    private static final String GOOD_POLICY =
        "permit(principal == User::\"alice\", action == Action::\"view\", resource == Resource::\"doc1\");";

    private static McpSchema schema(String mcpId, String cedarSchema, String ruleset) {
        return new McpSchema(McpSchemaVersion.CURRENT, mcpId,
            List.of(new ToolDecl("findContact", "Find a contact",
                List.of(new FieldDecl("email", PiiType.DIRECT_IDENTIFIER,
                    new IamBinding("crm:read"))))),
            new CedarRuleset(ruleset), new CedarSchemaText(cedarSchema));
    }

    @Test
    void fullyValidSchemaPasses() {
        assertTrue(StaticAnalysisGate.vet(
            schema("crm-mcp", GOOD_SCHEMA, GOOD_POLICY)).passed());
    }

    @Test
    void cedarInvalidDefectRejected() {
        GateVerdict v = StaticAnalysisGate.vet(schema(
            "crm-mcp", GOOD_SCHEMA, "this is not cedar"));
        assertFalse(v.passed());
    }

    @Test
    void selfPermissiveDefectRejected() {
        // Typechecks against GOOD_SCHEMA but is identity-less.
        GateVerdict v = StaticAnalysisGate.vet(schema("crm-mcp", GOOD_SCHEMA,
            "permit(principal, action == Action::\"view\", resource == Resource::\"doc1\");"));
        assertFalse(v.passed());
        assertTrue(((GateVerdict.Reject) v).reasons().stream()
            .anyMatch(s -> s.contains("self-permissive")));
    }

    @Test
    void injectivityDefectRejected() {
        GateVerdict v = StaticAnalysisGate.vet(schema("crm/mcp", GOOD_SCHEMA, GOOD_POLICY));
        assertFalse(v.passed());
    }

    @Test
    void vetAllWithTwoCleanDistinctMcpsPasses() {
        var a = schema("crm-mcp", GOOD_SCHEMA, GOOD_POLICY);
        var b = schema("hr-mcp", GOOD_SCHEMA, GOOD_POLICY);
        assertTrue(StaticAnalysisGate.vetAll(List.of(a, b)).passed());
    }

    @Test
    void vetAllWithCrossMcpDuplicateRejected() {
        var a = schema("crm-mcp", GOOD_SCHEMA, GOOD_POLICY);
        var b = schema("crm-mcp", GOOD_SCHEMA, GOOD_POLICY);
        GateVerdict v = StaticAnalysisGate.vetAll(List.of(a, b));
        assertFalse(v.passed());
        assertTrue(((GateVerdict.Reject) v).reasons().stream()
            .anyMatch(s -> s.toLowerCase().contains("duplicate mcpid")));
    }

    @Test
    void noDefectPathReturnsPass() {
        // Fail-closed guard: every seeded defect rejects.
        assertFalse(StaticAnalysisGate.vet(
            schema("crm-mcp", GOOD_SCHEMA, "not cedar")).passed());
        assertFalse(StaticAnalysisGate.vet(
            schema("crm/mcp", GOOD_SCHEMA, GOOD_POLICY)).passed());
        assertFalse(StaticAnalysisGate.vet(schema("crm-mcp", GOOD_SCHEMA,
            "permit(principal, action == Action::\"view\", resource == Resource::\"doc1\");"))
            .passed());
    }
}
