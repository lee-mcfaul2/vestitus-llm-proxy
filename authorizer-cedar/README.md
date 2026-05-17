# authorizer-cedar

Default Cedar-engine `Authorizer` for vestitus-llm-proxy (Plan 03, spec §5.4).

- `CedarAuthorizer implements dev.vestitus.authz.Authorizer` — evaluates a
  fixed per-cell Cedar policy text (constructor arg) against the authenticated
  principal/action/resource/context. No external call; the product's real authz.
- `CedarNative` — hand-written `java.lang.foreign` (FFM) binding over the
  `cedar-cabi` C ABI (3 functions). The attested `libcedar_cabi.so`
  (cedar-cabi-v0.1.0, sha256 422e3190…3a88c9) is **vendored** under
  `src/main/resources/native/` and rolled into the jar — in-process, no
  runtime code-load (ADR-001; spec §5.4 single signed artifact). It is
  extracted to a temp file at startup and `System.load`ed.
- **Fail-closed:** only a clean native `Allow` ⇒ `Allow`. Any null/parse/
  eval/panic/native `Error`/native `Deny`, or any Java exception in the
  binding path ⇒ `Deny(reason)` (spec §6 inv.7, §7 "engine error ⇒ deny").
- **Entity mapping (fixed):** principal.id→`User::"…"`, action→`Action::"…"`,
  resource→`Resource::"<mcpId>/<tool>/<field>"`; scopes/attributes/tags/
  request-context flattened into the Cedar context JSON object; entities `[]`.

Build: `mvn -pl authorizer-cedar -am test`.
