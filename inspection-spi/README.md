# inspection-spi

The per-request content-inspection contract for vestitus-llm-proxy (design spec
2026-05-22; spec v0.2 §5.7). `gateway-core` runs an inspection pipeline in two
places — **inbound** over untrusted user/agent content at ingress, **outbound**
over authorized-only MCP responses before they reach the LLM. This module is
the contract only: it emits findings and a sealed top-level decision; it does
not act on them, run in the request path, or emit audit/metrics.

Two structural guarantees of §5.7 are enforced by the Java type system, not by
developer discipline:

- **Inv. 9** — only a `RawSpanDetector`, which always inspects the original
  `RawContent`, can produce a `SpanFinding`. A `SemanticDetector` sees a
  possibly-`LOSSY` `NormalizedView` and cannot produce a span. "Detect on a
  translation, act on the original" is unrepresentable.
- **Inv. 10** — the security floor (mandatory credential detection; mandatory
  PII detection before tokenize/deliver) is a constructor argument of
  `InspectionPipeline`. A pipeline without the floor does not compile.

## Surface

- **Content model** — `RawContent` / `ContentKind`, `NormalizedView`,
  `OriginalOffset`, `SpanMap` (the transformer-declared back-mapping; its
  correctness is the transformer's promise, not statically verifiable — the
  §5.7.1 footgun).
- **Stages** — sealed `Stage` → `Transformer` | sealed `Detector` →
  `RawSpanDetector` | `SemanticDetector`. Stage implementations never throw;
  every failure is a sealed `StageFailed`.
- **Findings** — sealed `Finding` → `SpanFinding` (a `RawSpanDetector`'s
  offset-located finding) | `SemanticVerdict` (a `SemanticDetector`'s
  BLOCK/INCIDENT verdict, never an offset). `ReasonCode` is the stable audit
  key; a PII span carries its type in a `pii.*` `ReasonCode` (gateway-core owns
  the `ReasonCode -> tokenizer PiiType` mapping).
- **Pipeline** — `InspectionPipeline.outbound(...)` / `.inbound(...)` build a
  frozen `OutboundPipeline` / `InboundPipeline`. The outbound pipeline has a
  credential floor AND a PII floor; the inbound pipeline has the credential
  floor only (inbound PII is the user's own data). The assembly-time validator
  rejects a bad configuration at process start with `PipelineAssemblyException`.
- **Outcome** — sealed `PipelineOutcome` → `Allowed` | `Blocked` | `Incident` |
  `StageFailure`. No variant carries a matched secret or PII value (Inv. 13);
  `PipelineOutcomeNoLeakTest` asserts this reachability property by reflection.

No vestitus module dependency; JUnit (test) only — the executor is exercised
entirely by in-memory fake stages. The reference floor detectors
(`inspection-reference`) and the llm-guard semantic adapter
(`inspection-llmguard`) are separate provider modules.

Build: `mvn -pl inspection-spi test`. Mirrors `trust-spi`/`authorizer-spi`
discipline.
