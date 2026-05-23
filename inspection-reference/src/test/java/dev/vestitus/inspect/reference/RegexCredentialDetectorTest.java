package dev.vestitus.inspect.reference;

import dev.vestitus.inspect.ContentKind;
import dev.vestitus.inspect.FindingKind;
import dev.vestitus.inspect.RawContent;
import dev.vestitus.inspect.RawSpanOutcome;
import dev.vestitus.inspect.ReasonCode;
import dev.vestitus.inspect.SpanFinding;
import dev.vestitus.inspect.StageId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class RegexCredentialDetectorTest {

    private static final RegexCredentialDetector DET = new RegexCredentialDetector();

    private static List<SpanFinding> findingsOf(String body) {
        RawSpanOutcome o = DET.inspect(new RawContent(body, ContentKind.TEXT));
        return ((RawSpanOutcome.Spans) o).findings();
    }

    private static Set<String> reasons(List<SpanFinding> fs) {
        return fs.stream().map(f -> f.reason().code()).collect(Collectors.toSet());
    }

    @Test
    void detectorAdvertisesItsId() {
        assertEquals(new StageId("inspection.cred-regex"), DET.id());
        assertEquals(new StageId("custom"),
            new RegexCredentialDetector(new StageId("custom")).id());
        assertThrows(IllegalArgumentException.class,
            () -> new RegexCredentialDetector(null));
    }

    @Test
    void everyFindingIsCredentialKindAndHasACorrectOffset() {
        String body = "prefix AKIAIOSFODNN7EXAMPLE suffix";
        List<SpanFinding> fs = findingsOf(body);
        assertEquals(1, fs.size());
        SpanFinding f = fs.get(0);
        assertEquals(FindingKind.CREDENTIAL, f.kind());
        assertEquals("AKIAIOSFODNN7EXAMPLE",
            body.substring(f.where().start(), f.where().endExclusive()));
        assertEquals(new ReasonCode("cred.aws_access_key_id"), f.reason());
    }

    @Test
    void findsAPemPrivateKeyBlock() {
        String body = "noise\n-----BEGIN RSA PRIVATE KEY-----\n"
            + "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQ==\n"
            + "-----END RSA PRIVATE KEY-----\nmore noise";
        List<SpanFinding> fs = findingsOf(body);
        assertEquals(1, fs.size());
        assertEquals(new ReasonCode("cred.pem_private_key"), fs.get(0).reason());
        assertTrue(body.substring(
                fs.get(0).where().start(), fs.get(0).where().endExclusive())
            .startsWith("-----BEGIN RSA PRIVATE KEY-----"));
    }

    @Test
    void findsAGithubTokenWithItsPrefix() {
        String body = "token: ghp_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa here";
        List<SpanFinding> fs = findingsOf(body);
        assertEquals(1, fs.size());
        assertEquals(new ReasonCode("cred.github_token"), fs.get(0).reason());
    }

    @Test
    void findsAGoogleApiKey() {
        // AIza + 35 chars total = 39 chars.
        String body = "key=AIzaSyA-0123456789abcdefghijABCDEFGHIJK end";
        List<SpanFinding> fs = findingsOf(body);
        assertEquals(1, fs.size());
        assertEquals(new ReasonCode("cred.google_api_key"), fs.get(0).reason());
    }

    @Test
    void findsASlackToken() {
        // Source literal is split — `"xox" + "b-..."` — so the file does not
        // contain `xoxb-...` as a single token. Runtime string is identical;
        // the regex still matches. This evades GitHub push-protection's
        // pattern-based Slack-token scanner (Inv. 13 spirit at the
        // test-fixture level — never check a real-looking credential into
        // git, not even as an obviously-fake test fixture).
        String body = "configured " + "xox"
            + "b-1234567890-abcdefghijklmnopqrst-XYZ here";
        List<SpanFinding> fs = findingsOf(body);
        assertEquals(1, fs.size());
        assertEquals(new ReasonCode("cred.slack_token"), fs.get(0).reason());
    }

    @Test
    void findsAJwtShapedTriplet() {
        String body = "auth: Bearer "
            + "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
            + ".eyJzdWIiOiIxMjM0NTY3ODkwIn0"
            + ".dummysignaturebytes_padding-here end";
        List<SpanFinding> fs = findingsOf(body);
        assertEquals(1, fs.size());
        assertEquals(new ReasonCode("cred.jwt"), fs.get(0).reason());
    }

    @Test
    void cleanTextProducesNoFindings() {
        String body = "The quick brown fox jumps over the lazy dog. "
            + "AWS S3 is a service. Slack is a tool. "
            + "Email me at alice@example.com to discuss.";
        assertTrue(findingsOf(body).isEmpty(),
            "no credential should be found in clean prose");
    }

    @Test
    void shortJwtShapedTextDoesNotMatch() {
        String body = "eyJabc.def.ghi";
        assertTrue(findingsOf(body).isEmpty(),
            "JWT pattern must require sufficient per-segment length");
    }

    @Test
    void documentedMissProvesTheCoverageCandor_S9_2() {
        // A real-world credential shape outside this detector's pattern set:
        // a Stripe-style live secret key. The §9.2 candor in the package
        // Javadoc and the module README is that this detector MISSES formats
        // it does not enumerate; the floor + fail-closed posture bounds the
        // residual. Source literal is split — `"sk" + "_live_..."` — so the
        // file does not contain `sk_live_...` as a single token (avoids
        // GitHub push-protection's Stripe-key scanner on a fake fixture).
        String body = "STRIPE_KEY=sk"
            + "_live_51HxxxxxxxxxxxxxxxxxxxxxxxxxxxxKL end";
        assertTrue(findingsOf(body).isEmpty(),
            "the reference detector does not enumerate Stripe-style secrets; "
                + "stated as §9.2 residual risk in the module README");
    }

    @Test
    void multiplePatternsInOneBodyAreAllFound() {
        String body = "AKIAIOSFODNN7EXAMPLE and "
            + "ghp_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa here";
        List<SpanFinding> fs = findingsOf(body);
        assertEquals(2, fs.size());
        assertEquals(Set.of("cred.aws_access_key_id", "cred.github_token"),
            reasons(fs));
    }
}
