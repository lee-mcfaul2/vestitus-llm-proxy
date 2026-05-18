package dev.vestitus.gate;

import dev.vestitus.mcpschema.CedarRuleset;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdentityPredicateLintTest {

    private static GateVerdict lint(String policy) {
        return IdentityPredicateLint.check(new CedarRuleset(policy));
    }

    @Test
    void blatantIdentityLessPermitIsRejected() {
        GateVerdict v = lint("permit(principal, action, resource);");
        assertFalse(v.passed());
        assertTrue(((GateVerdict.Reject) v).reasons().get(0)
            .contains("self-permissive / identity-less permit"));
    }

    @Test
    void scopeConstrainedPrincipalPasses() {
        assertTrue(lint("permit(principal == User::\"a\", action, resource);").passed());
    }

    @Test
    void principalInGroupScopePasses() {
        assertTrue(lint("permit(principal in Group::\"g\", action, resource);").passed());
    }

    @Test
    void principalReferencingWhenPasses() {
        assertTrue(lint(
            "permit(principal, action, resource) when { principal.x == 1 };").passed());
    }

    @Test
    void forbidOnlyRulesetPasses() {
        assertTrue(lint("forbid(principal, action, resource);").passed());
    }

    @Test
    void annotationsBeforePermitAreStripped() {
        assertTrue(lint(
            "@id(\"p1\") permit(principal == User::\"a\", action, resource);").passed());
    }

    @Test
    void multiStatementWithOneBadIsRejected() {
        GateVerdict v = lint(
            "permit(principal == User::\"a\", action, resource);"
            + "permit(principal, action, resource);");
        assertFalse(v.passed());
        assertEquals(1, ((GateVerdict.Reject) v).reasons().size());
    }

    @Test
    void commentMentioningPermitDoesNotAffectVerdict() {
        // The // comment names "permit" but is stripped; the real rule is constrained.
        assertTrue(lint(
            "// a permissive permit example below\n"
            + "permit(principal == User::\"a\", action, resource);").passed());
    }

    @Test
    void semicolonInsideStringDoesNotSplitStatement() {
        assertTrue(lint(
            "permit(principal == User::\"a;b\", action, resource);").passed());
    }

    @Test
    void unknownStatementIsRejectedFailClosed() {
        GateVerdict v = lint("permit(principal == User::\"a\", action, resource);"
            + " entity User;");
        assertFalse(v.passed());
        assertTrue(((GateVerdict.Reject) v).reasons().get(0)
            .contains("unrecognized policy statement"));
    }

    @Test
    void malformedPermitScopeIsRejectedFailClosed() {
        GateVerdict v = lint("permit(principal, action, resource");
        assertFalse(v.passed());
        assertTrue(((GateVerdict.Reject) v).reasons().get(0)
            .toLowerCase().contains("fail-closed"));
    }

    @Test
    void wrongArityScopeIsRejectedFailClosed() {
        GateVerdict v = lint("permit(principal == User::\"a\", action);");
        assertFalse(v.passed());
    }

    @Test
    void deferredCvc5TautologyCaseCurrentlyPasses() {
        // ADR-002 §7 DEFERRED cvc5 case: a `when` body textually containing the
        // `principal` token passes the conservative textual lint. This semantic
        // self-permissive tautology is the named, tracked symbolic follow-on and
        // is intentionally NOT caught here (residual, not a gap).
        assertTrue(lint(
            "permit(principal, action, resource) when { principal == principal };")
            .passed());
    }
}
