package dev.vestitus.bundle.reload;

import dev.vestitus.trust.VerificationConfig;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Reload configuration. The compact constructor EAGERLY builds and retains a
 * {@link VerificationConfig} so an unanchored identity regexp or a blank OIDC
 * issuer fails fast at config construction — the ADR-003 "startup-validation
 * of the identity pin (anchored, mandatory issuer)" obligation — not at the
 * first reload.
 */
public record ReloadConfig(
        URI endpoint,
        Duration lastGoodWindow,
        int maxRetries,
        Duration retryBackoff,
        String identityRegexp,
        String oidcIssuer,
        String ghOwner,
        VerificationConfig verificationConfig) {

    public static final Duration DEFAULT_LAST_GOOD_WINDOW = Duration.ofHours(1);

    public ReloadConfig {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(lastGoodWindow, "lastGoodWindow");
        Objects.requireNonNull(retryBackoff, "retryBackoff");
        if (lastGoodWindow.isZero() || lastGoodWindow.isNegative()) {
            throw new IllegalArgumentException("lastGoodWindow must be positive");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
        if (retryBackoff.isNegative()) {
            throw new IllegalArgumentException("retryBackoff must be >= 0");
        }
        // Eager pin validation: VerificationConfig's compact ctor enforces a
        // fully anchored ^...$ regexp and a non-blank issuer; let it throw HERE.
        verificationConfig = new VerificationConfig(
            identityRegexp, oidcIssuer, Map.of("gh.owner", ghOwner));
    }

    /** Public ctor that derives the VerificationConfig from the pin fields. */
    public ReloadConfig(URI endpoint, Duration lastGoodWindow, int maxRetries,
                        Duration retryBackoff, String identityRegexp,
                        String oidcIssuer, String ghOwner) {
        this(endpoint, lastGoodWindow, maxRetries, retryBackoff,
            identityRegexp, oidcIssuer, ghOwner, null);
    }

    /** Convenience factory using {@link #DEFAULT_LAST_GOOD_WINDOW}. */
    public static ReloadConfig withDefaultWindow(URI endpoint, int maxRetries,
            Duration retryBackoff, String identityRegexp, String oidcIssuer,
            String ghOwner) {
        return new ReloadConfig(endpoint, DEFAULT_LAST_GOOD_WINDOW, maxRetries,
            retryBackoff, identityRegexp, oidcIssuer, ghOwner);
    }
}
