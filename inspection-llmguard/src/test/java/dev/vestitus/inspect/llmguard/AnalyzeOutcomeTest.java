package dev.vestitus.inspect.llmguard;

import dev.vestitus.inspect.ReasonCode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnalyzeOutcomeTest {

    @Test
    void scoresCopiesAndExposesTheMap() {
        Map<String, Double> mutable = new HashMap<>();
        mutable.put("PromptInjection", 0.95);
        AnalyzeOutcome.Scores s = new AnalyzeOutcome.Scores(mutable);
        mutable.clear();
        assertEquals(1, s.byScanner().size());
        assertEquals(0.95, s.byScanner().get("PromptInjection"));
    }

    @Test
    void failedCarriesItsReasonCodeAndRejectsNull() {
        AnalyzeOutcome.Failed f = new AnalyzeOutcome.Failed(
            new ReasonCode("llmguard.unreachable"));
        assertEquals("llmguard.unreachable", f.reason().code());
        assertThrows(NullPointerException.class,
            () -> new AnalyzeOutcome.Failed(null));
        assertThrows(NullPointerException.class,
            () -> new AnalyzeOutcome.Scores(null));
    }

    @Test
    void bothVariantsAreExhaustivelyMatchable() {
        AnalyzeOutcome ok = new AnalyzeOutcome.Scores(Map.of("X", 0.1));
        AnalyzeOutcome bad = new AnalyzeOutcome.Failed(new ReasonCode("x.y"));
        assertEquals("scores", kind(ok));
        assertEquals("failed", kind(bad));
    }

    private static String kind(AnalyzeOutcome o) {
        return switch (o) {
            case AnalyzeOutcome.Scores s -> "scores";
            case AnalyzeOutcome.Failed f -> "failed";
        };
    }
}
