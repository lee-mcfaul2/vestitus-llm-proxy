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
