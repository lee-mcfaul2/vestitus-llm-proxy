package dev.vestitus.inspect.llmguard;

import dev.vestitus.inspect.ContentKind;
import dev.vestitus.inspect.NormalizedView;
import dev.vestitus.inspect.RawContent;
import dev.vestitus.inspect.ReasonCode;
import dev.vestitus.inspect.SemanticAction;
import dev.vestitus.inspect.SemanticOutcome;
import dev.vestitus.inspect.StageId;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LlmGuardSemanticDetectorTest {

    private static final NormalizedView VIEW = NormalizedView.identityOf(
        new RawContent("hello world", ContentKind.TEXT));

    private static LlmGuardSemanticDetector detector(
            double threshold, SemanticAction action, LlmGuardScannerApi api) {
        return new LlmGuardSemanticDetector(new LlmGuardDetectorConfig(
            new StageId("d"), "PromptInjection", threshold, action,
            new ReasonCode("llmguard.prompt_injection")), api);
    }

    @Test
    void scoreBelowThresholdIsClean() {
        LlmGuardScannerApi api = (s, b) ->
            new AnalyzeOutcome.Scores(Map.of("PromptInjection", 0.1));
        SemanticOutcome o = detector(0.5, SemanticAction.BLOCK, api).inspect(VIEW);
        assertInstanceOf(SemanticOutcome.Clean.class, o);
    }

    @Test
    void scoreAtOrAboveThresholdYieldsAVerdictWithTheConfiguredAction() {
        LlmGuardScannerApi api = (s, b) ->
            new AnalyzeOutcome.Scores(Map.of("PromptInjection", 0.95));
        SemanticOutcome o = detector(0.5, SemanticAction.BLOCK, api).inspect(VIEW);
        SemanticOutcome.Verdict v = assertInstanceOf(
            SemanticOutcome.Verdict.class, o);
        assertEquals(SemanticAction.BLOCK, v.verdict().action());
        assertEquals(new ReasonCode("llmguard.prompt_injection"),
            v.verdict().reason());
    }

    @Test
    void incidentActionIsHonored() {
        LlmGuardScannerApi api = (s, b) ->
            new AnalyzeOutcome.Scores(Map.of("PromptInjection", 0.99));
        SemanticOutcome o = detector(0.5, SemanticAction.INCIDENT, api).inspect(VIEW);
        SemanticOutcome.Verdict v = (SemanticOutcome.Verdict) o;
        assertEquals(SemanticAction.INCIDENT, v.verdict().action());
    }

    @Test
    void analyzeFailureIsPassedThroughAsStageFailed() {
        LlmGuardScannerApi api = (s, b) ->
            new AnalyzeOutcome.Failed(new ReasonCode("llmguard.timeout"));
        SemanticOutcome o = detector(0.5, SemanticAction.BLOCK, api).inspect(VIEW);
        SemanticOutcome.StageFailed f = assertInstanceOf(
            SemanticOutcome.StageFailed.class, o);
        assertEquals(new ReasonCode("llmguard.timeout"), f.reason());
    }

    @Test
    void aResponseMissingThisScannerIsTreatedAsStageFailed() {
        LlmGuardScannerApi api = (s, b) ->
            new AnalyzeOutcome.Scores(Map.of("Toxicity", 0.1));
        SemanticOutcome o = detector(0.5, SemanticAction.BLOCK, api).inspect(VIEW);
        SemanticOutcome.StageFailed f = assertInstanceOf(
            SemanticOutcome.StageFailed.class, o);
        assertEquals(new ReasonCode("llmguard.score_missing"), f.reason());
    }

    @Test
    void detectorAdvertisesItsIdAndDeclaredActionSet() {
        LlmGuardSemanticDetector d = detector(0.5, SemanticAction.BLOCK,
            (s, b) -> new AnalyzeOutcome.Scores(Map.of()));
        assertEquals(new StageId("d"), d.id());
        assertEquals(Set.of(SemanticAction.BLOCK), d.declaredActions());
    }
}
