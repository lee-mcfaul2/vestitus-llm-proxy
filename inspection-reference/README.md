# inspection-reference

Two pure-library `RawSpanDetector` implementations that satisfy the
`InspectionPipeline` structural floor (design spec 2026-05-22, §9). Together
they let any vestitus deployment construct a working `OutboundPipeline` /
`InboundPipeline` without bringing its own floor.

- **`RegexCredentialDetector`** — `FindingKind.CREDENTIAL`. High-confidence,
  low-false-positive credential shapes: PEM private-key blocks, AWS access-key
  IDs, GitHub tokens (`gh[opsur]_…`), Google API keys (`AIza…`), Slack tokens
  (`xox[abprs]-…`), JWT-shaped triplets (`eyJ…`). Each finding carries a stable
  `cred.*` `ReasonCode`.
- **`RegexPiiDetector`** — `FindingKind.PII`. Common PII shapes: email, US SSN
  with basic structural validity (rejects 000/666/9xx area, 00 middle, 0000
  last group), North-American phone (NANP, with optional country code and
  separators), Luhn-checked card numbers. Each finding carries its specific
  PII type in a stable `pii.*` `ReasonCode` (`pii.email`, `pii.us_ssn`,
  `pii.phone_na`, `pii.card`); `gateway-core` owns the `ReasonCode ->
  tokenizer PiiType` mapping (design-spec §9.1). This module **never couples
  to `tokenizer-client`**.

Both detectors are deterministic, perform no I/O, and wrap their `inspect`
body `try { ... } catch (Throwable t) { return StageFailed(...) }` per the
SPI's never-throw contract.

## Coverage candor (design-spec §9.2)

Pattern detectors **miss novel formats**: an AWS key without one of the
listed prefixes; a Stripe-style `sk_live_*` key; a homegrown bearer-string
shape; international SSNs; non-NANP phone formats; formatted-without-separator
card numbers that bypass the candidate regex. This is the named residual
risk. The mandatory floor (a pipeline without it does not compile, design-spec
Inv. 10) plus the gateway-server's fail-closed posture bound it. These
detectors are a credible default, **not a guarantee**. A deployment wanting
stronger coverage swaps in a better `RawSpanDetector` — the SPI seam admits
any conforming implementation.

The `RegexCredentialDetectorTest.documentedMissProvesTheCoverageCandor_S9_2`
test demonstrates this by feeding the detector a Stripe-style secret and
asserting empty findings.

Build: `mvn -pl inspection-reference -am test`. Depends only on
`inspection-spi`; JUnit (test) only.
