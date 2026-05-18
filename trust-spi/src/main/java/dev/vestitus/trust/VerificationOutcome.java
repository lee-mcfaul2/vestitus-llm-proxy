package dev.vestitus.trust;

/**
 * The transient control-flow verdict a {@link BundleVerifier} returns.
 * Sealed (mirrors {@code AuthorizationDecision}); permits exactly
 * {@link Verified} and {@link Rejected}.
 */
public sealed interface VerificationOutcome
        permits VerificationOutcome.Verified, VerificationOutcome.Rejected {

    /**
     * A successful verdict. A transient control-flow value, NOT a value type:
     * its {@code equals}/{@code hashCode} are the record defaults and are NOT
     * meaningful on {@code authenticatedPayload} (array identity) — never
     * store or compare a {@code Verified} by value.
     *
     * <ul>
     *   <li>{@code authenticatedPayload} — EXACTLY the bytes the verifier
     *       vouched for, and the ONLY bytes a {@link BundleDigester} may
     *       consume (never the raw {@link Bundle#payload()}).</li>
     *   <li>{@code subjectId} — the verified signer/subject identity the core
     *       (Plan 05h) binds to the target {@code mcpId}.</li>
     *   <li>{@code version} — the authenticated monotone version the core
     *       no-rollback check (Plan 05d) consumes (never a sidecar).</li>
     * </ul>
     * Cloned in (compact ctor) and out (accessor) so the internal array is
     * never reachable.
     */
    record Verified(byte[] authenticatedPayload, String subjectId, BundleVersion version)
            implements VerificationOutcome {
        public Verified {
            if (authenticatedPayload == null)
                throw new IllegalArgumentException("authenticatedPayload must be non-null");
            if (subjectId == null || subjectId.isBlank())
                throw new IllegalArgumentException("subjectId must be non-blank");
            if (version == null)
                throw new IllegalArgumentException("version must be non-null");
            authenticatedPayload = authenticatedPayload.clone();
        }

        @Override
        public byte[] authenticatedPayload() {
            return authenticatedPayload.clone();
        }
    }

    /** A fail-closed verdict carrying a non-blank reason. */
    record Rejected(String reason) implements VerificationOutcome {
        public Rejected {
            if (reason == null || reason.isBlank())
                throw new IllegalArgumentException("rejection reason required");
        }
    }

    static VerificationOutcome verified(byte[] payload, String subjectId, BundleVersion v) {
        return new Verified(payload, subjectId, v);
    }

    static VerificationOutcome rejected(String reason) {
        return new Rejected(reason);
    }

    default boolean isVerified() {
        return switch (this) {
            case Verified v -> true;
            case Rejected r -> false;
        };
    }
}
