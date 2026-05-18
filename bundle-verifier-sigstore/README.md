# bundle-verifier-sigstore

The ADR-003 D3 **default `BundleVerifier`** for vestitus-llm-proxy's runtime
Cedar-bundle pull (Plan 05g). `SigstoreSlsaBundleVerifier` shells the pinned,
checksum-verified `cosign` + `gh` through the injectable `CommandRunner` seam
to verify the sigstore signature (cert-identity-regexp == config, OIDC issuer
== config) **and** the SLSA build-provenance attestation, then reads the
monotone version from the now-authenticated payload (ADR-003 D7). Implements
`dev.vestitus.trust.BundleVerifier`; sibling of `trust-spi`.

- `CommandRunner` — the fail-closed shell seam. Implementations never throw;
  a failed or blocked exec surfaces as a non-zero `Exec`, not an exception.
- `ProcessCommandRunner` — the real `ProcessBuilder` runner. Bounded 1 MiB
  capture (a hostile child cannot deadlock `waitFor` or exhaust memory), 60s
  timeout, never throws (a timeout / failure is a non-zero `Exec`).
- `SigstoreSlsaBundleVerifier` — fail-closed in every branch: any non-zero
  exec / parse failure / missing-or-invalid version / null input / `Throwable`
  ⇒ `Rejected`, never throws (the `BundleVerifier` contract). `subjectId` =
  the verified operator-pinned publisher identity; `version` = the top-level
  authenticated integer `"version"` (read ONLY after both cosign AND gh
  return success, so the bytes are authenticated — ADR-003 D7).

**NO mandatory crypto floor** (ADR-003 D3): airgap / homebrew operators ship
an alternative `BundleVerifier`. The load-bearing no-rollback (D7) / minimum
structural gate (D5) / set-atomic swap (D8) / fail-closed (D9) invariants live
in the *core* (`bundle-runtime` / the `authorizer-spi` swap — Plans
05d/05f/05e/05h), independent of this swappable seam (ADR-003 D6) — a weak
custom verifier cannot disable them.

The per-MCP signer-SAN extraction is a **named deferred scoping** (the
one-trusted-org-security-team-publisher model — `subjectId` is the
operator-pinned publisher identity). The §4 ② semantically-overbroad-but-
structurally-valid-ruleset case is the **core structural gate's** boundary
(Plan 05f / the deleted cvc5 follow-on), NOT this verifier's. Deliberate
boundaries, not gaps.

Depends on `trust-spi`; `jackson-databind` is root-managed and used only to
read the authenticated top-level `"version"`. No native dep, no surefire
argLine. Build: `mvn -pl bundle-verifier-sigstore -am test`.

**Offline / CI split.** The local `mvn test` reactor is offline +
deterministic: every verifier-logic test uses a fake `CommandRunner`;
`ProcessCommandRunner` is exercised only with a trivial local `sh`. The real
cosign/gh path is exercised **CI-only** by `SigstoreSlsaBundleVerifierItest`
(JUnit-gated by `VESTITUS_SIGSTORE_ITEST=1`, skipped locally) against a
self-produced cosign+SLSA-signed test bundle in
`.github/workflows/bundle-verifier-sigstore-itest.yml`;
`scripts/check_sigstore_itest_hardening.py` enforces the supply-chain
invariants over that workflow (wired into `supply-chain-invariants.yml`).
