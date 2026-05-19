# Design

This is the living design document for vestitus-llm-proxy. It describes the
intent, the principles, and where the project is heading. It distinguishes what
is built from what is planned. Any change in direction is recorded here.

## Purpose

Vestitus is a security and policy plane (a Policy Enforcement Point) between an
untrusted LLM agent runtime and the in-house tool and data services it calls. It
decides, per call, whether a subject may invoke a tool and touch a field, what
data may cross the boundary, and what is recorded.

## Threat model

Vestitus assumes breach. The only trusted element is the signed vestitus
artifact. Everything around it is treated as hostile:

- the agent runtime and the prompts it carries,
- the MCP services and their responses,
- the identity provider,
- the external tokenizer,
- the network,
- the supply chain that produces policy and dependencies,
- the operator configuration itself.

Compromise of the root of trust used to sign the vestitus artifact is out of
scope. It is named, not hidden.

## Principles

- Default-deny. Every control fails closed, is logged, and is metered.
- Decisions are made on structured attributes, never on free text.
- Policy is pulled at runtime and verified before it is applied. Hot-reload is
  an atomic set swap — all bundles or none — with no container restart and no
  runtime code loading.
- The trust plane is pluggable through compile-time interfaces. The load-bearing
  invariants (no-rollback, structural gate, set-atomic install) live in the core,
  downstream of the replaceable verifier, so a weaker verifier cannot disable
  them.
- Cedar is consumed as a source-pinned, self-built, self-attested artifact and
  embedded in-process through the Java FFM API. Never the upstream prebuilt jar,
  never JNI, sidecar, or wasm.
- Audit is tamper-evident security-of-record: never sampled, fail-closed.
  Tracing is best-effort, context-isolated, and carries no PII. A tracing or
  collector outage must never cause a request to be denied.

## Status

### Built and tested

| Area                  | What exists                                                       |
|-----------------------|-------------------------------------------------------------------|
| Wire model            | Canonical sealed message types, strict fail-closed JSON, schema emitter seam. |
| MCP schema model      | Tool and field declarations with default-deny fields, strict JSON. |
| Authorization SPI     | Engine-agnostic authorizer interface, per-MCP registry, generational atomic registry, policy-compiler seam. |
| Cedar authorizer      | Cedar in-process via FFM over the pinned native shim; policy compiler with a pre-native size/complexity bound. |
| Cedar native shim     | Rust C-ABI shim over pinned Cedar; reproducible two-instance build, SLSA-attested, cosign keyless. |
| Trust SPI             | Bundle, monotone version, verifier and digester contracts.        |
| Default digester      | Fail-closed bundle → schema digester with size caps and no polymorphic typing. |
| Default verifier      | Sigstore keyless plus SLSA provenance verification, fail-closed.   |
| Trust runtime core    | No-rollback gate, persisted version floor, structural gate, and the reload orchestrator (pull, verify, bind, digest, gate, compile, set-atomic install; all-or-none; bounded retries; last-good window on a monotonic clock; fail-closed). |
| Tokenizer client      | Pluggable `Tokenizer` interface (begin/tokenize/detokenize/end session, sealed never-throw outcomes) plus a shipped HTTP implementation against the published pii-tokenizer contract: self-pinned mTLS, bounded retries within a hard latency budget, strict fail-closed parsing, no secret/PII in failure detail. |

The runtime authorization and trust plane is complete end to end as a library
and is exercised by the reactor test suite. The tokenizer client is built as a
standalone library; it is wired into the request path by the gateway server
(planned).

The tokenizer is a **pluggable interface**, deliberately. The spec originally
called for it to be non-pluggable on the reasoning that an in-process plugin
could let the "PII must never aggregate in one component" invariant be
configured away. That reasoning does not apply here: the load-bearing
invariants (sole authorized caller, entitlement filtering before tokenization,
PII removed before delivery) are enforced downstream in the gateway server, not
in this client. The interface is a wire-contract and transport seam for
testability and vendor independence; it cannot switch tokenization off, and the
shipped implementation plus deployment (self-pinned mTLS, separate isolated
service) preserve the original isolation intent.

### Planned, not yet built

| Area                  | Intent                                                            |
|-----------------------|-------------------------------------------------------------------|
| Content inspection    | A configurable pipeline of transformers and detectors with a mandatory, non-removable security floor; fail-closed. |
| Additional authorizers| AuthZEN adapter and a PKI example, alongside the Cedar default.    |
| Schema-artifact build | The MCP-side tooling that produces the signed schema artifact.    |
| Gateway server        | The runnable PEP process: identity (OIDC / mTLS), authorization, tokenization, inspection, and audit wired into the request path. |
| Release surface       | Each module jar individually signed and verified (SLSA, cosign keyless), plus a container image running the gateway server. |

## Direction

The next horizon is the request path: a runnable server that authenticates the
subject, authorizes each tool call through the existing trust plane, applies the
data-protection stages, and writes the audit record. The remaining
data-protection stage (content inspection) and the additional authorizer
adapters feed that server, alongside the already-built tokenizer client. The
release surface — signed module jars and a signed container image — follows once
the server has a stable shape.
