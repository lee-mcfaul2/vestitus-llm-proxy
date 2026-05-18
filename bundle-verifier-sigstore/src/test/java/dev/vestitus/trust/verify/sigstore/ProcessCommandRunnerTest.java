package dev.vestitus.trust.verify.sigstore;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProcessCommandRunnerTest {

    private final CommandRunner runner = new ProcessCommandRunner();

    @Test
    void capturesExitStdoutStderrFromTrivialLocalShell() {
        CommandRunner.Exec e = runner.run(
            List.of("sh", "-c", "printf out; printf err 1>&2; exit 3"));
        assertEquals(3, e.exitCode());
        assertEquals("out", e.stdout());
        assertEquals("err", e.stderr());
    }

    @Test
    void missingBinaryIsNonZeroAndDoesNotThrow() {
        CommandRunner.Exec e = runner.run(List.of("/no/such/binary"));
        assertNotEquals(0, e.exitCode());
        assertFalse(e.stderr().isBlank());
    }
}
