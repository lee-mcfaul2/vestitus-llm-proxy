package dev.vestitus.authz;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class GenerationalRegistryTest {

    /** In-test allow-everything authorizer (proves delegation/snapshot Allow). */
    static final class AllowAuthorizer implements Authorizer {
        @Override
        public AuthorizationDecision authorize(AuthorizationRequest request) {
            return AuthorizationDecision.allow();
        }
    }

    private static AuthorizationRequest req() {
        return new AuthorizationRequest(
            new Principal("u", Set.of(), Map.of()),
            "read",
            new ResourceRef("mcp-a", "t", "f", Map.of()),
            Map.of());
    }

    @Test
    void coldStartDeniesAllFailClosed() {
        var reg = new GenerationalRegistry();
        assertFalse(reg.authorize("m", req()).allowed());
        assertFalse(reg.currentGeneration().authorize("m", req()).allowed());
        AuthorizationDecision d = reg.authorize("m", req());
        assertTrue(((AuthorizationDecision.Deny) d).reason().contains("fail-closed"));
    }

    @Test
    void installThenAuthorizeReflectsTheNewGeneration() {
        var reg = new GenerationalRegistry();
        var gen1 = McpAuthorizerRegistry.ofEntries(
            List.of(new RegistryEntry("m", new AllowAuthorizer())));
        reg.install(gen1);
        assertTrue(reg.authorize("m", req()).allowed());
        assertTrue(reg.currentGeneration().authorize("m", req()).allowed());
    }

    @Test
    void ingressSnapshotIsUnperturbedByAConcurrentInstall() {
        var reg = new GenerationalRegistry();
        var gen1 = McpAuthorizerRegistry.ofEntries(
            List.of(new RegistryEntry("m", new AllowAuthorizer())));
        reg.install(gen1);

        // A request pins ONE generation at ingress.
        var snap = reg.currentGeneration();

        // A concurrent reload swaps in gen2 where "m" now denies all.
        var gen2 = McpAuthorizerRegistry.ofEntries(
            List.of(new RegistryEntry("m", new DenyAllAuthorizer())));
        reg.install(gen2);

        // The pinned snapshot is UNPERTURBED by the concurrent swap (D8 no-straddle).
        assertTrue(snap.authorize("m", req()).allowed());
        // A fresh snapshot taken after the swap sees gen2.
        assertFalse(reg.currentGeneration().authorize("m", req()).allowed());
    }

    @Test
    void installNullIsRejected() {
        var reg = new GenerationalRegistry();
        assertThrows(IllegalArgumentException.class, () -> reg.install(null));
    }

    @Test
    void concurrentReadersNeverSeeATornDecisionWhileInstallsRace()
            throws InterruptedException {
        var reg = new GenerationalRegistry();
        reg.install(McpAuthorizerRegistry.ofEntries(
            List.of(new RegistryEntry("m", new AllowAuthorizer()))));
        var failure = new AtomicReference<Throwable>();
        var stop = new AtomicBoolean(false);
        int readers = 8;
        Thread[] ts = new Thread[readers];
        for (int i = 0; i < readers; i++) {
            ts[i] = new Thread(() -> {
                try {
                    while (!stop.get()) {
                        var snap = reg.currentGeneration();
                        AuthorizationDecision d = snap.authorize("m", req());
                        assertNotNull(d);
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            });
            ts[i].start();
        }
        for (int g = 0; g < 500; g++) {
            reg.install(McpAuthorizerRegistry.ofEntries(List.of(
                new RegistryEntry("m",
                    (g % 2 == 0) ? new AllowAuthorizer() : new DenyAllAuthorizer()))));
        }
        stop.set(true);
        for (Thread t : ts) t.join();
        assertNull(failure.get(),
            "no reader saw an exception or a torn/null decision: " + failure.get());
    }
}
