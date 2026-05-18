package dev.vestitus.trust.verify.sigstore;

import dev.vestitus.trust.Bundle;
import dev.vestitus.trust.VerificationConfig;
import dev.vestitus.trust.VerificationOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * CI-ONLY real cosign/gh integration test (the ADR-003 D3 default checkers,
 * end-to-end against a self-produced fully sigstore+SLSA-signed test bundle).
 * {@code @EnabledIfEnvironmentVariable} gates it so the local offline reactor
 * SKIPS it entirely — the offline-deterministic-reactor invariant. CI sets
 * VESTITUS_SIGSTORE_ITEST=1 and the blob/sig/cosign/gh/identity/owner env.
 */
@EnabledIfEnvironmentVariable(named = "VESTITUS_SIGSTORE_ITEST", matches = "1")
class SigstoreSlsaBundleVerifierItest {

    private static String env(String k) {
        String v = System.getenv(k);
        assertNotNull(v, "missing env " + k);
        assertFalse(v.isBlank(), "blank env " + k);
        return v;
    }

    @Test
    void realCosignAndGhVerifyTheSelfProducedSignedBundle() throws Exception {
        Path blob = Path.of(env("VESTITUS_ITEST_BLOB"));
        Path sig = Path.of(env("VESTITUS_ITEST_SIG"));
        var verifier = new SigstoreSlsaBundleVerifier(
            new ProcessCommandRunner(),
            env("VESTITUS_ITEST_COSIGN"), env("VESTITUS_ITEST_GH"));
        var cfg = new VerificationConfig(
            env("VESTITUS_ITEST_IDENTITY_REGEXP"),
            "https://token.actions.githubusercontent.com",
            Map.of("gh.owner", env("VESTITUS_ITEST_OWNER")));

        byte[] payload = Files.readAllBytes(blob);
        byte[] sigMat = Files.readAllBytes(sig);

        VerificationOutcome ok = verifier.verify(
            new Bundle(payload, sigMat, "itest"), cfg);
        assertTrue(ok.isVerified(), ok.toString());
        assertEquals(1L,
            ((VerificationOutcome.Verified) ok).version().value());

        // Negative: flip one payload byte -> cosign rejects -> Rejected.
        byte[] tampered = payload.clone();
        tampered[0] ^= 0x01;
        VerificationOutcome bad = verifier.verify(
            new Bundle(tampered, sigMat, "itest"), cfg);
        assertInstanceOf(VerificationOutcome.Rejected.class, bad);
    }
}
