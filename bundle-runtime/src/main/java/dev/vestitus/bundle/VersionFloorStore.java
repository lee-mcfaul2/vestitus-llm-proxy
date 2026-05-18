package dev.vestitus.bundle;

import dev.vestitus.trust.BundleVersion;
import java.util.Optional;

/**
 * The persisted, ratcheting minimum-version floor for the no-rollback gate
 * (ADR-003 D7 — the floor "ratchets forward and survives restart" so cold start
 * cannot be rolled back).
 *
 * <p><b>Not an ADR-003 extension SPI.</b> It is a core persistence detail
 * exposed as an interface only for the project's test-seam discipline (so
 * {@link NoRollbackGate} can be unit-tested against an in-memory and a throwing
 * store) and as an ops file-location config point. There is no runtime
 * code-load here (ADR-003 D2).</p>
 *
 * <p><b>Contract / invariants:</b></p>
 * <ul>
 *   <li>{@link #currentFloor()} returns {@link Optional#empty()} ONLY for a
 *       first-ever pristine store. A corrupt/unreadable persisted floor MUST
 *       throw (a {@link VersionStoreException}), never be silently treated as
 *       empty — silently treating corrupt-as-empty would be a rollback hole.</li>
 *   <li>{@link #ratchet(BundleVersion)} is monotone: it persists
 *       {@code max(existing, accepted)} and MUST NEVER lower the floor.</li>
 *   <li>{@link #ratchet(BundleVersion)} MUST be crash-safe: a crash mid-write
 *       cannot corrupt or lower the persisted floor (see
 *       {@link FileVersionFloorStore}'s atomic-move implementation).</li>
 * </ul>
 */
public interface VersionFloorStore {

    /**
     * The current persisted floor, or {@link Optional#empty()} ONLY for a
     * first-ever pristine store.
     *
     * @throws VersionStoreException if the persisted floor is corrupt or
     *         unreadable (never silently empty — that would be a rollback hole)
     */
    Optional<BundleVersion> currentFloor();

    /**
     * Ratchet the floor forward to {@code max(existing, accepted)} and persist
     * it crash-safely. Monotone: MUST NEVER lower the floor.
     *
     * @throws VersionStoreException if the floor could not be persisted
     */
    void ratchet(BundleVersion accepted);
}
