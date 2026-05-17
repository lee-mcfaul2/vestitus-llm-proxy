package dev.vestitus.authz.cedar;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CedarNativeTest {

    private static final String ALLOW_POLICY =
        "permit(principal == User::\"alice\", action == Action::\"view\", resource == Resource::\"doc1\");";
    private static final String DENY_POLICY =
        "permit(principal == User::\"bob\", action == Action::\"view\", resource == Resource::\"doc1\");";

    @Test
    void cleanAllowReturnsCode1NoDiag() {
        CedarNative.Result r = CedarNative.instance().isAuthorized(
            ALLOW_POLICY, "User::\"alice\"", "Action::\"view\"",
            "Resource::\"doc1\"", "{}", "[]");
        assertEquals(1, r.code());            // CedarResult::Allow
        assertNull(r.diag());                 // shim leaves *out_diag NULL on clean Allow
    }

    @Test
    void cleanDenyReturnsCode0WithDiag() {
        CedarNative.Result r = CedarNative.instance().isAuthorized(
            DENY_POLICY, "User::\"alice\"", "Action::\"view\"",
            "Resource::\"doc1\"", "{}", "[]");
        assertEquals(0, r.code());            // CedarResult::Deny
        assertNotNull(r.diag());
        assertTrue(r.diag().toLowerCase().contains("deny"));
    }

    @Test
    void malformedPolicyReturnsErrorCodeMinus1WithDiag() {
        CedarNative.Result r = CedarNative.instance().isAuthorized(
            "this is not cedar", "User::\"alice\"", "Action::\"view\"",
            "Resource::\"doc1\"", "{}", "[]");
        assertEquals(-1, r.code());           // CedarResult::Error
        assertNotNull(r.diag());
        assertTrue(r.diag().toLowerCase().contains("polic"));
    }

    @Test
    void repeatedCallsDoNotLeakOrCrash() {
        for (int i = 0; i < 200; i++) {
            CedarNative.Result r = CedarNative.instance().isAuthorized(
                DENY_POLICY, "User::\"x" + i + "\"", "Action::\"view\"",
                "Resource::\"doc1\"", "{}", "[]");
            assertEquals(0, r.code());        // each Deny allocates+frees a diag
        }
    }

    @Test
    void validateBindingWorksOverRealLib() {
        String schema =
            "entity User; entity Resource; "
            + "action \"view\" appliesTo { principal: User, resource: Resource };";
        String pol =
            "permit(principal == User::\"alice\", action == Action::\"view\", resource == Resource::\"doc1\");";
        CedarNative.Result r = CedarNative.instance().validate(schema, pol);
        assertEquals(2, r.code());            // CedarResult::Valid
    }

    @Test
    void singletonIsStable() {
        assertSame(CedarNative.instance(), CedarNative.instance());
    }
}
