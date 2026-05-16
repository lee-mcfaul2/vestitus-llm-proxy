package dev.vestitus.authz;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthorizationDecisionTest {
    @Test
    void allowIsAllowed() {
        AuthorizationDecision d = AuthorizationDecision.allow();
        assertTrue(d.allowed());
        assertInstanceOf(AuthorizationDecision.Allow.class, d);
    }

    @Test
    void denyCarriesReasonAndIsNotAllowed() {
        AuthorizationDecision d = AuthorizationDecision.deny("nope");
        assertFalse(d.allowed());
        assertEquals("nope", ((AuthorizationDecision.Deny) d).reason());
    }

    @Test
    void denyRequiresNonBlankReason() {
        assertThrows(IllegalArgumentException.class, () -> AuthorizationDecision.deny(" "));
        assertThrows(IllegalArgumentException.class, () -> new AuthorizationDecision.Deny(null));
    }

    @Test
    void exhaustiveSwitchHasNoDefault() {
        AuthorizationDecision d = AuthorizationDecision.deny("x");
        String tag = switch (d) {                      // no default: sealed exhaustiveness
            case AuthorizationDecision.Allow a -> "allow";
            case AuthorizationDecision.Deny dn -> "deny:" + dn.reason();
        };
        assertEquals("deny:x", tag);
    }
}
