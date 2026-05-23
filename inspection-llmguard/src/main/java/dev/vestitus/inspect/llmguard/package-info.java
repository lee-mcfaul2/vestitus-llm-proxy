/**
 * The shipped HTTP {@link dev.vestitus.inspect.SemanticDetector} adapter for
 * Protect AI {@code llm-guard-api}. One configured llm-guard scanner = one
 * {@link dev.vestitus.inspect.llmguard.LlmGuardSemanticDetector} = one HTTP
 * POST per {@code inspect(NormalizedView)} (design-spec §10).
 *
 * <ul>
 *   <li>{@link dev.vestitus.inspect.llmguard.LlmGuardScannerApi} — the
 *       pluggable interface seam.</li>
 *   <li>{@link dev.vestitus.inspect.llmguard.HttpLlmGuardClient} — the shipped
 *       implementation against the pinned {@code llm-guard-api} v0.1.x
 *       analyze contract: own pinned-mTLS {@code SSLContext}, bounded retries
 *       on transport-class errors within a hard latency budget, strict
 *       fail-closed Jackson parsing, NEVER throws.</li>
 *   <li>{@link dev.vestitus.inspect.llmguard.LlmGuardSemanticDetector} — wraps
 *       one scanner; emits {@code Verdict(action)} at or above threshold,
 *       {@code Clean} below, {@code StageFailed} on any analyze failure or
 *       missing score.</li>
 * </ul>
 *
 * <p><b>What llm-guard CANNOT do here (design-spec §1.1-2).</b> {@code
 * llm-guard-api} returns scores, not offsets. A llm-guard-backed detector
 * therefore CANNOT produce a {@link dev.vestitus.inspect.SpanFinding} and
 * CANNOT satisfy the {@link dev.vestitus.inspect.InspectionPipeline}
 * structural floor. The floor is satisfied by {@code inspection-reference}.
 *
 * <p><b>Inv. 13 (no secret/PII in failure detail).</b> Every {@link
 * dev.vestitus.inspect.llmguard.AnalyzeOutcome.Failed} and {@link
 * dev.vestitus.inspect.SemanticOutcome.StageFailed} carries ONLY a stable
 * reason code (and, where useful, an HTTP-status qualifier). NEVER request
 * body text, response body substring, header value, or matched value.
 *
 * <p><b>Known cost, deferred optimization (design-spec §10).</b> N llm-guard
 * scanners ⇒ N HTTP POSTs per pipeline run. Batching multiple scanners into
 * one call needs a shared per-run scope the SPI deliberately does not have;
 * noted as future work.
 */
package dev.vestitus.inspect.llmguard;
