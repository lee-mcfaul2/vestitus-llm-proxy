package dev.vestitus.inspect.llmguard;

import dev.vestitus.inspect.ReasonCode;
import dev.vestitus.inspect.SemanticAction;
import dev.vestitus.inspect.StageId;

import java.util.Objects;

/**
 * Eagerly-validated per-detector config for {@link LlmGuardSemanticDetector}.
 * Construction-time check; the detector itself never throws.
 *
 * <ul>
 *   <li>{@code id} — the pipeline-unique stage id used in audit and metric tags.</li>
 *   <li>{@code scannerName} — the llm-guard-api scanner this detector consults
 *       (e.g. {@code "PromptInjection"}, {@code "Toxicity"}).</li>
 *   <li>{@code threshold} — the score (0.0..1.0 inclusive) at or above which
 *       the detector emits a Verdict.</li>
 *   <li>{@code action} — the action raised when the threshold trips
 *       (BLOCK or INCIDENT). The §7 assembly validator rejects FAIL_OPEN
 *       on a detector whose declared action is INCIDENT.</li>
 *   <li>{@code triggerReason} — the stable {@link ReasonCode} the Verdict
 *       carries when triggered (for audit/metric tagging in gateway-core).</li>
 * </ul>
 */
public record LlmGuardDetectorConfig(
        StageId id,
        String scannerName,
        double threshold,
        SemanticAction action,
        ReasonCode triggerReason) {

    public LlmGuardDetectorConfig {
        Objects.requireNonNull(id, "id");
        if (scannerName == null || scannerName.isBlank())
            throw new IllegalArgumentException("scannerName required");
        if (Double.isNaN(threshold) || threshold < 0.0 || threshold > 1.0)
            throw new IllegalArgumentException(
                "threshold must be within [0.0, 1.0]");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(triggerReason, "triggerReason");
    }
}
