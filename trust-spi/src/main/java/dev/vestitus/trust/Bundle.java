package dev.vestitus.trust;

/**
 * The raw fetched unit pulled from the configured endpoint, before
 * verification. Deliberately NOT a record: it holds {@code byte[]} state, and
 * Java records would generate identity-based array {@code equals}/{@code
 * hashCode} (a latent correctness bug); a fetched transient unit also has no
 * value-equality meaning. No {@code equals}/{@code hashCode} override —
 * identity semantics, documented.
 *
 * <ul>
 *   <li>{@code payload} — the publisher's raw bytes to be authenticated (the
 *       {@link BundleVerifier} input; never consumed by a digester directly).</li>
 *   <li>{@code signatureMaterial} — the accompanying detached signature /
 *       sigstore-bundle bytes; impl-interpreted, MAY be empty for an impl that
 *       fetches provenance out-of-band.</li>
 *   <li>{@code sourceRef} — opaque origin id for audit/diagnostics.</li>
 * </ul>
 * Construction clones the inputs and the accessors clone out, so the internal
 * arrays are never reachable by a caller.
 */
public final class Bundle {
    private final byte[] payload;
    private final byte[] signatureMaterial;
    private final String sourceRef;

    public Bundle(byte[] payload, byte[] signatureMaterial, String sourceRef) {
        if (payload == null)
            throw new IllegalArgumentException("bundle payload must be non-null");
        if (signatureMaterial == null)
            throw new IllegalArgumentException("bundle signatureMaterial must be non-null");
        if (sourceRef == null || sourceRef.isBlank())
            throw new IllegalArgumentException("bundle sourceRef must be non-blank");
        this.payload = payload.clone();
        this.signatureMaterial = signatureMaterial.clone();
        this.sourceRef = sourceRef;
    }

    public byte[] payload() { return payload.clone(); }

    public byte[] signatureMaterial() { return signatureMaterial.clone(); }

    public String sourceRef() { return sourceRef; }
}
