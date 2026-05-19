package dev.vestitus.bundle.reload;

import dev.vestitus.authz.*;
import dev.vestitus.trust.VerificationConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BundleReloadOrchestratorHappyPathTest {

    private static final String RX = "^pub/.*$";
    private static final String ISS = "https://issuer.example";

    private static ReloadConfig cfg() {
        return new ReloadConfig(URI.create("http://h/b"), Duration.ofHours(1),
            2, Duration.ZERO, RX, ISS, "acme");
    }

    private static AuthorizationRequest read(String mcpId) {
        return new AuthorizationRequest(
            new Principal("alice", Set.of(), Map.of()),
            "read",
            new ResourceRef(mcpId, "t", "f", Map.of()),
            Map.of());
    }

    @Test
    void successfulReloadInstallsAllCellsAtomically() {
        var registry = new GenerationalRegistry();
        // Cold start: deny-all.
        assertFalse(registry.authorize("crm", read("crm")).allowed());

        var schemas = List.of(ReloadFakes.schema("crm"), ReloadFakes.schema("hr"));
        BundleSource src = () -> new FetchResult.Fetched(
            List.of(ReloadFakes.bundle("r1")));
        var observer = new ReloadFakes.RecordingObserver();
        var clock = new ReloadFakes.FakeClock();

        var orch = new BundleReloadOrchestrator(
            src,
            ReloadFakes.verifierVerifying("pub/acme", 5),
            ReloadFakes.digesterReturning(schemas),
            ReloadFakes.freshGate(),
            ReloadFakes.stubCompiler(),
            registry,
            cfg(),
            clock,
            observer);

        orch.attemptReload();

        assertTrue(registry.authorize("crm", read("crm")).allowed());
        assertTrue(registry.authorize("hr", read("hr")).allowed());
        assertTrue(observer.events.stream().anyMatch(e -> e.startsWith("applied:5:2")),
            observer.events::toString);
    }

    @Test
    void constructorRejectsAnyNullCollaborator() {
        assertThrows(NullPointerException.class, () -> new BundleReloadOrchestrator(
            null, ReloadFakes.verifierVerifying("p", 1),
            ReloadFakes.digesterReturning(List.of(ReloadFakes.schema("a"))),
            ReloadFakes.freshGate(), ReloadFakes.stubCompiler(),
            new GenerationalRegistry(), cfg(),
            new ReloadFakes.FakeClock(), new NoOpReloadObserver()));
    }
}
