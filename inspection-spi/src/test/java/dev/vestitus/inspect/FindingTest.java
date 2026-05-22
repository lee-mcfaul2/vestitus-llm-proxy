package dev.vestitus.inspect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FindingTest {

    private static final StageId BY = new StageId("det");
    private static final ReasonCode RC = new ReasonCode("pii.email");

    @Test
    void spanFindingRejectsNulls() {
        assertThrows(NullPointerException.class, () -> new SpanFinding(
            null, RC, new OriginalOffset(0, 1), FindingKind.PII));
        assertThrows(NullPointerException.class, () -> new SpanFinding(
            BY, RC, null, FindingKind.PII));
        assertThrows(NullPointerException.class, () -> new SpanFinding(
            BY, RC, new OriginalOffset(0, 1), null));
    }

    @Test
    void semanticVerdictRejectsNulls() {
        assertThrows(NullPointerException.class,
            () -> new SemanticVerdict(BY, RC, null));
    }

    @Test
    void bothAreFindingsAndExhaustivelyMatchable() {
        Finding span = new SpanFinding(
            BY, RC, new OriginalOffset(0, 5), FindingKind.PII);
        Finding verdict = new SemanticVerdict(
            BY, new ReasonCode("llmguard.prompt_injection"), SemanticAction.BLOCK);
        assertEquals("span", describe(span));
        assertEquals("verdict", describe(verdict));
    }

    private static String describe(Finding f) {
        return switch (f) {
            case SpanFinding s -> "span";
            case SemanticVerdict v -> "verdict";
        };
    }
}
