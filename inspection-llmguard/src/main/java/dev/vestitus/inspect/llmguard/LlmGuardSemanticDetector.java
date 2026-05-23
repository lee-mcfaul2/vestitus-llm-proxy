package dev.vestitus.inspect.llmguard;

import dev.vestitus.inspect.NormalizedView;
import dev.vestitus.inspect.ReasonCode;
import dev.vestitus.inspect.SemanticAction;
import dev.vestitus.inspect.SemanticDetector;
import dev.vestitus.inspect.SemanticOutcome;
import dev.vestitus.inspect.SemanticVerdict;
import dev.vestitus.inspect.StageId;

import java.util.Objects;
import java.util.Set;

/**
 * A {@link SemanticDetector} wrapping exactly ONE {@code llm-guard-api}
 * scanner: one configured detector = one HTTP POST per {@code inspect}
 * (design spec §10). Returns {@link SemanticOutcome.Verdict} with the
 * configured action when the scanner's score is at or above the configured
 * threshold; otherwise {@link SemanticOutcome.Clean}; any analyze failure or
 * missing score becomes {@link SemanticOutcome.StageFailed} carrying a stable
 * {@link ReasonCode}. NEVER throws — the {@code inspect} body is wrapped
 * defensively per the SPI contract.
 */
public final class LlmGuardSemanticDetector implements SemanticDetector {

    private final LlmGuardDetectorConfig cfg;
    private final LlmGuardScannerApi api;
    private final Set<SemanticAction> declared;

    public LlmGuardSemanticDetector(LlmGuardDetectorConfig cfg,
                                    LlmGuardScannerApi api) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        this.api = Objects.requireNonNull(api, "api");
        this.declared = Set.of(cfg.action());
    }

    @Override
    public StageId id() { return cfg.id(); }

    @Override
    public Set<SemanticAction> declaredActions() { return declared; }

    @Override
    public SemanticOutcome inspect(NormalizedView view) {
        try {
            AnalyzeOutcome r = api.analyze(cfg.scannerName(), view.body());
            return switch (r) {
                case AnalyzeOutcome.Failed f ->
                    new SemanticOutcome.StageFailed(f.reason());
                case AnalyzeOutcome.Scores s -> {
                    Double score = s.byScanner().get(cfg.scannerName());
                    if (score == null)
                        yield new SemanticOutcome.StageFailed(
                            new ReasonCode("llmguard.score_missing"));
                    yield score >= cfg.threshold()
                        ? new SemanticOutcome.Verdict(new SemanticVerdict(
                            cfg.id(), cfg.triggerReason(), cfg.action()))
                        : new SemanticOutcome.Clean();
                }
            };
        } catch (Throwable t) {
            return new SemanticOutcome.StageFailed(
                new ReasonCode("llmguard.detector_threw"));
        }
    }
}
