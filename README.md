# vestitus-llm-proxy

A standalone security and policy plane that sits between an untrusted LLM agent
runtime and the in-house tool/data services it wants to call.

Vestitus is a Policy Enforcement Point (PEP). It does not run models. It governs
what an agent is allowed to do once a model has decided to act: who the call is
on behalf of, which tools and fields that subject may touch, what data is allowed
to leave, and what gets recorded.

Java 25 / Maven, Apache-2.0. The runtime authorization and trust plane is built
and tested; the end-to-end server and the data-protection stages are on the way
(see `DESIGN.md`).

## Where it sits in an LLM workflow

```
  ┌──────────────┐        ┌───────────────┐        ┌──────────────────┐
  │  LLM agent   │  tool  │               │  tool  │  in-house MCP    │
  │  runtime     │ ─────▶ │   vestitus    │ ─────▶ │  tool / data     │
  │ (untrusted)  │  call  │   (PEP)       │  call  │  services        │
  └──────────────┘        └───────────────┘        └──────────────────┘
                                  ▲
                                  │ pull + verify signed policy bundles
                                  │ (set-atomic hot-reload)
                          ┌───────────────┐
                          │ bundle source │
                          └───────────────┘
```

The agent runtime never calls the MCP services directly. Every tool call is
mediated by vestitus, which:

1. Establishes the subject the call is made on behalf of (OIDC / mTLS,
   configuration-driven).
2. Authorizes the call against per-MCP policy. Each MCP service publishes a
   signed schema artifact (tool and field declarations plus a Cedar ruleset).
   The decision is default-deny and made on structured attributes, not free text.
3. Applies the data-protection stages on the request and the response
   (PII / codename tokenization via an external tokenizer, content inspection
   for credential leaks). *Upcoming.*
4. Records a tamper-evident audit entry and emits a best-effort, PII-free trace.

Vestitus does not trust the bundle source, the network, the agent, the MCP
services, or its own operator configuration. The only trusted element is the
signed vestitus artifact itself. Policy is pulled at runtime and verified before
it is ever applied.

## How policy reaches a running instance

A running instance pulls a list of policy bundles from one configured endpoint
and hot-reloads them as an atomic set — no container restart, no runtime code
loading. The reload is all-or-nothing and fail-closed:

```
  pull  ──▶  verify each bundle      (Sigstore keyless + SLSA provenance)
        ──▶  bind verified identity to its MCP target
        ──▶  digest to MCP schemas
        ──▶  no-rollback check       (signed monotone version, ratcheting floor)
        ──▶  structural gate         (reject identity-less permits, etc.)
        ──▶  compile each to a Cedar authorizer
        ──▶  install the whole set   (single atomic registry swap)
```

Any failure for any bundle installs nothing — the previous good policy is kept
until the configured last-good window expires, after which the instance
fail-closes to deny-all. The load-bearing checks (no-rollback, structural gate,
set-atomic install) live in the core, downstream of the replaceable verifier, so
a weaker verifier cannot switch them off.

Cedar runs in-process through the Java FFM API over a native shim that is
source-pinned, self-built, and self-attested — never the upstream prebuilt jar.

## Modules

| Module                    | Role                                                            |
|---------------------------|-----------------------------------------------------------------|
| `wire-schema`             | Canonical request/response message model, strict JSON.          |
| `mcp-schema`              | MCP tool/field declaration model (default-deny fields).         |
| `trust-spi`               | Bundle, version, and the verifier/digester contracts.           |
| `bundle-digester-default` | Fail-closed default bundle → schema digester.                   |
| `bundle-verifier-sigstore`| Default verifier: cosign keyless + SLSA attestation.            |
| `authorizer-spi`          | Engine-agnostic authorizer SPI and per-MCP registry.            |
| `authorizer-cedar`        | Cedar authorizer, in-process via FFM over the pinned shim.       |
| `bundle-runtime`          | The trust core: gates, version floor, reload orchestrator.      |
| `cedar-cabi`              | Rust C-ABI shim over pinned Cedar (built and attested, not a Maven module). |

## Build

Requires JDK 25 and Maven 3.9+. The attested Cedar native shim is vendored, so a
normal build does not need a Rust toolchain.

```
mvn -e test        # build and run the reactor test suite
```

## Status

The trust plane and runtime authorization path are implemented and tested. The
runnable gateway server, identity integration, tokenization, content inspection,
and the signed release surface are specified but not yet built. `DESIGN.md` is
the source of truth for what is done and what is planned.
