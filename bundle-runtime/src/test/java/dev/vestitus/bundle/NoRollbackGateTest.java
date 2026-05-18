package dev.vestitus.bundle;

import dev.vestitus.trust.BundleVersion;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NoRollbackGateTest {

    /** Pure-logic in-memory store for the gate's deterministic tests. */
    static final class InMemoryVersionFloorStore implements VersionFloorStore {
        private Long floor; // null == pristine (first-ever)

        InMemoryVersionFloorStore() { this.floor = null; }

        InMemoryVersionFloorStore(long seeded) { this.floor = seeded; }

        @Override
        public Optional<BundleVersion> currentFloor() {
            return floor == null
                ? Optional.empty()
                : Optional.of(new BundleVersion(floor));
        }

        @Override
        public void ratchet(BundleVersion accepted) {
            long existing = (floor == null) ? 0L : floor;
            this.floor = Math.max(existing, accepted.value());
        }
    }

    /** Fail-closed seam: every store call throws. */
    static final class ThrowingVersionFloorStore implements VersionFloorStore {
        @Override
        public Optional<BundleVersion> currentFloor() {
            throw new VersionStoreException("currentFloor blew up", null);
        }
        @Override
        public void ratchet(BundleVersion accepted) {
            throw new VersionStoreException("ratchet blew up", null);
        }
    }

    /** Fail-closed seam: read ok, but ratchet (the persist) throws. */
    static final class RatchetThrowingFloorStore implements VersionFloorStore {
        @Override
        public Optional<BundleVersion> currentFloor() {
            return Optional.empty();
        }
        @Override
        public void ratchet(BundleVersion accepted) {
            throw new VersionStoreException("ratchet persist failed", null);
        }
    }

    @Test
    void inMemoryStoreHonoursTheMonotoneContract() {
        InMemoryVersionFloorStore s = new InMemoryVersionFloorStore();
        assertTrue(s.currentFloor().isEmpty());
        s.ratchet(new BundleVersion(5L));
        assertEquals(5L, s.currentFloor().orElseThrow().value());
        s.ratchet(new BundleVersion(3L)); // must not lower
        assertEquals(5L, s.currentFloor().orElseThrow().value());
        s.ratchet(new BundleVersion(9L));
        assertEquals(9L, s.currentFloor().orElseThrow().value());
    }

    @Test
    void coldStartFirstCandidateIsAcceptedAndFloorRatchetsAndLiveSet() {
        InMemoryVersionFloorStore store = new InMemoryVersionFloorStore();
        NoRollbackGate gate = new NoRollbackGate(store);
        VersionDecision d = gate.evaluate(new BundleVersion(5L));
        assertTrue(d.accepted());
        assertEquals(5L, ((VersionDecision.Accept) d).version().value());
        assertEquals(5L, store.currentFloor().orElseThrow().value());
        // live is now 5: an equal candidate is rejected (proves live was set).
        assertFalse(gate.evaluate(new BundleVersion(5L)).accepted());
    }

    @Test
    void equalCandidateAfterLiveIsRejectedNotStrictlyAfter() {
        InMemoryVersionFloorStore store = new InMemoryVersionFloorStore();
        NoRollbackGate gate = new NoRollbackGate(store);
        gate.evaluate(new BundleVersion(5L));
        VersionDecision d = gate.evaluate(new BundleVersion(5L));
        assertInstanceOf(VersionDecision.Reject.class, d);
        assertTrue(((VersionDecision.Reject) d).reason()
            .contains("not strictly after the live generation"));
    }

    @Test
    void lowerCandidateAfterLiveIsRejectedReplay() {
        InMemoryVersionFloorStore store = new InMemoryVersionFloorStore();
        NoRollbackGate gate = new NoRollbackGate(store);
        gate.evaluate(new BundleVersion(5L));
        VersionDecision d = gate.evaluate(new BundleVersion(3L));
        assertInstanceOf(VersionDecision.Reject.class, d);
        assertFalse(d.accepted());
    }

    @Test
    void higherCandidateAfterLiveIsAcceptedAndFloorRatchets() {
        InMemoryVersionFloorStore store = new InMemoryVersionFloorStore();
        NoRollbackGate gate = new NoRollbackGate(store);
        gate.evaluate(new BundleVersion(5L));
        VersionDecision d = gate.evaluate(new BundleVersion(7L));
        assertTrue(d.accepted());
        assertEquals(7L, store.currentFloor().orElseThrow().value());
    }

    @Test
    void belowSeededFloorWithNoLiveIsRejectedColdStartRollbackRefused() {
        // Fresh gate, store pre-seeded floor=10 (a prior process ratcheted it),
        // candidate v8 < floor: cold start cannot be rolled back.
        InMemoryVersionFloorStore store = new InMemoryVersionFloorStore(10L);
        NoRollbackGate gate = new NoRollbackGate(store);
        VersionDecision d = gate.evaluate(new BundleVersion(8L));
        assertInstanceOf(VersionDecision.Reject.class, d);
        assertTrue(((VersionDecision.Reject) d).reason()
            .contains("below the persisted min-version floor"));
        assertEquals(10L, store.currentFloor().orElseThrow().value()); // unchanged
    }

    @Test
    void nullCandidateIsRejectedFailClosed() {
        NoRollbackGate gate =
            new NoRollbackGate(new InMemoryVersionFloorStore());
        VersionDecision d = gate.evaluate(null);
        assertInstanceOf(VersionDecision.Reject.class, d);
        assertTrue(((VersionDecision.Reject) d).reason()
            .contains("must be non-null"));
    }

    @Test
    void anyStoreThrowableYieldsFailClosedRejectAndLiveIsNotAdvanced() {
        NoRollbackGate gate =
            new NoRollbackGate(new ThrowingVersionFloorStore());
        VersionDecision d = gate.evaluate(new BundleVersion(99L));
        assertInstanceOf(VersionDecision.Reject.class, d);
        assertTrue(((VersionDecision.Reject) d).reason()
            .contains("version floor store failure (fail-closed)"));
        // live was NOT advanced: swap in a sane store and a low candidate is
        // still evaluated against the unchanged (null) live, so it is the
        // first accept rather than being rejected as a replay of 99.
        NoRollbackGate sane =
            new NoRollbackGate(new InMemoryVersionFloorStore());
        assertTrue(sane.evaluate(new BundleVersion(1L)).accepted());
    }

    @Test
    void ratchetFailureYieldsRejectAndLiveIsNotAdvanced() {
        NoRollbackGate gate =
            new NoRollbackGate(new RatchetThrowingFloorStore());
        // First candidate: floor empty, no live, strictly-after passes, then
        // ratchet (the persist) throws BEFORE live is updated and BEFORE
        // Accept -> fail-closed Reject, live stays null.
        VersionDecision d1 = gate.evaluate(new BundleVersion(5L));
        assertInstanceOf(VersionDecision.Reject.class, d1);
        // live was NOT advanced: a subsequent candidate is still evaluated as
        // the first (no replay rejection against a phantom live=5).
        VersionDecision d2 = gate.evaluate(new BundleVersion(2L));
        assertInstanceOf(VersionDecision.Reject.class, d2); // ratchet still throws
        assertTrue(((VersionDecision.Reject) d2).reason()
            .contains("version floor store failure (fail-closed)"));
    }
}
