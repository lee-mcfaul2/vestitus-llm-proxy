package dev.vestitus.trust.verify.sigstore;

import java.util.List;

/**
 * The shell-invocation seam (ADR-003 D3 default verifier). Decouples
 * {@link SigstoreSlsaBundleVerifier} from the real {@code cosign}/{@code gh}
 * processes so the verifier logic is deterministically unit-testable OFFLINE
 * (no network, no external binaries) with a fake runner — the
 * offline-deterministic-reactor invariant. The real runner is
 * {@link ProcessCommandRunner}; the real cosign/gh path is exercised CI-only.
 *
 * <p><b>Contract:</b> an implementation MUST NOT throw. A failed, blocked,
 * timed-out, or absent command MUST surface as a non-zero {@link Exec} (the
 * seam is fail-closed: the caller treats any non-zero exit as a rejection,
 * never as success-by-exception).</p>
 */
public interface CommandRunner {

    /** The fully-captured result of one process invocation. */
    record Exec(int exitCode, String stdout, String stderr) {}

    /**
     * Runs {@code argv} (argv[0] is the absolute binary path — no {@code
     * $PATH} reliance) and returns its captured {@link Exec}. MUST NOT throw.
     */
    Exec run(List<String> argv);
}
