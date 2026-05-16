# wire-schema

Canonical, single-source wire contract for vestitus-llm-proxy.

- Sealed `WireMessage` hierarchy of records (`AgentRequest`, `ToolCall`,
  `ToolResult`, `ResponsePromptEnvelope`), JDK 25.
- `WireJson` — strict, fail-closed JSON (de)serialization with a `kind`
  discriminator. Unknown/missing/garbage input ⇒ `WireParseException`.
- `SchemaVersion` — wire schema version identifier (`CURRENT`).
- `CanonicalSchema` — the single enumerated source of root types; a
  conformance test asserts it never drifts from the sealed `permits` clause.
- `SchemaEmitter` — SPI seam; Phase 6 plugs OpenAPI / signed-JSON-Schema
  emitters in here. No other module may redefine the wire types.

Build: `mvn -pl wire-schema -am test`. Dependency-free except Jackson;
the foundation every other vestitus module builds on.
