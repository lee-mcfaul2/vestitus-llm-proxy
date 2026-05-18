# registration-gate

The ADR-002 §7 blocking Cedar static-analysis gate **logic** over a declared
`mcp-schema` (set). The spec names no module for the gate (§5.1 silent); this
module names its §5.3-step-2 / §9 role. Sibling of `authorizer-cedar` +
`mcp-schema`.

- `GateVerdict` — sealed pass/reject verdict ADT (mirrors
  `dev.vestitus.authz.AuthorizationDecision`). Internal at this stage; the
  canonical stamped-output transform + HMAC stamp is Plan 04c.
- `CedarValidateCheck` — check 1: the ruleset must typecheck against its
  per-MCP Cedar schema, via the existing `CedarNative.validate` (raw code
  `2 = Valid`); runs against the real vendored `.so`.
- `PolicyScanner` — string/comment-aware Cedar source scanning primitives
  (Cedar has only `//` comments).
- `IdentityPredicateLint` — check 2, the security linchpin: a deterministic,
  conservative, **fail-closed** textual lint rejecting self-permissive /
  identity-less `permit` policies. The semantic tautology case
  (`when { principal == principal }`) is the **named, tracked ADR-002 §7
  deferred cvc5 follow-on**, intentionally not caught here.
- `CrossMcpInjectivityCheck` — check 3 / Inv. 11: `/`-free, control-free,
  set-unique `mcpId`s and intra-scope-unique tool/field names keep the
  `mcpId/tool/field` -> resource-UID mapping injective across the sealed image.
- `StaticAnalysisGate` — orchestrator: `vet(schema)` / `vetAll(set)`; Pass iff
  every check passes, else Reject with the union of reasons; fully fail-closed.

Build: `mvn -pl registration-gate -am test` (carries the
`--enable-native-access=ALL-UNNAMED` surefire argLine — transitive native load
via `authorizer-cedar`). Consumed by Plan 04c (the attested gate artifact).
