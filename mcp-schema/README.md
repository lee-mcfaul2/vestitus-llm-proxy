# mcp-schema

Canonical, single-source type set for the declared **mcp-schema** artifact
(spec §5.3 step 1 + §5.4) — the document an MCP build produces and signs,
distinct from the ingress wire contract (`wire-schema`, ①). This is the
declared side (②); this module does not depend on `wire-schema`.

- `McpSchema` — the single aggregate root record (NOT a sealed/discriminated
  family): version anchor + `mcpId` + non-empty tool catalog + the MCP's
  Cedar ruleset + the per-MCP Cedar schema text.
- `ToolDecl` / `FieldDecl` — tool catalog and per-field declarations.
  `FieldDecl` is the **default-deny linchpin** (spec §5.3 step 2, non-Cedar
  half): a field missing its PII *or* IAM annotation is rejected at
  construction; an omitted annotation is rejection, never an implicit default.
- `PiiType` — controlled PII vocabulary (`NONE`, `DIRECT_IDENTIFIER`,
  `QUASI_IDENTIFIER`, `SENSITIVE`); a drift test pins the constant set.
  `NONE` = explicitly classified not-PII, still a stated classification.
- `IamBinding` — typed holder for the required external-IAM entitlement
  (opaque here; the IAM resolver is Plan 04e).
- `CedarRuleset` / `CedarSchemaText` — opaque-but-non-blank text holders;
  semantic `cedar validate` is the Plan 04b gate (ADR-002). This module has
  **no native / Cedar dependency** by design.
- `McpSchemaVersion` — this artifact's own version line (`CURRENT`),
  independent of `wire-schema`'s `SchemaVersion`.
- `McpSchemaJson` — strict, fail-closed JSON (de)serialization. Unknown /
  trailing / garbage / annotation-incomplete input ⇒ `McpSchemaParseException`.

Build: `mvn -pl mcp-schema -am test`. Dependency-free except Jackson;
mirrors `wire-schema`'s discipline. Consumed by Plans 04b/04d/04e.
