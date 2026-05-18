package dev.vestitus.bundle;

import dev.vestitus.trust.BundleVersion;

/**
 * The no-rollback gate's verdict (ADR-003 D7). {@link Accept} means the
 * candidate {@link BundleVersion} — which the {@code BundleVerifier} already
 * authenticated (the version lives inside the signed/attested content, never an
 * unauthenticated sidecar) — is strictly newer than the live generation AND
 * at/above the persisted operator min-version floor, and the floor has been
 * ratcheted forward. {@link Reject} is fail-closed: any rollback, replay,
 * cold-start downgrade, or floor-store failure. Mirrors
 * {@code dev.vestitus.authz.AuthorizationDecision}'s sealed/record/compact-ctor
 * discipline.
 */
public sealed interface VersionDecision
        permits VersionDecision.Accept, VersionDecision.Reject {

    /** The candidate was strictly newer than live and at/above the floor. */
    record Accept(BundleVersion version) implements VersionDecision {
        public Accept {
            if (version == null)
                throw new IllegalArgumentException("accepted version required");
        }
    }

    /** Fail-closed: rollback / replay / cold-start downgrade / store failure. */
    record Reject(String reason) implements VersionDecision {
        public Reject {
            if (reason == null || reason.isBlank())
                throw new IllegalArgumentException("reject reason required");
        }
    }

    static VersionDecision accept(BundleVersion v) { return new Accept(v); }

    static VersionDecision reject(String reason) { return new Reject(reason); }

    default boolean accepted() {
        return switch (this) {
            case Accept a -> true;
            case Reject r -> false;
        };
    }
}
