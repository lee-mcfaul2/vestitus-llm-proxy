# Pinned cedar-policy API surface (input for the 03b extern "C" shim)

Pin: tag `v4.10.0`, commit `c38e789101f0aec67651f976d29fc886009b3836` (see `CEDAR_PIN`).
Captured 2026-05-17 from the built pinned crate (`cargo doc -p cedar-policy` + crate source
at `~/.cargo/git/checkouts/cedar-4eb620986d042f8b/c38e789/cedar-policy/src/`).
ADR-001: the shim wraps RUNTIME entrypoints ONLY (authorization + registration-time
policy/schema validation). No analysis/CLI surface here (that is the Cedar CLI, 03e).

## Authorization (hot path)

### Authorizer

```rust
// cedar_policy::Authorizer
pub fn new() -> Self
pub fn is_authorized(&self, r: &Request, p: &PolicySet, e: &Entities) -> Response
```

`Authorizer::new()` is cheap: it stores only a compile-time `Extensions` constant and a
default `ErrorHandling` enum. There are no heap allocations that scale with policy or
entity data. It is safe to create one per call, but caching a single shared instance
(e.g., in a `static` or `Arc<Authorizer>`) is equally valid.

### Response / Decision / Diagnostics

```rust
// cedar_policy::Response  (Debug, PartialEq, Eq, Clone)
pub fn decision(&self) -> Decision
pub fn diagnostics(&self) -> &Diagnostics

// cedar_policy::Decision  (Debug, PartialEq, Eq, Clone, Copy — re-exported from cedar_policy_core)
pub enum Decision { Allow, Deny }

// cedar_policy::Diagnostics  (Debug, PartialEq, Eq, Clone)
pub fn reason(&self) -> impl Iterator<Item = &PolicyId>
    // PolicyIds of policies that contributed to the decision (empty if none applied)
pub fn errors(&self) -> impl Iterator<Item = &AuthorizationError>
    // Evaluation errors that occurred; treat as unordered

// cedar_policy::AuthorizationError  (Debug, Diagnostic, PartialEq, Eq, Error, Clone)
//   Single variant: PolicyEvaluationError(authorization_errors::PolicyEvaluationError)
//   Implements Display + std::error::Error (via thiserror #[derive(Error)])
//
// authorization_errors::PolicyEvaluationError
pub fn policy_id(&self) -> &PolicyId
pub fn inner(&self) -> &EvaluationError
```

To extract deny reasons across the C boundary:
1. `response.decision()` — returns `Decision::Allow` or `Decision::Deny`.
2. `response.diagnostics().reason()` — iterator of `&PolicyId`; call `.to_string()` on each.
3. `response.diagnostics().errors()` — iterator of `&AuthorizationError`; call `.to_string()` on each (Display is implemented).

### Request

```rust
// cedar_policy::Request
pub fn new(
    principal: EntityUid,
    action: EntityUid,
    resource: EntityUid,
    context: Context,
    schema: Option<&Schema>,
) -> Result<Self, RequestValidationError>
```

- All five arguments are required; no optional overload exists at this pin.
- `schema: None` skips request validation against a schema.
- `schema: Some(&s)` validates principal/action/resource types and context shape.
- Error type: `cedar_policy::RequestValidationError` — `#[derive(Debug, Diagnostic, Error)]`,
  implements `Display` + `std::error::Error`.

### Building EntityUid inputs (principal / action / resource)

Two constructors — choose based on what crosses the C boundary:

**Option A — parse Cedar UID syntax** (`Type::"id"` string, e.g. `User::"alice"`):
```rust
// cedar_policy::EntityUid
impl FromStr for EntityUid {
    type Err = ParseErrors;   // non-empty list of ParseError; Display + std::error::Error
    fn from_str(uid_str: &str) -> Result<Self, ParseErrors>
}
// Convenience: `let uid: EntityUid = r#"User::"alice""#.parse()?;`
// NOTE: requires normalized form (RFC-9). Do NOT construct by string concatenation.
```

**Option B — parse JSON escape form** (`{"__entity": {"type": "...", "id": "..."}}`):
```rust
pub fn from_json(json: serde_json::Value) -> Result<Self, entities_json_errors::JsonDeserializationError>
```

**Option C — build from components** (safest when type/id arrive as separate C strings):
```rust
pub fn from_type_name_and_id(name: EntityTypeName, id: EntityId) -> Self
// EntityTypeName: impl FromStr, Err = ParseErrors
// EntityId: impl FromStr, Err = Infallible  (all strings are valid EntityIds)
```

For the C ABI the recommended path is Option A (`EntityUid::from_str`) because it accepts
the standard Cedar UID string that callers already format, and the error is a single
`ParseErrors` that renders cleanly via `Display`.

### Context

```rust
// cedar_policy::Context
pub fn empty() -> Self

// From a JSON object string (must be a JSON object, not array/scalar):
pub fn from_json_str(
    json: &str,
    schema: Option<(&Schema, &EntityUid)>,   // action UID needed for schema-guided parse
) -> Result<Self, ContextJsonError>
// ContextJsonError: #[derive(Debug, Diagnostic, Error)], Display + std::error::Error

// From key-value pairs (when caller builds context fields individually):
pub fn from_pairs(
    pairs: impl IntoIterator<Item = (String, RestrictedExpression)>,
) -> Result<Self, ContextCreationError>
```

For the C ABI: pass a JSON object string and use `Context::from_json_str(json, None)`.
Schema-guided parsing (`Some((&schema, &action_uid))`) validates attribute types against
the schema; pass `None` to skip that validation (validation still happens in `Request::new`
if a schema is supplied there).

Expected JSON shape — a flat or nested JSON object:
```json
{ "key1": "value1", "key2": 42, "key3": true }
```
Entity references inside context use the `__entity` escape:
```json
{ "caller": { "__entity": { "type": "User", "id": "alice" } } }
```
Extension values use the `__extn` escape:
```json
{ "src_ip": { "__extn": { "fn": "ip", "arg": "10.0.1.1" } } }
```

### Entities

```rust
// cedar_policy::Entities
pub fn empty() -> Self

// From a JSON array string:
pub fn from_json_str(json: &str, schema: Option<&Schema>) -> Result<Self, EntitiesError>
// EntitiesError is re-exported from entities_errors::EntitiesError
// (cedar_policy_core::entities::err::EntitiesError)
// Implements Display + std::error::Error
```

Expected JSON shape — a JSON array of entity objects:
```json
[
  {
    "uid": { "type": "User", "id": "alice" },
    "attrs": {
      "age": 19,
      "ip_addr": { "__extn": { "fn": "ip", "arg": "10.0.1.101" } }
    },
    "parents": [{ "type": "Group", "id": "admin" }]
  },
  {
    "uid": { "type": "Group", "id": "admin" },
    "attrs": {},
    "parents": []
  }
]
```
Pass `schema: None` to skip schema-based conformance checking; `Some(&schema)` adds action
entities from the schema and validates attribute types/requiredness.

### PolicySet

```rust
// cedar_policy::PolicySet
impl FromStr for PolicySet {
    type Err = ParseErrors;   // Display + std::error::Error; iterate individual errors via .iter()
    fn from_str(policies: &str) -> Result<Self, ParseErrors>
}
```

Parses one or more Cedar policy statements. Policy IDs default to `policy0`, `policy1`, ...
when not specified. `PolicySet` is `Clone`; it is safe to parse once at registration time
and clone into a thread-local or `Arc` for concurrent authorization calls.

---

## Registration-time validation (runtime-callable)

### Schema constructors

```rust
// cedar_policy::Schema

// From Cedar human-readable schema syntax (.cedarschema):
impl FromStr for Schema {
    type Err = CedarSchemaError;   // Display + std::error::Error
    fn from_str(schema_src: &str) -> Result<Self, CedarSchemaError>
    // Delegates to from_cedarschema_str, discards warnings
}

// Same, with warnings surfaced:
pub fn from_cedarschema_str(
    src: &str,
) -> Result<(Self, impl Iterator<Item = SchemaWarning>), CedarSchemaError>

// From Cedar JSON schema format:
pub fn from_json_str(json: &str) -> Result<Self, SchemaError>
pub fn from_json_value(json: serde_json::Value) -> Result<Self, SchemaError>

// SchemaError: re-exported from cedar_policy_core::validator::schema_errors::SchemaError
//   #[derive(Debug, Diagnostic, Error)], Display + std::error::Error
// CedarSchemaError: #[derive(Debug, Diagnostic, Error)], Display + std::error::Error
//   Variants: Parse(CedarSchemaParseError), Io(IoError), Schema(SchemaError)
```

For the C ABI: accept either a Cedar schema string and call `Schema::from_str(src)` (Cedar
human syntax), or a JSON string and call `Schema::from_json_str(json)`.

### Policy / schema validation

```rust
// cedar_policy::Validator
pub fn new(schema: Schema) -> Self

pub fn validate(&self, pset: &PolicySet, mode: ValidationMode) -> ValidationResult
// ValidationMode: #[derive(Default)]; Default = ValidationMode::Strict
// Strict = no type errors AND restricted form amenable to analysis.

// cedar_policy::ValidationResult  (also implements Display + std::error::Error)
pub fn validation_passed(&self) -> bool
    // true if no errors (warnings are allowed); use as the primary pass/fail gate
pub fn validation_passed_without_warnings(&self) -> bool
    // true if neither errors nor warnings
pub fn validation_errors(&self) -> impl Iterator<Item = &ValidationError>
    // ValidationError: #[derive(Debug, Clone, Error, Diagnostic)], Display + std::error::Error
    // per-error: .policy_id() -> &PolicyId; Display gives a human-readable message
pub fn validation_warnings(&self) -> impl Iterator<Item = &ValidationWarning>
    // ValidationWarning: similar derive chain, Display + std::error::Error
```

Usage pattern at registration time:
```rust
let schema = Schema::from_str(schema_src)?;          // parse schema
let pset   = PolicySet::from_str(policy_src)?;       // parse policies
let result = Validator::new(schema).validate(&pset, ValidationMode::default());
if !result.validation_passed() {
    // collect messages: result.validation_errors().map(|e| e.to_string())
}
```

---

## FFI marshalling notes (for 03b)

### Which calls return Result; concrete error types; Display + std::error::Error

| Call | Error type | Display+Error |
|---|---|---|
| `EntityUid::from_str` | `ParseErrors` | yes (thiserror + manual impl) |
| `EntityUid::from_json` | `entities_json_errors::JsonDeserializationError` | yes |
| `Context::from_json_str` | `ContextJsonError` | yes |
| `Entities::from_json_str` | `entities_errors::EntitiesError` | yes |
| `PolicySet::from_str` | `ParseErrors` | yes |
| `Schema::from_str` | `CedarSchemaError` | yes |
| `Schema::from_json_str` | `SchemaError` | yes |
| `Request::new` | `RequestValidationError` | yes |
| `Authorizer::is_authorized` | — (infallible; returns `Response`) | n/a |
| `Validator::validate` | — (infallible; returns `ValidationResult`) | n/a |

All error types implement `Display` + `std::error::Error`. The canonical way to produce an
error string across the FFI boundary is `err.to_string()` (calls `Display`).

`ParseErrors` exposes `.iter()` to walk individual `ParseError` items (each also has
`Display`). For FFI, `format!("{}", parse_errors)` gives a summary of the first error;
iterating gives all of them.

### Send + Sync constraints (compile-verified at this pin)

`Authorizer`, `PolicySet`, `Entities`, `Request`, `Response`, `Schema`, and `Validator` are
all `Send + Sync + 'static`. This was verified by a compile-only test against the pinned
crate (see tests/send_sync.rs, removed after verification).

No `!Send`/`!Sync` types are involved in the authorization hot path. There are no lifetime
parameters on any of the structs above; they all own their data.

`Authorizer::is_authorized` takes `&self` (shared ref), so a single `Authorizer` can be
wrapped in `Arc<Authorizer>` and called concurrently from multiple threads.

### Construction cost / reuse guidance

- **`Authorizer`**: zero-cost to construct (two scalar fields). Can be created per-call or
  shared; no meaningful difference. Sharing via `Arc` is fine.
- **`PolicySet`**: non-trivial parse cost. Parse once at policy-registration time; store in
  `Arc<PolicySet>` (or equivalent) and reuse across authorization calls. Do not re-parse
  per-request.
- **`Schema`**: non-trivial parse cost. Parse once; store and reuse. Required at
  registration-time validation; optional at auth time (pass `None` to `Request::new` to
  skip per-call validation).
- **`Entities`**: rebuilt per request, since the entity set is request-scoped. Parsing from
  JSON is O(n entities). For hot paths with large entity sets, consider caching at a level
  above the shim.
- **`Request`**: per-call object; cheap to construct once `EntityUid`s and `Context` are
  built.
- **`Validator`**: wraps a `Schema`; construct once per schema and reuse.

### JSON shapes Cedar expects (concrete examples)

**Entities array** (passed to `Entities::from_json_str`):
```json
[
  {
    "uid":     { "type": "User", "id": "alice" },
    "attrs":   { "department": "engineering", "clearance": 3 },
    "parents": [{ "type": "Group", "id": "employees" }]
  }
]
```

**Context object** (passed to `Context::from_json_str`; must be a JSON _object_):
```json
{ "tls": true, "src_ip": { "__extn": { "fn": "ip", "arg": "192.0.2.1" } } }
```

**Entity UID string** (passed to `EntityUid::from_str`):
```
User::"alice"
Action::"ReadDocument"
Namespace::Type::"id-with-namespace"
```

**Cedar JSON schema fragment** (passed to `Schema::from_json_str`):
```json
{
  "": {
    "entityTypes": {
      "User":     { "shape": { "type": "Record", "attributes": {} } },
      "Resource": { "shape": { "type": "Record", "attributes": {} } }
    },
    "actions": {
      "Read": {
        "appliesTo": {
          "principalTypes": ["User"],
          "resourceTypes": ["Resource"],
          "context": { "type": "Record", "attributes": {} }
        }
      }
    }
  }
}
```
