package dev.vestitus.bundle.reload;

import dev.vestitus.trust.VerificationConfig;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class ReloadConfigTest {

    private static final String OK_REGEXP = "^https://github\\.com/acme/.*$";
    private static final String OK_ISSUER = "https://token.actions.githubusercontent.com";

    @Test
    void wellFormedConfigBuildsAndExposesAVerificationConfig() {
        var c = new ReloadConfig(URI.create("http://h/bundles"),
            Duration.ofHours(1), 3, Duration.ofMillis(50),
            OK_REGEXP, OK_ISSUER, "acme");
        VerificationConfig vc = c.verificationConfig();
        assertEquals(OK_REGEXP, vc.expectedIdentityRegexp());
        assertEquals(OK_ISSUER, vc.oidcIssuer());
        assertEquals("acme", vc.extra().get("gh.owner"));
        assertEquals(Duration.ofHours(1), c.lastGoodWindow());
    }

    @Test
    void defaultLastGoodWindowConstantIsOneHour() {
        assertEquals(Duration.ofHours(1), ReloadConfig.DEFAULT_LAST_GOOD_WINDOW);
        var c = ReloadConfig.withDefaultWindow(URI.create("http://h/b"),
            2, Duration.ZERO, OK_REGEXP, OK_ISSUER, "acme");
        assertEquals(Duration.ofHours(1), c.lastGoodWindow());
    }

    @Test
    void unanchoredRegexpIsRejectedAtConstruction() {
        // The VerificationConfig compact ctor enforces ^...$ ; it must fire
        // HERE (config build), not at first reload.
        assertThrows(IllegalArgumentException.class, () -> new ReloadConfig(
            URI.create("http://h/b"), Duration.ofHours(1), 1, Duration.ZERO,
            "https://github\\.com/acme/.*", OK_ISSUER, "acme"));
    }

    @Test
    void blankIssuerIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new ReloadConfig(
            URI.create("http://h/b"), Duration.ofHours(1), 1, Duration.ZERO,
            OK_REGEXP, "  ", "acme"));
    }

    @Test
    void nonPositiveWindowAndNegativeRetriesRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ReloadConfig(
            URI.create("http://h/b"), Duration.ZERO, 1, Duration.ZERO,
            OK_REGEXP, OK_ISSUER, "acme"));
        assertThrows(IllegalArgumentException.class, () -> new ReloadConfig(
            URI.create("http://h/b"), Duration.ofHours(1), -1, Duration.ZERO,
            OK_REGEXP, OK_ISSUER, "acme"));
        assertThrows(IllegalArgumentException.class, () -> new ReloadConfig(
            URI.create("http://h/b"), Duration.ofHours(1), 1,
            Duration.ofMillis(-1), OK_REGEXP, OK_ISSUER, "acme"));
    }

    @Test
    void nullEndpointRejected() {
        assertThrows(NullPointerException.class, () -> new ReloadConfig(
            null, Duration.ofHours(1), 1, Duration.ZERO,
            OK_REGEXP, OK_ISSUER, "acme"));
    }
}
