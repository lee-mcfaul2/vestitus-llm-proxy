package dev.vestitus.trust.verify.sigstore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The real {@link CommandRunner}: executes {@code argv} via
 * {@link ProcessBuilder} (argv[0] is an absolute binary path — no {@code
 * $PATH} reliance). stdout and stderr are captured FULLY but BOUNDED (each
 * capped at 1 MiB; the rest is drained and dropped — a hostile tool cannot
 * exhaust memory). A 60-second {@code waitFor} timeout
 * ({@code destroyForcibly()} + {@code Exec(124, partialOut, "timed out")} on
 * expiry). {@link IOException}/{@link InterruptedException} (the thread is
 * re-interrupted) become {@code Exec(127, "", "<class>: <msg>")} — this class
 * NEVER throws (the {@link CommandRunner} contract; fail-closed at the seam).
 */
public final class ProcessCommandRunner implements CommandRunner {

    private static final int CAP = 1 << 20;          // 1 MiB per stream
    private static final long TIMEOUT_SECONDS = 60L;

    @Override
    public Exec run(List<String> argv) {
        Process p = null;
        try {
            p = new ProcessBuilder(argv).redirectErrorStream(false).start();
            byte[] out = readCapped(p.getInputStream());
            byte[] err = readCapped(p.getErrorStream());
            if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return new Exec(124,
                    new String(out, StandardCharsets.UTF_8), "timed out");
            }
            return new Exec(p.exitValue(),
                new String(out, StandardCharsets.UTF_8),
                new String(err, StandardCharsets.UTF_8));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (p != null) p.destroyForcibly();
            return new Exec(127, "",
                ie.getClass().getName() + ": " + ie.getMessage());
        } catch (IOException | RuntimeException ex) {
            if (p != null) p.destroyForcibly();
            return new Exec(127, "",
                ex.getClass().getName() + ": " + ex.getMessage());
        }
    }

    private static byte[] readCapped(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) {
            if (buf.size() < CAP) {
                buf.write(chunk, 0, Math.min(n, CAP - buf.size()));
            }
            // else: keep draining so the child does not block on a full pipe,
            // but discard the excess (bounded capture, hostile-output defence).
        }
        return buf.toByteArray();
    }
}
