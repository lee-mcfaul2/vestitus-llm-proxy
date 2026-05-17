# cedar-cabi

vestitus-llm-proxy's self-built shim over a **commit-pinned** `cedar-policy`
(per ADR-001: never the upstream `com.cedarpolicy:cedar-java` jar; source-pin by
commit; build + attest ourselves; embed via the Java FFM API + this shim).

**This is Plan 03a (foundation) only.** Scope here: a pinned, checksum-verified
Rust toolchain; the recorded Cedar pin (`CEDAR_PIN`); a crate that builds
`cargo build --locked` against that pinned Cedar; and the captured pinned API
surface (`CEDAR_API_NOTES.md`). The crate currently contains a probe + Rust
round-trip tests only — **no C ABI yet**.

Coming in later sub-plans: the `extern "C"` runtime shim (03b), the hermetic
reproducible + cosign-keyless/SLSA-attested native build (03c), the jextract/FFM
Java bindings + the Cedar-backed `Authorizer` impl plugging the `authorizer-spi`
SPI (03d), and the CI Cedar-CLI static-analysis registration gate (03e).

- Pin & selection rule: see `CEDAR_PIN`.
- Pinned API the shim will wrap: see `CEDAR_API_NOTES.md`.
- Build/test: `cargo test --locked` (requires the pinned Rust toolchain).
- Not a Maven module — built by cargo; wired into the JVM build in 03c/03d.
