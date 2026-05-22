package dev.vestitus.inspect;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StageOutcomeTest {

    private static final StageId BY = new StageId("s");
    private static final ReasonCode RC = new ReasonCode("stage.failed");

    @Test
    void transformOutcomeIsSealedAndValidated() {
        NormalizedView v = NormalizedView.identityOf(
            new RawContent("x", ContentKind.TEXT));
        assertInstanceOf(TransformOutcome.class, new TransformOutcome.Normalized(v));
        assertInstanceOf(TransformOutcome.class, new TransformOutcome.StageFailed(RC));
        assertThrows(NullPointerException.class,
            () -> new TransformOutcome.Normalized(null));
        assertThrows(NullPointerException.class,
            () -> new TransformOutcome.StageFailed(null));
    }

    @Test
    void rawSpanOutcomeSpansCopiesItsFindingsList() {
        var mutable = new ArrayList<SpanFinding>();
        mutable.add(new SpanFinding(BY, new ReasonCode("pii.email"),
            new OriginalOffset(0, 3), FindingKind.PII));
        RawSpanOutcome.Spans spans = new RawSpanOutcome.Spans(mutable);
        mutable.clear();
        assertEquals(1, spans.findings().size());
    }

    @Test
    void semanticOutcomeHasThreeCases() {
        SemanticVerdict verdict = new SemanticVerdict(
            BY, new ReasonCode("x"), SemanticAction.BLOCK);
        assertEquals("verdict", name(new SemanticOutcome.Verdict(verdict)));
        assertEquals("clean", name(new SemanticOutcome.Clean()));
        assertEquals("failed", name(new SemanticOutcome.StageFailed(RC)));
    }

    private static String name(SemanticOutcome o) {
        return switch (o) {
            case SemanticOutcome.Verdict v -> "verdict";
            case SemanticOutcome.Clean c -> "clean";
            case SemanticOutcome.StageFailed f -> "failed";
        };
    }
}
