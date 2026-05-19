# bundle-runtime

The ADR-003 D6 **core** for vestitus-llm-proxy's runtime Cedar-bundle pull
(Plan 05d — the first concrete piece; Plans 05f/05h add the structural gate and
the reload orchestrator to this module). No runtime code-load: the SPI seam is
the only extension mechanism (ADR-003 D2).

- `NoRollbackGate` — the ADR-003 D7 no-rollback version gate.
  `evaluate(BundleVersion) -> VersionDecision`. Accepts ONLY a version strictly
  newer than the live generation AND at/above the persisted operator floor
  (fail-forward); rejects `<= live` (replay) or `< floor` (cold-start rollback)
  fail-closed. Verifier-independent.
- `VersionDecision` — sealed `Accept(BundleVersion) | Reject(String reason)`
  ADT (mirrors `authorizer-spi`'s `AuthorizationDecision` discipline).
- `VersionFloorStore` — the ratcheting persisted-floor contract (a core
  persistence detail exposed as an interface only for the test-seam discipline
  + ops file-location config; NOT an ADR-003 extension SPI).
- `FileVersionFloorStore` — single-`long` floor persisted to a configured
  `Path`. Absent file == pristine; corrupt == fail-closed (throws
  `VersionStoreException`, never silently empty); crash-safe via
  temp-file + `ATOMIC_MOVE`; monotone (`Math.max`, never lowers).
- `VersionStoreException` — `bundle-runtime`-local fail-closed signal for a
  corrupt/unwritable floor.

**This is the core, NOT the verifier / fetch / digest / set-swap
(ADR-003 D6).** It consumes a `BundleVersion` the `BundleVerifier` *already
authenticated* (the version lives inside the signed/attested content, never an
unauthenticated sidecar — D7). It deliberately does NOT verify signatures
(05g `bundle-verifier-sigstore`), fetch bundles (05h), digest the payload
(05c `bundle-digester-default`), apply the minimum structural gate (05f), or
perform the set-atomic registry swap (05e). The no-rollback invariant is
verifier-independent core code precisely so a weak/sabotaged custom verifier
cannot disable rollback protection. This module's single responsibility is the
monotone-version verdict + the ratcheting persisted floor. A deliberate
boundary, not a gap.

Depends on `trust-spi` (for the already-authenticated `BundleVersion`). No
`mcp-schema` / Jackson dependency (this gate operates on a `long`-valued
version, not on JSON). Build: `mvn -pl bundle-runtime -am test`. Mirrors
`trust-spi` / `bundle-digester-default` discipline.

## Minimum structural gate (ADR-003 D5 — Plan 05f)

The salvaged, dual-reviewed, security-fix-looped structural lint (recovered
verbatim from the removed `registration-gate`, package re-homed only) plus a
new orchestrator, in the `dev.vestitus.bundle.gate` package:

- `PolicyScanner` — string/comment-aware Cedar-source scanning primitives
  (incl. `blankStringContents`, the security-fix-loop output that closes the
  string-literal-decoy self-permissive bypass).
- `IdentityPredicateLint` — rejects identity-less / self-permissive `permit`
  fail-closed (conservative TEXTUAL lint; any unknown/ambiguous/unbalanced
  shape rejects). A `when`/`unless` body textually referencing `principal`
  passes — the semantic tautology (`when { principal == principal }`) is the
  **named ADR-003 §4 boundary-2 residual** (the deleted cvc5/symbolic
  follow-on), intentionally NOT caught.
- `CrossMcpInjectivityCheck` — cross-MCP resource-identity injectivity
  (Inv. 11): set-unique `mcpId`, `/`/control-char-free + intra-scope-unique
  tool/field names so the `(mcpId, tool, field) -> Cedar UID` mapping is
  injective across the assembled image.
- `GateVerdict` — sealed `Pass | Reject(List<String> reasons)` ADT (mirrors
  `authorizer-spi`'s `AuthorizationDecision` discipline).
- `StructuralGate` — the ADR-003 D5 minimum gate over the already-digested
  `List<McpSchema>`. Runs in the D6 core, downstream of `BundleDigester`
  (Plan 05c) and BEFORE the Cedar compile (wired by Plan 05h). Per-schema
  `IdentityPredicateLint` + cross-MCP `CrossMcpInjectivityCheck.checkSet`,
  fail-closed (null set / any sub-check Reject / any Throwable => Reject;
  reasons unioned). An empty list => Pass — set-admissibility is the caller's
  per ADR-003 D6 (Plan 05h), not an error here.

**Deliberate D5 boundary, not gaps:** the "reject a field missing PII or IAM"
D5 clause is **already structurally enforced upstream** by `mcp-schema`
`FieldDecl`'s compact constructor (a digested `McpSchema` cannot carry a
PII/IAM-incomplete field), so it is not re-checked here. This gate is **NOT
cvc5/symbolic**; the ADR-002 native-`cedar_validate` ceremony
(`CedarValidateCheck`/`StaticAnalysisGate`) is deliberately NOT salvaged
(ADR-003 D5/D10). It does not verify (05g), fetch/compile/orchestrate (05h),
digest (05c), enforce no-rollback (05d), or set-atomically swap (05e).

Adds a `dev.vestitus:mcp-schema` compile dependency (the lint operates on the
digested `McpSchema` set). No native code, no surefire `argLine`.

## Reload orchestrator (Plan 05h)

The ADR-003 D6 runtime-pull capstone, in the `dev.vestitus.bundle.reload`
package — it wires the already-merged pieces (05c digest, 05d no-rollback,
05f structural gate, 05e generational registry, 05g verifier SPI) into one
fail-closed, ALL-N-or-NONE reload:

- `MonotonicClock` — the ONLY time source for the last-good window
  (`System.nanoTime`-backed `SYSTEM`; tests inject a fake). Wall-clock is
  never read, so a clock skew cannot widen or shrink the window.
- `BundleSource` / `FetchResult` — the fetch seam (sealed
  `Fetched | Unreachable`; never throws through). `HttpBundleSource` is the
  default: GET a JSON array of base64 `{payload,signatureMaterial,sourceRef}`,
  bounded body + request timeout, every failure mode mapped to `Unreachable`.
- `PolicyCompiler` (in `authorizer-spi`) / `CedarPolicyCompiler` (in
  `authorizer-cedar`) — compiles a vetted `McpSchema` to an `Authorizer`
  behind a deterministic pre-native size/complexity bound; a breach throws
  `PolicyCompileException` WITHOUT touching the engine.
- `ReloadObserver` / `NoOpReloadObserver` — the rate-aggregated reload-lifecycle
  audit, deliberately DECOUPLED from the per-request fail-closed authorize path
  (`GenerationalRegistry` is untouched).
- `ReloadConfig` — eagerly builds & retains a `VerificationConfig`, so an
  unanchored identity pin or blank OIDC issuer fails FAST at construction
  (startup pin validation), not at first reload.
- `BundleReloadOrchestrator` — fetch -> per-bundle verify -> digest ->
  aggregate -> single authenticated set version -> no-rollback gate ->
  structural gate -> per-schema compile -> `McpAuthorizerRegistry.ofEntries`
  -> atomic `GenerationalRegistry.install`. On ANY abort the prior generation
  stays live (last-good); beyond the window or cold-start unreachable =>
  fail-closed deny-all. A weak/sabotaged verifier cannot disable the
  no-rollback or structural invariants — they run downstream of verify in
  verifier-independent core code.

**Deliberate D6 boundary, not a gap:** 05h adds NO new Maven module (subpackage
of `bundle-runtime`), NO metrics library, NO runtime code-load (compile-time
SPI seams only), and NO CI for this plan — it is all-local. The single
real-native test is `CedarPolicyCompilerNativeTest` in `authorizer-cedar` (the
vendored `.so` is already how that module tests). This closes the ADR-003
runtime Cedar-bundle pull series.
