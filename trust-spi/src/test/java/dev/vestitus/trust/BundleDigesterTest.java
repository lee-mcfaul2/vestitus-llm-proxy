package dev.vestitus.trust;

import dev.vestitus.mcpschema.McpSchema;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BundleDigesterTest {
    private static final class EmptyDigester implements BundleDigester {
        @Override
        public List<McpSchema> digest(byte[] authenticatedPayload) {
            return List.of();
        }
    }

    private static final class FailClosedDigester implements BundleDigester {
        @Override
        public List<McpSchema> digest(byte[] authenticatedPayload) {
            throw new TrustException("malformed payload", null);
        }
    }

    @Test
    void seamCompilesAndReturnsList() {
        BundleDigester d = new EmptyDigester();
        assertTrue(d.digest(new byte[]{1, 2}).isEmpty());
    }

    @Test
    void failClosedDigesterThrowsTrustException() {
        BundleDigester d = new FailClosedDigester();
        var ex = assertThrows(TrustException.class, () -> d.digest(new byte[]{0}));
        assertEquals("malformed payload", ex.getMessage());
    }

    @Test
    void digesterIsAnOpenInterfaceNotSealed() {
        assertTrue(BundleDigester.class.isInterface());
        assertFalse(BundleDigester.class.isSealed());
    }
}
