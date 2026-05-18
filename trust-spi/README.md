# trust-spi

The two compile-time trust SPI seams for vestitus-llm-proxy's runtime
Cedar-bundle pull (ADR-003, Plan 05b). No runtime code-load: the SPI seam is
the only extension mechanism (ADR-003 D2).

- `BundleVerifier` — open SPI: `verify(Bundle, VerificationConfig)
  -> VerificationOutcome`. The default sigstore/SLSA impl is Plan 05g. NO
  mandatory crypto floor (airgap/homebrew allowed, ADR-003 D3). Contract:
  fail-closed; authenticate the version (D7) and subject; honour the
  config pinning; impl-specific identity hardening.
- `BundleDigester` — open SPI: `digest(byte[]) -> List<McpSchema>`. Runs
  ONLY on verified bytes; ADR-003 D4 hardening contract (bounded size/count,
  no XXE/SSRF/zip-slip/decompression-bomb/native-deser); immutable result.
  The default identity impl is Plan 05c.
- `VerificationOutcome` — sealed `Verified(authenticatedPayload, subjectId,
  version)` | `Rejected(reason)` control-flow ADT (mirrors
  `AuthorizationDecision`). `Verified` clones the payload in and out;
  transient verdict, not a value type.
- `Bundle` — the raw fetched unit (payload + signatureMaterial + sourceRef).
  Deliberately a `final class`, not a record (it holds `byte[]`; identity
  semantics, defensive clone in/out).
- `BundleVersion` — monotone, comparable, validated; the publisher stamps it
  INTO the verifier-authenticated content (ADR-003 D7), never a sidecar.
- `VerificationConfig` — expected-identity regexp (startup-validated: must be
  fully anchored `^...$`) + mandatory OIDC issuer + impl-specific `extra`
  (immutable). ADR-003 §4 ⑧.
- `TrustException` — fail-closed signal a digester/verifier raises on
  malformed/internal failure.

**The load-bearing invariants are NOT here.** No-rollback (D7), the minimum
structural gate (D5), set-atomic registry swap (D8) and the failure-mode
state machine (D9) live in the *core* (`bundle-runtime` / the `authorizer-spi`
swap — Plans 05d/05f/05e/05h), downstream of and independent from the
swappable verifier (ADR-003 D6). A weak custom verifier cannot disable them.

Depends only on `mcp-schema` (the digester target type). No Jackson — the
SPI is serialization-agnostic; each digester impl brings its own parser.
Build: `mvn -pl trust-spi -am test`. Mirrors `authorizer-spi`'s discipline.
