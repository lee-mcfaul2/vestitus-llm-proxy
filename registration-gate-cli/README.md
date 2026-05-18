# registration-gate-cli

The ADR-002 §4/§5 gate **artifact** — a thin module packaging the 04b
`StaticAnalysisGate` logic as a required transform that emits canonical stamped
output (the only thing the vestitus core loads). Sibling of `registration-gate`
+ `mcp-schema`.

- `CanonicalJson` — deterministic fixed-order set serialization.
- `GateStamp` — content-bound HMAC-SHA256 over the canonical bytes. The
  embedded key is an **intentional, comment-wrapped, non-credential-grade
  integrity key** (ADR-002 §5, NOT a leaked secret).
- `GateCli` — fail-closed required transform: PASS => stamped envelope on
  stdout, exit 0; REJECT => nothing on stdout, exit 1; any error => exit 2.
  Transform, not checkpoint.

Build: `mvn -pl registration-gate-cli -am package` (carries the
`--enable-native-access=ALL-UNNAMED` surefire argLine — transitive native load
via `registration-gate`->`authorizer-cedar`; `maven-shade-plugin` produces the
runnable jar; the root `project.build.outputTimestamp` makes it reproducible).

Supply chain: built and attested by the cedar-cabi-03c-pattern pipeline
(`registration-gate-cli-build-container.yml` +
`registration-gate-cli-release.yml`): 2-instance in-container byte-compare
reproducibility gate, SLSA L3 via `attest-build-provenance`, keyless cosign,
supply-chain invariants gate.

Consumer verification (on the published `registration-gate-cli-0.1.0-SNAPSHOT.jar`):

```
gh attestation verify registration-gate-cli-0.1.0-SNAPSHOT.jar --owner lee-mcfaul2
```

```
cosign verify-blob --new-bundle-format \
  --bundle registration-gate-cli-0.1.0-SNAPSHOT.jar.sigstore \
  --certificate-identity-regexp '.*registration-gate-cli-release.yml@refs/tags/registration-gate-cli-v.*' \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  registration-gate-cli-0.1.0-SNAPSHOT.jar
```

The cvc5 symbolic upgrade is the named, tracked ADR-002 §7 deferred follow-on
(inherited from 04b, not addressed here).
