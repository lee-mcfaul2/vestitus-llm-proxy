# bundle-digester-default

The ADR-003 D4 default **identity** `BundleDigester` for vestitus-llm-proxy's
runtime Cedar-bundle pull (Plan 05c). No runtime code-load: the SPI seam is the
only extension mechanism (ADR-003 D2).

- `IdentityBundleDigester` — `digest(byte[]) -> List<McpSchema>`. Expects the
  already-verified payload to be an `mcp-schema` JSON array; maps each element
  through `mcp-schema`'s strict `McpSchemaJson.read`. Returns an immutable
  `List.copyOf` result.
- **Post-verify only:** consumes ONLY already-verified bytes (the caller passes
  `VerificationOutcome.Verified.authenticatedPayload()`), never raw input.
  Verification proves provenance, not well-formedness — treated as an
  attacker-shaped-input parser.
- **D4 hardening posture (ADR-003 D4):** bounded raw byte size (checked before
  any parse — default 8 MiB), bounded top-level element count (default 1024),
  Jackson without default/polymorphic typing (no XXE/SSRF/deserialization-gadget
  surface — the shared mapper only splits the array; strict per-doc validation
  is `McpSchemaJson.read`'s job). No decompression / un-archiving, so
  zip-slip / decompression-bomb is structurally absent. Any failure =>
  fail-closed `TrustException`.

**Set policy is the core's responsibility, NOT the digester's
(ADR-003 D6/D1/D8).** An empty array yields an empty immutable list; empty-set /
authoritative-complete / duplicate-or-missing-`mcpId` policy, no-rollback (D7),
the structural gate (D5), set-atomic swap (D8) and the failure-mode state
machine (D9) live in the *core* (`bundle-runtime` / the `authorizer-spi` swap —
Plans 05d/05e/05f/05h), downstream of and independent from the swappable
digester. A weak custom digester cannot disable them. This module's single
responsibility is format translation + D4 hardening.

Depends on `trust-spi` (the SPI + `TrustException`) and `mcp-schema` (the
target type + the strict reader). No explicit Jackson dependency — it arrives
transitively via `mcp-schema`. Build: `mvn -pl bundle-digester-default -am
test`. Mirrors `trust-spi`'s discipline.
