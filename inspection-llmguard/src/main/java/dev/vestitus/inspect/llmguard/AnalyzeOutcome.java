package dev.vestitus.inspect.llmguard;

import dev.vestitus.inspect.ReasonCode;

import java.util.Map;
import java.util.Objects;

/**
 * The outcome of {@link LlmGuardScannerApi#analyze}. Sealed, never thrown.
 *
 * <p><b>Inv. 13 (no secret/PII in failure detail).</b> {@code Failed.reason()}
 * carries ONLY a stable reason code (and, where useful, a short static
 * qualifier such as the HTTP status). It MUST NEVER carry request body text,
 * response body substrings, header values, or the matched value.
 */
public sealed interface AnalyzeOutcome {

    /** The per-scanner score map returned by {@code llm-guard-api}. */
    record Scores(Map<String, Double> byScanner) implements AnalyzeOutcome {
        public Scores {
            Objects.requireNonNull(byScanner, "byScanner");
            byScanner = Map.copyOf(byScanner);
        }
    }

    /** A fail-closed analysis failure carrying ONLY a stable reason code. */
    record Failed(ReasonCode reason) implements AnalyzeOutcome {
        public Failed {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
