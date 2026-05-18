package dev.vestitus.trust.digest;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildSmokeTest {
    @Test
    void toolchainIsJava25() {
        assertEquals(25, Runtime.version().feature());
    }
}
