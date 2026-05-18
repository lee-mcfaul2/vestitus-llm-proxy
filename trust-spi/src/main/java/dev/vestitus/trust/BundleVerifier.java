package dev.vestitus.trust;

/**
 * The bundle-authentication SPI seam (ADR-003 D2/D3). A compile-time extension
 * point — NOT sealed; the only extension mechanism, no runtime code-load. The
 * implementer is a trusted, security-literate, compile-time integrator (D2),
 * explicitly NOT in the runtime adversary set. The default sigstore/SLSA impl
 * is Plan 05g.
 *
 * <p><b>Security contract a conformant implementation MUST uphold (ADR-003 D6
 * documented contract):</b></p>
 * <ul>
 *   <li><b>Fail-closed:</b> never grant on uncertainty. SHOULD return
 *       {@link VerificationOutcome.Rejected} rather than throw; and callers
 *       MUST treat any thrown exception as a rejection (stated both ways:
 *       the impl should not throw, the caller must not trust a thrown impl).</li>
 *   <li><b>Authenticated version:</b> MUST populate
 *       {@link VerificationOutcome.Verified#version()} from content the impl
 *       cryptographically/authentically bound (ADR-003 D7) — NEVER an
 *       unauthenticated sidecar field.</li>
 *   <li><b>Authenticated subject:</b> MUST populate
 *       {@link VerificationOutcome.Verified#subjectId()} with the verified
 *       signer identity (the core, Plan 05h, binds it to the target
 *       {@code mcpId}).</li>
 *   <li><b>Pinning + hardening:</b> MUST honour the {@code config} identity
 *       regexp and OIDC issuer pinning, and MUST perform impl-specific
 *       startup/identity hardening (e.g. host-spanning-wildcard rejection)
 *       per ADR-003 §4 ⑧ — the generic anchoring check is in
 *       {@link VerificationConfig}; impl-specific rejection is the impl's.</li>
 * </ul>
 *
 * <p>There is NO mandatory crypto floor (airgap/homebrew verifiers are
 * allowed, ADR-003 D3). A weak custom verifier downgrades authenticity for
 * that operator's environment ONLY — the load-bearing no-rollback (D7),
 * minimum structural gate (D5), set-atomic (D8) and fail-closed (D9)
 * invariants are enforced by the CORE downstream, independent of this seam
 * (ADR-003 D6), so a weak verifier cannot disable them.</p>
 */
public interface BundleVerifier {
    VerificationOutcome verify(Bundle bundle, VerificationConfig config);
}
