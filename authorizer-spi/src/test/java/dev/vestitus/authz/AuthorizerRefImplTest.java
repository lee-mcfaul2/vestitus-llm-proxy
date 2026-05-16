package dev.vestitus.authz;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class AuthorizerRefImplTest {
    private static AuthorizationRequest req() {
        return new AuthorizationRequest(
            new Principal("u", Set.of("read"), Map.of()),
            "read",
            new ResourceRef("m", "t", "f", Map.of()),
            Map.of());
    }

    @Test
    void denyAllAlwaysDeniesWithReason() {
        Authorizer a = new DenyAllAuthorizer();
        AuthorizationDecision d = a.authorize(req());
        assertFalse(d.allowed());
        assertInstanceOf(AuthorizationDecision.Deny.class, d);
    }

    @Test
    void denyAllHandlesNullRequestFailClosed() {
        AuthorizationDecision d = new DenyAllAuthorizer().authorize(null);
        assertFalse(d.allowed());
    }

    @Test
    void anonymousImplementationSatisfiesSpi() {
        Authorizer always = r -> AuthorizationDecision.allow();
        assertTrue(always.authorize(req()).allowed());
    }
}
