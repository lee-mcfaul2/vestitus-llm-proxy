package dev.vestitus.authz.cedar;

import dev.vestitus.authz.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CedarAuthorizerTest {

    private static String resource(String name) {
        try (var in = CedarAuthorizerTest.class.getResourceAsStream("/cedar/" + name)) {
            if (in == null) throw new IllegalStateException("missing test resource " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static AuthorizationRequest req(String principalId, String action,
            Set<String> scopes, Map<String,String> tags) {
        return new AuthorizationRequest(
            new Principal(principalId, scopes, Map.of()),
            action,
            new ResourceRef("mcp-a", "files", "contents", tags),
            Map.of());
    }

    @Test
    void cleanMatchAllows() {
        var authz = new CedarAuthorizer(resource("test-policy.cedar"));
        AuthorizationDecision d = authz.authorize(
            req("alice", "read", Set.of("read"), Map.of()));
        assertTrue(d.allowed(), () -> ((AuthorizationDecision.Deny) d).reason());
        assertInstanceOf(AuthorizationDecision.Allow.class, d);
    }

    @Test
    void wrongPrincipalDeniesFailClosed() {
        var authz = new CedarAuthorizer(resource("test-policy.cedar"));
        AuthorizationDecision d = authz.authorize(
            req("bob", "read", Set.of("read"), Map.of()));
        assertFalse(d.allowed());
        assertInstanceOf(AuthorizationDecision.Deny.class, d);
        assertFalse(((AuthorizationDecision.Deny) d).reason().isBlank());
    }

    @Test
    void missingScopeDenies() {
        var authz = new CedarAuthorizer(resource("test-policy.cedar"));
        AuthorizationDecision d = authz.authorize(
            req("alice", "read", Set.of("other"), Map.of()));
        assertFalse(d.allowed());
    }

    @Test
    void forbidOnPiiTagOverridesPermit() {
        var authz = new CedarAuthorizer(resource("test-policy.cedar"));
        AuthorizationDecision d = authz.authorize(
            req("alice", "read", Set.of("read"), Map.of("pii", "true")));
        assertFalse(d.allowed());
        assertTrue(((AuthorizationDecision.Deny) d).reason().toLowerCase().contains("deny"));
    }

    @Test
    void malformedPolicyEvaluatesToDenyNotThrow() {
        var authz = new CedarAuthorizer(resource("malformed.cedar"));
        AuthorizationDecision d = authz.authorize(
            req("alice", "read", Set.of("read"), Map.of()));
        assertFalse(d.allowed());                       // native Error(-1) -> Deny
        assertInstanceOf(AuthorizationDecision.Deny.class, d);
    }

    @Test
    void blankOrNullPolicyRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new CedarAuthorizer(null));
        assertThrows(IllegalArgumentException.class, () -> new CedarAuthorizer("   "));
    }

    @Test
    void nullRequestIsDeniedFailClosedNeverThrows() {
        var authz = new CedarAuthorizer(resource("test-policy.cedar"));
        AuthorizationDecision d = authz.authorize(null);
        assertFalse(d.allowed());
        assertInstanceOf(AuthorizationDecision.Deny.class, d);
    }

    @Test
    void idWithQuoteIsEscapedAndDeniedNotInjected() {
        // An id containing a double quote must not break out of the Cedar
        // UID literal (no injection); worst case it simply doesn't match -> Deny.
        var authz = new CedarAuthorizer(resource("test-policy.cedar"));
        AuthorizationDecision d = authz.authorize(
            req("alice\" || true || \"", "read", Set.of("read"), Map.of()));
        assertFalse(d.allowed());
    }
}
