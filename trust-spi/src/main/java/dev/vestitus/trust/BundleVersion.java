package dev.vestitus.trust;

/**
 * An opaque MONOTONE epoch the publisher stamps INTO the verifier-authenticated
 * content (ADR-003 D7) — never an unauthenticated sidecar field the endpoint
 * can mutate. The core no-rollback check (Plan 05d, verifier-independent)
 * accepts only a strictly-greater version (fail-forward). Whether the value is
 * a sequence number or a timestamp is publisher policy; vestitus only performs
 * the strict-greater comparison.
 */
public record BundleVersion(long value) implements Comparable<BundleVersion> {
    public BundleVersion {
        if (value < 0)
            throw new IllegalArgumentException("bundle version must be non-negative");
    }

    @Override
    public int compareTo(BundleVersion o) {
        return Long.compare(this.value, o.value);
    }

    public boolean isStrictlyAfter(BundleVersion other) {
        if (other == null)
            throw new IllegalArgumentException("other version must be non-null");
        return this.value > other.value;
    }
}
