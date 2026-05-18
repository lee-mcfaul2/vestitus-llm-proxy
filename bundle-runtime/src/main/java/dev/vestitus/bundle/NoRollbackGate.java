package dev.vestitus.bundle;

import dev.vestitus.trust.BundleVersion;
import java.util.Objects;
import java.util.Optional;

/**
 * The core ADR-003 D7 no-rollback gate. Verifier-independent (ADR-003 D6): it
 * consumes a {@link BundleVersion} the {@code BundleVerifier} <i>already
 * authenticated</i> (the version lives inside the signed/attested content,
 * never an unauthenticated sidecar) and refuses any rollback/replay so a
 * weak/sabotaged custom verifier cannot disable rollback protection.
 *
 * <p><b>Evaluation order (locked):</b> floor-check, then live-check, then
 * ratchet-then-update-live-then-Accept. The persisted ratchet runs BEFORE the
 * in-memory {@code live} update and BEFORE the {@code Accept} return: if the
 * persisted ratchet fails, NOTHING is accepted — the store call throws, is
 * caught below, and a fail-closed {@code Reject} is returned with {@code live}
 * NOT advanced. Accepting in memory while failing to persist the floor would
 * defeat anti-rollback across a restart.</p>
 *
 * <p>{@code evaluate} is {@code synchronized} because reloads are serialized
 * (one reload at a time) — this is NOT a hot per-request path, so a monitor is
 * correct and sufficient. ANY {@link Throwable} from the floor store ⇒
 * fail-closed {@link VersionDecision.Reject}: an untrustworthy floor store must
 * never permit acceptance.</p>
 */
public final class NoRollbackGate {

    private final VersionFloorStore store;
    private BundleVersion live; // null at cold start before any accept

    public NoRollbackGate(VersionFloorStore store) {
        this.store = Objects.requireNonNull(store, "store required");
    }

    public synchronized VersionDecision evaluate(BundleVersion candidate) {
        try {
            if (candidate == null) {
                return VersionDecision.reject(
                    "candidate version must be non-null (fail-closed)");
            }
            Optional<BundleVersion> floor = store.currentFloor();
            if (floor.isPresent() && candidate.compareTo(floor.get()) < 0) {
                return VersionDecision.reject("candidate " + candidate.value()
                    + " is below the persisted min-version floor "
                    + floor.get().value()
                    + "; fail-forward only (cold-start rollback refused)");
            }
            if (live != null && !candidate.isStrictlyAfter(live)) {
                return VersionDecision.reject("candidate " + candidate.value()
                    + " is not strictly after the live generation "
                    + live.value()
                    + "; fail-forward only (replay/rollback refused)");
            }
            store.ratchet(candidate);
            this.live = candidate;
            return VersionDecision.accept(candidate);
        } catch (Throwable t) {
            return VersionDecision.reject(
                "version floor store failure (fail-closed): " + t);
        }
    }
}
