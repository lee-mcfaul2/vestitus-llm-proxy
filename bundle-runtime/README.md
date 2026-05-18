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
