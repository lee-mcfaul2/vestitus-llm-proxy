package dev.vestitus.trust.verify.sigstore;

import dev.vestitus.trust.Bundle;
import dev.vestitus.trust.VerificationConfig;
import dev.vestitus.trust.VerificationOutcome;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SigstoreSlsaBundleVerifierTest {

    // Scripted, deterministic, OFFLINE fake — no real cosign/gh, no network.
    static final class FakeCommandRunner implements CommandRunner {
        final Map<String, Exec> scripted = new HashMap<>();
        final List<List<String>> captured = new ArrayList<>();
        RuntimeException throwOnRun;
        @Override public Exec run(List<String> argv) {
            captured.add(List.copyOf(argv));
            if (throwOnRun != null) throw throwOnRun;
            String key = argv.size() > 1 ? argv.get(1) : "";
            Exec e = scripted.get(key);
            return e != null ? e : new Exec(99, "", "unscripted: " + key);
        }
    }

    private static final String IDENT =
        "^https://github.com/lee-mcfaul2/vestitus-llm-proxy/"
        + ".github/workflows/bundle-verifier-sigstore-itest.yml@"
        + "refs/tags/bundle-verifier-sigstore-v.*$";
    private static final String ISSUER =
        "https://token.actions.githubusercontent.com";

    private static VerificationConfig cfg(Map<String, String> extra) {
        return new VerificationConfig(IDENT, ISSUER, extra);
    }
    private static Map<String, String> owner(String o) {
        Map<String, String> m = new HashMap<>();
        m.put("gh.owner", o);
        return m;
    }
    private static Bundle bundle(String payload) {
        return new Bundle(payload.getBytes(StandardCharsets.UTF_8),
            "sigbytes".getBytes(StandardCharsets.UTF_8), "ref-1");
    }

    @Test
    void cosign0Gh0WithValidVersionVerifiesAndBindsSubjectAndVersion() {
        FakeCommandRunner f = new FakeCommandRunner();
        f.scripted.put("verify-blob", new CommandRunner.Exec(0, "ok", ""));
        f.scripted.put("attestation", new CommandRunner.Exec(0, "ok", ""));
        var v = new SigstoreSlsaBundleVerifier(f, "/usr/bin/cosign", "/usr/bin/gh");
        VerificationOutcome o = v.verify(
            bundle("{\"version\":7,\"schemas\":[]}"), cfg(owner("lee-mcfaul2")));
        assertTrue(o.isVerified(), o.toString());
        var ver = (VerificationOutcome.Verified) o;
        assertEquals(IDENT, ver.subjectId());
        assertEquals(7L, ver.version().value());
        assertArrayEquals("{\"version\":7,\"schemas\":[]}"
            .getBytes(StandardCharsets.UTF_8), ver.authenticatedPayload());
        // EXACT cosign argv (proves the real CI command form).
        List<String> c = f.captured.get(0);
        assertEquals("/usr/bin/cosign", c.get(0));
        assertEquals("verify-blob", c.get(1));
        assertEquals("--new-bundle-format", c.get(2));
        assertEquals("--bundle", c.get(3));
        assertTrue(c.get(4).endsWith("blob.sigstore"), c.get(4));
        assertEquals("--certificate-identity-regexp", c.get(5));
        assertEquals(IDENT, c.get(6));
        assertEquals("--certificate-oidc-issuer", c.get(7));
        assertEquals(ISSUER, c.get(8));
        assertTrue(c.get(9).endsWith("blob"), c.get(9));
        assertEquals(10, c.size());
        // EXACT gh argv.
        List<String> g = f.captured.get(1);
        assertEquals(List.of("/usr/bin/gh", "attestation", "verify",
            g.get(3), "--owner", "lee-mcfaul2"), g);
        assertTrue(g.get(3).endsWith("blob"), g.get(3));
    }

    @Test
    void cosignNonZeroIsRejectedAndGhNotInvoked() {
        FakeCommandRunner f = new FakeCommandRunner();
        f.scripted.put("verify-blob", new CommandRunner.Exec(1, "", "bad sig"));
        var v = new SigstoreSlsaBundleVerifier(f, "/usr/bin/cosign", "/usr/bin/gh");
        VerificationOutcome o = v.verify(
            bundle("{\"version\":1}"), cfg(owner("lee-mcfaul2")));
        var r = (VerificationOutcome.Rejected) o;
        assertTrue(r.reason().contains("cosign"), r.reason());
        // fail-fast: gh was NOT invoked (only the cosign argv captured).
        assertEquals(1, f.captured.size());
        assertEquals("verify-blob", f.captured.get(0).get(1));
    }

    @Test
    void cosign0ButGhNonZeroIsRejectedNamingGh() {
        FakeCommandRunner f = new FakeCommandRunner();
        f.scripted.put("verify-blob", new CommandRunner.Exec(0, "ok", ""));
        f.scripted.put("attestation", new CommandRunner.Exec(1, "", "no att"));
        var v = new SigstoreSlsaBundleVerifier(f, "/usr/bin/cosign", "/usr/bin/gh");
        var r = (VerificationOutcome.Rejected) v.verify(
            bundle("{\"version\":1}"), cfg(owner("lee-mcfaul2")));
        assertTrue(r.reason().contains("gh attestation"), r.reason());
    }

    @Test
    void verifiedButPayloadVersionInvalidIsRejected() {
        for (String bad : new String[]{
                "{\"schemas\":[]}",          // missing
                "{\"version\":\"x\"}",       // non-integer
                "{\"version\":-1}",          // negative
                "not-json-at-all"}) {        // not JSON
            FakeCommandRunner f = new FakeCommandRunner();
            f.scripted.put("verify-blob", new CommandRunner.Exec(0, "ok", ""));
            f.scripted.put("attestation", new CommandRunner.Exec(0, "ok", ""));
            var v = new SigstoreSlsaBundleVerifier(
                f, "/usr/bin/cosign", "/usr/bin/gh");
            var r = (VerificationOutcome.Rejected) v.verify(
                bundle(bad), cfg(owner("lee-mcfaul2")));
            assertTrue(r.reason().contains(
                "missing a valid non-negative top-level integer \"version\""),
                bad + " -> " + r.reason());
        }
    }

    @Test
    void missingGhOwnerIsRejectedAndCosignNotInvoked() {
        FakeCommandRunner f = new FakeCommandRunner();
        var v = new SigstoreSlsaBundleVerifier(f, "/usr/bin/cosign", "/usr/bin/gh");
        var r = (VerificationOutcome.Rejected) v.verify(
            bundle("{\"version\":1}"), cfg(new HashMap<>()));
        assertTrue(r.reason().contains("gh.owner"), r.reason());
        assertTrue(f.captured.isEmpty(), "cosign must not run");
    }

    @Test
    void aThrowingRunnerIsCaughtAndRejectedNotPropagated() {
        FakeCommandRunner f = new FakeCommandRunner();
        f.throwOnRun = new IllegalStateException("seam blew up");
        var v = new SigstoreSlsaBundleVerifier(f, "/usr/bin/cosign", "/usr/bin/gh");
        VerificationOutcome o = v.verify(
            bundle("{\"version\":1}"), cfg(owner("lee-mcfaul2")));
        var r = (VerificationOutcome.Rejected) o;
        assertTrue(r.reason().contains("sigstore verify error (fail-closed)"),
            r.reason());
    }

    @Test
    void nullBundleOrNullConfigIsRejected() {
        var v = new SigstoreSlsaBundleVerifier(
            new FakeCommandRunner(), "/usr/bin/cosign", "/usr/bin/gh");
        assertInstanceOf(VerificationOutcome.Rejected.class,
            v.verify(null, cfg(owner("o"))));
        assertInstanceOf(VerificationOutcome.Rejected.class,
            v.verify(bundle("{\"version\":1}"), null));
    }

    @Test
    void ctorRejectsNullOrBlankArgs() {
        CommandRunner f = new FakeCommandRunner();
        assertThrows(NullPointerException.class,
            () -> new SigstoreSlsaBundleVerifier(null, "/c", "/g"));
        assertThrows(IllegalArgumentException.class,
            () -> new SigstoreSlsaBundleVerifier(f, " ", "/g"));
        assertThrows(IllegalArgumentException.class,
            () -> new SigstoreSlsaBundleVerifier(f, "/c", ""));
    }
}
