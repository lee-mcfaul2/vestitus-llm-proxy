package dev.vestitus.inspect.llmguard;

/**
 * Pluggable interface for analyzing a body through one or more named
 * llm-guard scanners. The shipped implementation is {@link HttpLlmGuardClient}.
 * The interface seam lets {@code LlmGuardSemanticDetector} be unit-tested
 * against an in-memory fake; production code uses the HTTP impl.
 *
 * <p>An implementation MUST NOT throw — any failure is an
 * {@link AnalyzeOutcome.Failed}.
 */
public interface LlmGuardScannerApi {

    /**
     * Posts {@code body} to the configured llm-guard-api endpoint, requesting
     * an analysis for the named scanner, and returns the per-scanner score
     * map (or a failure). The caller (typically {@code
     * LlmGuardSemanticDetector}) picks its own scanner's score from the map.
     *
     * @param scannerName the llm-guard scanner to run (e.g. {@code
     *                    "PromptInjection"}, {@code "Toxicity"})
     * @param body        the text to analyze (the {@link
     *                    dev.vestitus.inspect.NormalizedView}'s body)
     * @return the score map or a fail-closed outcome; never throws
     */
    AnalyzeOutcome analyze(String scannerName, String body);
}
