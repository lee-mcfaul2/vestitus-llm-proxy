# inspection-llmguard

The shipped HTTP `SemanticDetector` adapter for Protect AI `llm-guard-api`
(design spec 2026-05-22, §10). One configured llm-guard scanner becomes one
`LlmGuardSemanticDetector` = one HTTP POST per `inspect(NormalizedView)`. The
detector emits `Verdict(action)` at or above a configured threshold, `Clean`
below, `StageFailed` on any analyze failure or missing score.

## Surface

- **`LlmGuardScannerApi`** — pluggable interface; `analyze(scannerName, body)`
  returns `AnalyzeOutcome.Scores | Failed`.
- **`HttpLlmGuardClient`** — shipped HTTP implementation against the pinned
  `llm-guard-api` v0.1.x analyze contract (`POST <endpoint>` with
  `{"prompt": <body>, "scanners": ["<name>"]}`; reads `scanner-scores[<name>]`
  as a double on 2xx; non-2xx and transport errors fail closed). Own
  pinned-mTLS `SSLContext` (no mesh trust), bounded retries on
  transport-class errors only within a hard latency budget, strict
  fail-closed Jackson parsing, never throws.
- **`LlmGuardEndpointConfig`** — eagerly-validated HTTPS/mTLS/timeouts/retry
  config; construction fails fast on invalid input.
- **`LlmGuardSemanticDetector`** — wraps one scanner; consumes a
  `LlmGuardDetectorConfig` (id + scanner name + threshold + trigger action +
  trigger reason code) and an `LlmGuardScannerApi`.
- **`AnalyzeOutcome`** — sealed two-arm value type for the API contract.

## What llm-guard CANNOT do here

`llm-guard-api` returns scores, not offsets. A llm-guard-backed detector
therefore **cannot produce a `SpanFinding`** and **cannot satisfy the
structural floor** — that is `inspection-reference`'s job (design spec
§1.1-2). llm-guard backs `SemanticDetector`s only.

## Inv. 13 — no secret/PII in failure detail

Every `AnalyzeOutcome.Failed` and `SemanticOutcome.StageFailed` carries only a
stable reason code (`llmguard.unreachable`, `llmguard.timeout`,
`llmguard.malformed`, `llmguard.terminal_status_<N>`,
`llmguard.score_missing`, `llmguard.bad_request`,
`llmguard.detector_threw`). Never request body text, response body, header
value, or any matched value. Asserted by
`HttpLlmGuardClientFailClosedTest.failureReasonNeverContainsRequestOrResponseBodyText`.

## Pinning

`HttpLlmGuardClient` builds its own `SSLContext` from
`LlmGuardEndpointConfig.pinnedServerTrust` — a keystore containing **only**
the pinned llm-guard-api server certificate, no system CAs.
`HttpLlmGuardClientPinningTest` proves a rogue server cert is rejected.

## Deferred (design-spec §10)

N scanners ⇒ N POSTs per pipeline run. Batching multiple scanners into one
call needs a shared per-run scope the SPI deliberately does not have; future
work.

Build: `mvn -pl inspection-llmguard -am test`. Depends on `inspection-spi` and
Jackson; JUnit (test) only. Offline tests use a JDK `HttpsServer` harness.
