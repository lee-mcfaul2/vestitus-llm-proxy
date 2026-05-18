package dev.vestitus.trust.verify.sigstore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.vestitus.trust.Bundle;
import dev.vestitus.trust.BundleVerifier;
import dev.vestitus.trust.BundleVersion;
import dev.vestitus.trust.VerificationConfig;
import dev.vestitus.trust.VerificationOutcome;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The ADR-003 D3 DEFAULT {@link BundleVerifier}: shells the pinned,
 * checksum-verified {@code cosign} + {@code gh} (absolute in-image paths,
 * passed as argv[0] via the injectable {@link CommandRunner} seam) to verify
 * the sigstore signature (cert-identity-regexp == {@code config}, OIDC issuer
 * == {@code config}) AND the SLSA build-provenance attestation over the
 * fetched payload, then reads the monotone version from the NOW-authenticated
 * payload (ADR-003 D7 locked sub-decision: the top-level integer {@code
 * "version"} field of the cosign+SLSA-authenticated bytes; the full structure
 * is the Plan-05c digester's job, not here).
 *
 * <p>This is a REPLACEABLE default — there is NO mandatory crypto floor
 * (ADR-003 D3): airgap/homebrew operators ship an alternative
 * {@link BundleVerifier}. A weak custom verifier downgrades authenticity for
 * that operator's environment ONLY; the load-bearing no-rollback (D7),
 * minimum structural gate (D5), set-atomic (D8) and fail-closed (D9)
 * invariants live in the CORE downstream, independent of this seam (ADR-003
 * D6).</p>
 *
 * <p><b>Fail-closed (the {@link BundleVerifier} contract):</b> any non-zero
 * exec, parse failure, missing/invalid version, null input, or {@link
 * Throwable} ⇒ {@link VerificationOutcome.Rejected}; this impl NEVER throws.
 * {@code subjectId} is the verified operator-pinned publisher identity
 * (decision #3 — the one-trusted-org-security-team-publisher model; per-MCP
 * signer-SAN extraction is a NAMED deferred scoping, not a coverage gap).
 * The §4 ② semantically-overbroad-but-structurally-valid-ruleset residual is
 * the CORE structural gate's (Plan 05f / deleted cvc5) boundary, NOT this
 * verifier's. The real cosign/gh path is exercised CI-only against a
 * self-produced fully sigstore+SLSA-signed test bundle; local tests use a
 * fake {@link CommandRunner} (the offline-deterministic-reactor invariant).</p>
 */
public final class SigstoreSlsaBundleVerifier implements BundleVerifier {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final CommandRunner runner;
    private final String cosignPath;
    private final String ghPath;

    public SigstoreSlsaBundleVerifier(
            CommandRunner runner, String cosignPath, String ghPath) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.cosignPath = Objects.requireNonNull(cosignPath, "cosignPath");
        this.ghPath = Objects.requireNonNull(ghPath, "ghPath");
        if (cosignPath.isBlank())
            throw new IllegalArgumentException("cosignPath must be non-blank");
        if (ghPath.isBlank())
            throw new IllegalArgumentException("ghPath must be non-blank");
    }

    @Override
    public VerificationOutcome verify(Bundle bundle, VerificationConfig config) {
        try {
            if (bundle == null || config == null)
                return VerificationOutcome.rejected(
                    "null bundle/config (fail-closed)");
            String owner = config.extra().get("gh.owner");
            if (owner == null || owner.isBlank())
                return VerificationOutcome.rejected(
                    "missing gh.owner in verification config (fail-closed)");

            byte[] payload = bundle.payload();
            Path tmp = Files.createTempDirectory("vestitus-sigstore-");
            try {
                Path blob = tmp.resolve("blob");
                Path sig = tmp.resolve("blob.sigstore");
                Files.write(blob, payload);
                Files.write(sig, bundle.signatureMaterial());
                String blobPath = blob.toAbsolutePath().toString();
                String sigPath = sig.toAbsolutePath().toString();

                CommandRunner.Exec c = runner.run(List.of(
                    cosignPath, "verify-blob", "--new-bundle-format",
                    "--bundle", sigPath,
                    "--certificate-identity-regexp",
                    config.expectedIdentityRegexp(),
                    "--certificate-oidc-issuer", config.oidcIssuer(),
                    blobPath));
                if (c.exitCode() != 0)
                    return VerificationOutcome.rejected(
                        "cosign verify-blob failed (exit " + c.exitCode()
                        + "): " + trunc(c.stderr()));

                CommandRunner.Exec g = runner.run(List.of(
                    ghPath, "attestation", "verify", blobPath,
                    "--owner", owner));
                if (g.exitCode() != 0)
                    return VerificationOutcome.rejected(
                        "gh attestation verify failed (exit " + g.exitCode()
                        + "): " + trunc(g.stderr()));

                JsonNode root;
                try {
                    root = MAPPER.readTree(payload);
                } catch (IOException je) {
                    root = null;
                }
                JsonNode v = root == null ? null : root.get("version");
                if (v == null || !v.canConvertToLong() || v.asLong() < 0)
                    return VerificationOutcome.rejected(
                        "authenticated bundle missing a valid non-negative "
                        + "top-level integer \"version\" (fail-closed)");
                BundleVersion version = new BundleVersion(v.asLong());

                return VerificationOutcome.verified(
                    payload, config.expectedIdentityRegexp(), version);
            } finally {
                deleteRecursivelyBestEffort(tmp);
            }
        } catch (Throwable t) {
            return VerificationOutcome.rejected(
                "sigstore verify error (fail-closed): " + t);
        }
    }

    private static String trunc(String s) {
        if (s == null) return "";
        return s.length() <= 200 ? s : s.substring(0, 200);
    }

    private static void deleteRecursivelyBestEffort(Path dir) {
        if (dir == null) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {
            // best-effort cleanup; never mask a verdict on a temp-dir issue.
        }
    }
}
