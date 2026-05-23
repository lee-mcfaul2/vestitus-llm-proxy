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

class RegexPiiDetectorTest {

    private static final RegexPiiDetector DET = new RegexPiiDetector();

    private static List<SpanFinding> findingsOf(String body) {
        RawSpanOutcome o = DET.inspect(new RawContent(body, ContentKind.TEXT));
        return ((RawSpanOutcome.Spans) o).findings();
    }

    private static Set<String> reasons(List<SpanFinding> fs) {
        return fs.stream().map(f -> f.reason().code()).collect(Collectors.toSet());
    }

    @Test
    void detectorAdvertisesItsId() {
        assertEquals(new StageId("inspection.pii-regex"), DET.id());
        assertEquals(new StageId("custom"),
            new RegexPiiDetector(new StageId("custom")).id());
        assertThrows(IllegalArgumentException.class,
            () -> new RegexPiiDetector(null));
    }

    @Test
    void everyFindingIsPiiKindAndHasACorrectOffset() {
        String body = "contact me at alice@example.com please";
        List<SpanFinding> fs = findingsOf(body);
        assertEquals(1, fs.size());
        SpanFinding f = fs.get(0);
        assertEquals(FindingKind.PII, f.kind());
        assertEquals(new ReasonCode("pii.email"), f.reason());
        assertEquals("alice@example.com",
            body.substring(f.where().start(), f.where().endExclusive()));
    }

    @Test
    void findsAValidUsSsn() {
        String body = "my ssn is 123-45-6789 thanks";
        List<SpanFinding> fs = findingsOf(body);
        assertEquals(1, fs.size());
        assertEquals(new ReasonCode("pii.us_ssn"), fs.get(0).reason());
        assertEquals("123-45-6789", body.substring(
            fs.get(0).where().start(), fs.get(0).where().endExclusive()));
    }

    @Test
    void rejectsAnInvalidSsnAreaNumber() {
        for (String bad : List.of("000-12-3456", "666-12-3456", "900-12-3456")) {
            List<SpanFinding> fs = findingsOf("pre " + bad + " post");
            assertTrue(fs.stream().noneMatch(f ->
                f.reason().equals(new ReasonCode("pii.us_ssn"))),
                "should not match invalid SSN area: " + bad);
        }
    }

    @Test
    void findsAParenthesizedAndHyphenatedNanpPhone() {
        for (String good : List.of("(555) 123-4567", "555-123-4567",
                                   "+1 555-123-4567", "1-555-123-4567")) {
            List<SpanFinding> fs = findingsOf("call " + good + " now");
            assertTrue(fs.stream().anyMatch(f ->
                f.reason().equals(new ReasonCode("pii.phone_na"))),
                "should match NANP phone: " + good);
        }
    }

    @Test
    void findsALuhnValidCardNumberAndRejectsLuhnInvalid() {
        List<SpanFinding> ok = findingsOf("card 4111-1111-1111-1111 thanks");
        assertEquals(1, (int) ok.stream().filter(f ->
            f.reason().equals(new ReasonCode("pii.card"))).count());

        List<SpanFinding> bad = findingsOf("card 4111-1111-1111-1112 thanks");
        assertEquals(0, (int) bad.stream().filter(f ->
            f.reason().equals(new ReasonCode("pii.card"))).count());
    }

    @Test
    void cleanTextProducesNoFindings() {
        String body = "The quick brown fox jumps over the lazy dog. "
            + "Project numbers like 12 or 1234 do not look like PII. "
            + "Versions: 2.17.0, 0.1.0-SNAPSHOT.";
        assertTrue(findingsOf(body).isEmpty(),
            "no PII should be found in clean prose");
    }

    @Test
    void multiplePatternsInOneBodyAreAllFound() {
        String body = "Email alice@example.com, phone (555) 123-4567, "
            + "ssn 123-45-6789, card 4111-1111-1111-1111.";
        Set<String> got = reasons(findingsOf(body));
        assertEquals(Set.of("pii.email", "pii.phone_na",
                            "pii.us_ssn", "pii.card"), got);
    }
}
