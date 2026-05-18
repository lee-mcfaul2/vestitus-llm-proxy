# authorizer-spi

The pluggable authorization seam for vestitus-llm-proxy (Plan 02).

- `Authorizer` — Kafka-style SPI extension point: `authorize(AuthorizationRequest)
  -> AuthorizationDecision`. Enterprises implement it in their own repo and
  select it at build/config time. Contract: fail-closed.
- `AuthorizationDecision` — sealed `Allow` | `Deny(reason)` ADT.
- `Principal` / `ResourceRef` / `AuthorizationRequest` — validated, immutable
  value types (subject + scopes/attributes; the field being authorized + its
  tags; the full request + context).
- `DenyAllAuthorizer` — safe default reference impl (denies everything); the
  fail-closed baseline when no policy engine is bound.
- `McpAuthorizerRegistry` — per-MCP isolated cells, operator-bound. Unknown/
  unbound mcpId, null request, null decision, or any thrown exception => Deny.

Engine-agnostic by design: the Cedar policy engine is a *pluggable
implementation* delivered in Plan 03 (Cedar via the Java FFM API + a
self-built `cedar-cabi` shim, per ADR-001). This module has no Cedar/native
dependency. Build: `mvn -pl authorizer-spi -am test`.

## Set-atomic generation swap (ADR-003 D8 — Plan 05e)

- `RegistryEntry` — one bundle's validated `mcpId -> Authorizer` binding. The
  input unit for `McpAuthorizerRegistry.ofEntries` (a list, not a `Map`) so a
  duplicate `mcpId` is detectable before the entries collapse — a `Map` would
  silently last-wins, which ADR-003 D8 / red-team CRITICAL-2 flag as
  exploitable.
- `McpAuthorizerRegistry.ofEntries(List<RegistryEntry>)` — builds one
  immutable generation; **fail-closed rejects the whole generation on any
  duplicate `mcpId`** (never silent last-wins). An empty list yields an empty
  deny-all generation, NOT an error — the set-policy (is empty admissible) and
  build-all-N-or-none are the caller's per ADR-003 D6 (Plan 05h). Reuses
  `of(Map)` for per-cell validation + immutability; the existing `of`/
  `authorize` are unchanged (purely additive).
- `GenerationalRegistry` — the ADR-003 D8 atomic holder over immutable
  `McpAuthorizerRegistry` generations (`AtomicReference`-backed). Cold start
  denies all (empty deny-all generation, fail-closed per D9) until the first
  `install`. `currentGeneration()` is the per-request INGRESS SNAPSHOT: a
  request pins one immutable generation at ingress for its whole lifetime, so a
  concurrent `install` can never perturb an in-flight request (no generation
  straddle — red-team HIGH-4). `install` is the atomic publish; build-all-N-or-
  none / prior-generation-retained-on-failure is the caller's contract (D6,
  Plan 05h).

Per-generation immutability is preserved exactly as before: each
`McpAuthorizerRegistry` stays immutable; `GenerationalRegistry` only swaps an
`AtomicReference` between whole immutable generations. No new dependency
(`AtomicReference`/`List`/`LinkedHashMap` are JDK). No POM change.
