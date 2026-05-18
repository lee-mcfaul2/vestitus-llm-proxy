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
}
