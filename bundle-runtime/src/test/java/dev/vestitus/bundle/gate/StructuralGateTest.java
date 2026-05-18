package dev.vestitus.bundle.gate;

import dev.vestitus.mcpschema.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StructuralGateTest {

    private static FieldDecl f(String name) {
        return new FieldDecl(name, PiiType.NONE, new IamBinding("x"));
    }

    /** A well-formed identity-constrained-permit schema. */
    private static McpSchema schema(String mcpId, String ruleset, ToolDecl... tools) {
        return new McpSchema(McpSchemaVersion.CURRENT, mcpId, List.of(tools),
            new CedarRuleset(ruleset),
            new CedarSchemaText("entity User;"));
    }

    private static McpSchema goodSchema(String mcpId) {
        return schema(mcpId,
            "permit(principal == User::\"a\", action, resource);",
            new ToolDecl("t", "d", List.of(f("email"))));
    }

    @Test
    void cleanSingleSchemaSetPasses() {
        GateVerdict v = StructuralGate.vet(List.of(goodSchema("crm")));
        assertTrue(v.passed());
        assertInstanceOf(GateVerdict.Pass.class, v);
    }

    @Test
    void selfPermissivePermitSchemaIsRejected() {
        // Identity-less permit -> per-schema IdentityPredicateLint must fire.
        GateVerdict v = StructuralGate.vet(List.of(schema("crm",
            "permit(principal, action, resource);",
            new ToolDecl("t", "d", List.of(f("a"))))));
        assertFalse(v.passed());
        assertTrue(((GateVerdict.Reject) v).reasons().stream()
                .anyMatch(r -> r.contains("self-permissive / identity-less permit")),
            "reason must name the self-permissive permit: "
                + ((GateVerdict.Reject) v).reasons());
    }

    @Test
    void crossMcpInjectivityViolationIsRejected() {
        // Two schemas with a duplicate mcpId -> CrossMcpInjectivityCheck.checkSet fires.
        GateVerdict v = StructuralGate.vet(List.of(
            goodSchema("crm"), goodSchema("crm")));
        assertFalse(v.passed());
        assertTrue(((GateVerdict.Reject) v).reasons().stream()
                .anyMatch(r -> r.toLowerCase().contains("duplicate")),
            "reason must name the cross-MCP duplicate: "
                + ((GateVerdict.Reject) v).reasons());
    }

    @Test
    void nullListIsRejectedFailClosed() {
        GateVerdict v = StructuralGate.vet(null);
        assertFalse(v.passed());
        assertTrue(((GateVerdict.Reject) v).reasons().get(0).contains("fail-closed"));
    }

    @Test
    void emptyListPassesSetPolicyIsCallers() {
        // No MCPs to vet. Set-admissibility (is empty admissible) is the
        // CALLER's per ADR-003 D6, NOT an error here.
        GateVerdict v = StructuralGate.vet(List.of());
        assertTrue(v.passed());
    }

    @Test
    void multiSchemaSetWithOneBadIsRejectedWithItsReason() {
        GateVerdict v = StructuralGate.vet(List.of(
            goodSchema("crm"),
            schema("hr", "permit(principal, action, resource);",
                new ToolDecl("t", "d", List.of(f("a"))))));
        assertFalse(v.passed());
        assertTrue(((GateVerdict.Reject) v).reasons().stream()
                .anyMatch(r -> r.contains("self-permissive / identity-less permit")),
            "the bad schema's reason must be present: "
                + ((GateVerdict.Reject) v).reasons());
    }

    @Test
    void deferredCvc5CaseStillPassesCarriedResidual() {
        // ADR-003 §4 boundary-2 named residual: a `when` body textually
        // containing `principal` passes the conservative textual lint. The
        // semantic self-permissive tautology is the deleted-cvc5 follow-on,
        // intentionally NOT caught here (residual, carried through, not a gap).
        GateVerdict v = StructuralGate.vet(List.of(schema("crm",
            "permit(principal, action, resource) when { principal == principal };",
            new ToolDecl("t", "d", List.of(f("a"))))));
        assertTrue(v.passed());
    }
}
