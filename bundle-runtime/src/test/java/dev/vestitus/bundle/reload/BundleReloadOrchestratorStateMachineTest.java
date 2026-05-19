package dev.vestitus.bundle.reload;

import dev.vestitus.authz.*;
import dev.vestitus.bundle.NoRollbackGate;
import dev.vestitus.mcpschema.McpSchema;
import dev.vestitus.trust.*;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BundleReloadOrchestratorStateMachineTest {

    private static final String RX = "^pub/.*$";
    private static final String ISS = "https://issuer.example";

    private static ReloadConfig cfg(int retries) {
        return new ReloadConfig(URI.create("http://h/b"), Duration.ofHours(1),
            retries, Duration.ZERO, RX, ISS, "acme");
    }

    private static AuthorizationRequest read(String mcpId) {
        return new AuthorizationRequest(
            new Principal("alice", Set.of(), Map.of()),
            "read",
            new ResourceRef(mcpId, "t", "f", Map.of()),
            Map.of());
    }

    private static BundleReloadOrchestrator orch(
            BundleSource src, BundleVerifier ver, BundleDigester dig,
            NoRollbackGate gate, PolicyCompiler comp,
            GenerationalRegistry reg, ReloadConfig cfg,
            MonotonicClock clk, ReloadObserver obs) {
        return new BundleReloadOrchestrator(src, ver, dig, gate, comp,
            reg, cfg, clk, obs);
    }

    @Test
    void neverAppliesUnverifiedOrOlder() {
        var reg = new GenerationalRegistry();
        var gate = ReloadFakes.freshGate();
        orch(() -> new FetchResult.Fetched(List.of(ReloadFakes.bundle("r"))),
             ReloadFakes.verifierVerifying("pub/acme", 5),
             ReloadFakes.digesterReturning(List.of(ReloadFakes.schema("crm"))),
             gate, ReloadFakes.stubCompiler(), reg, cfg(0),
             new ReloadFakes.FakeClock(), new NoOpReloadObserver())
            .attemptReload();
        assertTrue(reg.authorize("crm", read("crm")).allowed());

        var obs = new ReloadFakes.RecordingObserver();
        var out = orch(() -> new FetchResult.Fetched(List.of(ReloadFakes.bundle("r"))),
             ReloadFakes.verifierVerifying("pub/acme", 3),
             ReloadFakes.digesterReturning(List.of(ReloadFakes.schema("crm"))),
             gate, ReloadFakes.stubCompiler(), reg, cfg(0),
             new ReloadFakes.FakeClock(), obs)
            .attemptReload();
        assertEquals(ReloadOutcome.ABORTED_RETRYABLE, out);
        assertTrue(obs.events.stream().anyMatch(e -> e.startsWith("aggRejected:")),
            obs.events::toString);
        assertTrue(reg.authorize("crm", read("crm")).allowed());
    }

    // NOTE: the last-good WINDOW timer (lastSuccessfulApplyNanos) is
    // PER-ORCHESTRATOR-INSTANCE state by design (ADR-003 D9: one long-lived
    // orchestrator calling attemptReload() repeatedly on a monotonic clock).
    // Tests that exercise apply -> later unreachable MUST drive the SAME
    // orchestrator instance with a STATEFUL BundleSource (Fetched on the
    // first call, Unreachable after), NOT two separate orchestrators sharing
    // only reg/gate/clock — a fresh instance has an empty timer and would
    // wrongly cold-start fail-closed, masking the within/beyond-window paths.
    @Test
    void unreachableWithinWindowRetainsLastGood() {
        var reg = new GenerationalRegistry();
        var clock = new ReloadFakes.FakeClock();
        var obs = new ReloadFakes.RecordingObserver();
        java.util.concurrent.atomic.AtomicInteger calls =
            new java.util.concurrent.atomic.AtomicInteger();
        BundleSource src = () -> calls.getAndIncrement() == 0
            ? new FetchResult.Fetched(List.of(ReloadFakes.bundle("r")))
            : new FetchResult.Unreachable("net down");
        var o = orch(src,
            ReloadFakes.verifierVerifying("pub/acme", 1),
            ReloadFakes.digesterReturning(List.of(ReloadFakes.schema("crm"))),
            ReloadFakes.freshGate(), ReloadFakes.stubCompiler(), reg, cfg(0),
            clock, obs);
        o.attemptReload();
        assertTrue(reg.authorize("crm", read("crm")).allowed());

        clock.advance(Duration.ofMinutes(30).toNanos()); // within 1h window
        var out = o.attemptReload();
        assertEquals(ReloadOutcome.RETAINED_LAST_GOOD, out);
        assertTrue(reg.authorize("crm", read("crm")).allowed(),
            "last good must remain live");
        assertTrue(obs.events.stream().anyMatch(e -> e.startsWith("retained:")));
    }

    @Test
    void unreachableBeyondWindowFailsClosedDenyAll() {
        var reg = new GenerationalRegistry();
        var clock = new ReloadFakes.FakeClock();
        var obs = new ReloadFakes.RecordingObserver();
        java.util.concurrent.atomic.AtomicInteger calls =
            new java.util.concurrent.atomic.AtomicInteger();
        BundleSource src = () -> calls.getAndIncrement() == 0
            ? new FetchResult.Fetched(List.of(ReloadFakes.bundle("r")))
            : new FetchResult.Unreachable("net down");
        var o = orch(src,
            ReloadFakes.verifierVerifying("pub/acme", 1),
            ReloadFakes.digesterReturning(List.of(ReloadFakes.schema("crm"))),
            ReloadFakes.freshGate(), ReloadFakes.stubCompiler(), reg, cfg(0),
            clock, obs);
        o.attemptReload();
        assertTrue(reg.authorize("crm", read("crm")).allowed());

        clock.advance(Duration.ofHours(2).toNanos()); // beyond 1h window
        var out = o.attemptReload();
        assertEquals(ReloadOutcome.FAILED_CLOSED, out);
        assertFalse(reg.authorize("crm", read("crm")).allowed(),
            "beyond window => deny-all");
        assertTrue(obs.events.stream().anyMatch(e -> e.startsWith("failedClosed:")));
    }

    @Test
    void coldStartNoLastGoodFailsClosedDenyAll() {
        var reg = new GenerationalRegistry();
        var obs = new ReloadFakes.RecordingObserver();
        var out = orch(() -> new FetchResult.Unreachable("net down"),
             ReloadFakes.verifierVerifying("pub/acme", 1),
             ReloadFakes.digesterReturning(List.of(ReloadFakes.schema("crm"))),
             ReloadFakes.freshGate(), ReloadFakes.stubCompiler(), reg, cfg(0),
             new ReloadFakes.FakeClock(), obs)
            .attemptReload();
        assertEquals(ReloadOutcome.FAILED_CLOSED, out);
        assertFalse(reg.authorize("crm", read("crm")).allowed());
        assertTrue(obs.events.stream().anyMatch(e -> e.startsWith("failedClosed:")));
    }

    @Test
    void clockSkewDoesNotExtendWindow() {
        var reg = new GenerationalRegistry();
        var clock = new ReloadFakes.FakeClock();
        java.util.concurrent.atomic.AtomicInteger calls =
            new java.util.concurrent.atomic.AtomicInteger();
        BundleSource src = () -> calls.getAndIncrement() == 0
            ? new FetchResult.Fetched(List.of(ReloadFakes.bundle("r")))
            : new FetchResult.Unreachable("down");
        var o = orch(src,
            ReloadFakes.verifierVerifying("pub/acme", 1),
            ReloadFakes.digesterReturning(List.of(ReloadFakes.schema("crm"))),
            ReloadFakes.freshGate(), ReloadFakes.stubCompiler(), reg, cfg(0),
            clock, new NoOpReloadObserver());
        o.attemptReload();
        clock.advance(Duration.ofHours(3).toNanos());
        var out = o.attemptReload();
        assertEquals(ReloadOutcome.FAILED_CLOSED, out,
            "monotonic elapsed > window => closed; wall-clock is never read");

        var reg2 = new GenerationalRegistry();
        var clk2 = new ReloadFakes.FakeClock();
        java.util.concurrent.atomic.AtomicInteger calls2 =
            new java.util.concurrent.atomic.AtomicInteger();
        BundleSource src2 = () -> calls2.getAndIncrement() == 0
            ? new FetchResult.Fetched(List.of(ReloadFakes.bundle("r")))
            : new FetchResult.Unreachable("down");
        var o2 = orch(src2,
            ReloadFakes.verifierVerifying("pub/acme", 1),
            ReloadFakes.digesterReturning(List.of(ReloadFakes.schema("crm"))),
            ReloadFakes.freshGate(), ReloadFakes.stubCompiler(), reg2, cfg(0),
            clk2, new NoOpReloadObserver());
        o2.attemptReload();
        var out2 = o2.attemptReload();
        assertEquals(ReloadOutcome.RETAINED_LAST_GOOD, out2);
    }

    @Test
    void corruptBundleKOfNYieldsZeroCellsAdvanced() {
        var reg = new GenerationalRegistry();
        orch(() -> new FetchResult.Fetched(List.of(ReloadFakes.bundle("r"))),
             ReloadFakes.verifierVerifying("pub/acme", 1),
             ReloadFakes.digesterReturning(List.of(ReloadFakes.schema("base"))),
             ReloadFakes.freshGate(), ReloadFakes.stubCompiler(), reg, cfg(0),
             new ReloadFakes.FakeClock(), new NoOpReloadObserver())
            .attemptReload();
        McpAuthorizerRegistry before = reg.currentGeneration();
        assertTrue(reg.authorize("base", read("base")).allowed());

        java.util.concurrent.atomic.AtomicInteger i =
            new java.util.concurrent.atomic.AtomicInteger();
        BundleDigester flaky = payload -> {
            if (i.incrementAndGet() == 2) {
                throw new TrustException("corrupt k-th", null);
            }
            return List.of(ReloadFakes.schema("c" + i.get()));
        };
        var out = orch(() -> new FetchResult.Fetched(List.of(
                ReloadFakes.bundle("b1"), ReloadFakes.bundle("b2"),
                ReloadFakes.bundle("b3"))),
             ReloadFakes.verifierVerifying("pub/acme", 5),
             flaky, ReloadFakes.freshGate(), ReloadFakes.stubCompiler(),
             reg, cfg(0), new ReloadFakes.FakeClock(),
             new NoOpReloadObserver())
            .attemptReload();
        assertEquals(ReloadOutcome.ABORTED_RETRYABLE, out);
        assertSame(before, reg.currentGeneration(),
            "zero cells advanced — exact same generation instance");
        assertTrue(reg.authorize("base", read("base")).allowed());
        assertFalse(reg.authorize("c1", read("c1")).allowed());
    }

    @Test
    void weakVerifierCannotDisableCoreInvariants() {
        var reg = new GenerationalRegistry();
        var gate = ReloadFakes.freshGate();
        orch(() -> new FetchResult.Fetched(List.of(ReloadFakes.bundle("r"))),
             ReloadFakes.verifierVerifying("pub/acme", 10),
             ReloadFakes.digesterReturning(List.of(ReloadFakes.schema("crm"))),
             gate, ReloadFakes.stubCompiler(), reg, cfg(0),
             new ReloadFakes.FakeClock(), new NoOpReloadObserver())
            .attemptReload();
        McpAuthorizerRegistry afterFirst = reg.currentGeneration();

        var outOld = orch(() -> new FetchResult.Fetched(List.of(ReloadFakes.bundle("r"))),
             ReloadFakes.verifierVerifying("pub/acme", 4),
             ReloadFakes.digesterReturning(List.of(ReloadFakes.schema("crm"))),
             gate, ReloadFakes.stubCompiler(), reg, cfg(0),
             new ReloadFakes.FakeClock(), new NoOpReloadObserver())
            .attemptReload();
        assertEquals(ReloadOutcome.ABORTED_RETRYABLE, outOld,
            "NoRollbackGate still rejects even though verifier said Verified");
        assertSame(afterFirst, reg.currentGeneration());

        var reg2 = new GenerationalRegistry();
        var outBad = orch(() -> new FetchResult.Fetched(List.of(ReloadFakes.bundle("r"))),
             ReloadFakes.verifierVerifying("pub/acme", 7),
             ReloadFakes.digesterReturning(
                 List.of(ReloadFakes.selfPermissiveSchema("crm"))),
             ReloadFakes.freshGate(), ReloadFakes.stubCompiler(), reg2, cfg(0),
             new ReloadFakes.FakeClock(), new NoOpReloadObserver())
            .attemptReload();
        assertEquals(ReloadOutcome.ABORTED_RETRYABLE, outBad,
            "StructuralGate still rejects the self-permissive permit");
        assertFalse(reg2.authorize("crm", read("crm")).allowed(),
            "nothing installed — deny-all cold start retained");
    }

    @Test
    void verifyFailRetriesThenFailsClosed() {
        var reg = new GenerationalRegistry();
        var obs = new ReloadFakes.RecordingObserver();
        var o = orch(() -> new FetchResult.Fetched(List.of(ReloadFakes.bundle("r"))),
             ReloadFakes.verifierRejecting("bad sig"),
             ReloadFakes.digesterReturning(List.of(ReloadFakes.schema("crm"))),
             ReloadFakes.freshGate(), ReloadFakes.stubCompiler(), reg, cfg(2),
             new ReloadFakes.FakeClock(), obs);
        var out = o.runWithRetries(BundleReloadOrchestrator.Sleeper.NO_SLEEP);
        assertEquals(ReloadOutcome.FAILED_CLOSED, out);
        assertFalse(reg.authorize("crm", read("crm")).allowed());
        long verifyRejects = obs.events.stream()
            .filter(e -> e.startsWith("verifyRejected:")).count();
        assertEquals(3, verifyRejects, "1 initial + 2 retries");
        assertTrue(obs.events.stream().anyMatch(e -> e.startsWith("failedClosed:")));
    }

    @Test
    void duplicateMcpIdAcrossBundlesFailsClosed() {
        var reg = new GenerationalRegistry();
        var obs = new ReloadFakes.RecordingObserver();
        BundleDigester dig = payload -> List.of(ReloadFakes.schema("crm"));
        var out = orch(() -> new FetchResult.Fetched(List.of(
                ReloadFakes.bundle("b1"), ReloadFakes.bundle("b2"))),
             ReloadFakes.verifierVerifying("pub/acme", 5),
             dig, ReloadFakes.freshGate(), ReloadFakes.stubCompiler(),
             reg, cfg(0), new ReloadFakes.FakeClock(), obs)
            .attemptReload();
        assertEquals(ReloadOutcome.ABORTED_RETRYABLE, out);
        assertFalse(reg.authorize("crm", read("crm")).allowed());
        assertTrue(obs.events.stream()
            .anyMatch(e -> e.contains("duplicate mcpId across bundles")),
            obs.events::toString);
    }

    @Test
    void successfulReloadInstallsAllCellsAtomically() {
        var reg = new GenerationalRegistry();
        var obs = new ReloadFakes.RecordingObserver();
        var out = orch(() -> new FetchResult.Fetched(List.of(ReloadFakes.bundle("r"))),
             ReloadFakes.verifierVerifying("pub/acme", 8),
             ReloadFakes.digesterReturning(List.of(
                 ReloadFakes.schema("a"), ReloadFakes.schema("b"),
                 ReloadFakes.schema("c"))),
             ReloadFakes.freshGate(), ReloadFakes.stubCompiler(), reg, cfg(0),
             new ReloadFakes.FakeClock(), obs)
            .attemptReload();
        assertEquals(ReloadOutcome.APPLIED, out);
        assertTrue(reg.authorize("a", read("a")).allowed());
        assertTrue(reg.authorize("b", read("b")).allowed());
        assertTrue(reg.authorize("c", read("c")).allowed());
        assertTrue(obs.events.stream().anyMatch(e -> e.equals("applied:8:3")),
            obs.events::toString);
    }

    @Test
    void reloadObserverEventsAreEmittedAndDecoupledFromPerRequestPath() {
        var reg = new GenerationalRegistry();
        var obs = new ReloadFakes.RecordingObserver();
        orch(() -> new FetchResult.Fetched(List.of(ReloadFakes.bundle("r"))),
             ReloadFakes.verifierVerifying("pub/acme", 2),
             ReloadFakes.digesterReturning(List.of(ReloadFakes.schema("crm"))),
             ReloadFakes.freshGate(), ReloadFakes.stubCompiler(), reg, cfg(0),
             new ReloadFakes.FakeClock(), obs)
            .attemptReload();
        assertTrue(obs.events.stream().anyMatch(e -> e.startsWith("applied:")));
        int eventsAfterApply = obs.events.size();
        reg.authorize("crm", read("crm"));
        reg.authorize("crm", read("crm"));
        assertEquals(eventsAfterApply, obs.events.size(),
            "per-request authorize must be decoupled from the reload observer");
    }

    @Test
    void badIdentityPinIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new ReloadConfig(
            URI.create("http://h/b"), Duration.ofHours(1), 0, Duration.ZERO,
            "pub/.*", ISS, "acme"));
    }
}
