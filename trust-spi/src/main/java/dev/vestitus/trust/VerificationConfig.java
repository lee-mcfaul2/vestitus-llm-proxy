package dev.vestitus.trust;

import java.util.Map;

/**
 * Operator-supplied verification config, startup-validatable (ADR-003 §4 ⑧).
 * The compact constructor enforces the GENERIC startup checks only:
 * {@code expectedIdentityRegexp} non-blank AND fully anchored ({@code ^...$}),
 * {@code oidcIssuer} non-blank (mandatory issuer pinning). Impl-specific
 * hardening (e.g. host-spanning-wildcard rejection) is the {@link
 * BundleVerifier} implementation's responsibility per its documented contract,
 * NOT here. {@code extra} carries impl-specific params (immutable copy) so the
 * SPI stays extensible for airgap/homebrew verifiers (ADR-003 D3) without
 * trust-spi knowing the impls.
 */
public record VerificationConfig(
        String expectedIdentityRegexp,
        String oidcIssuer,
        Map<String, String> extra) {
    public VerificationConfig {
        if (expectedIdentityRegexp == null || expectedIdentityRegexp.isBlank())
            throw new IllegalArgumentException(
                "expected-identity regexp must be non-blank");
        if (!expectedIdentityRegexp.startsWith("^") || !expectedIdentityRegexp.endsWith("$"))
            throw new IllegalArgumentException(
                "expected-identity regexp must be fully anchored (^...$)");
        if (oidcIssuer == null || oidcIssuer.isBlank())
            throw new IllegalArgumentException("oidc issuer must be non-blank");
        if (extra == null)
            throw new IllegalArgumentException("extra must be non-null");
        extra = Map.copyOf(extra);
    }
}
