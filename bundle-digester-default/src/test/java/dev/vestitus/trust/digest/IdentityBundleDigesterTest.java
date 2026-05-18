package dev.vestitus.trust.digest;

import dev.vestitus.mcpschema.McpSchema;
import dev.vestitus.trust.TrustException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdentityBundleDigesterTest {

    // Exact known-good mcp-schema doc shape (mcp-schema tests / recovered GateCli fixture).
    private static final String DOC = """
        {"schemaVersion":{"value":"1.0.0"},"mcpId":"crm-mcp","tools":[{"name":"findContact","description":"d","fields":[{"name":"email","pii":"DIRECT_IDENTIFIER","iam":{"entitlement":"crm:read"}}]}],"ruleset":{"text":"permit(principal == User::\\"a\\", action, resource);"},"cedarSchema":{"text":"entity User;"}}""";
    private static final String DOC2 = """
        {"schemaVersion":{"value":"1.0.0"},"mcpId":"billing-mcp","tools":[{"name":"findContact","description":"d","fields":[{"name":"email","pii":"DIRECT_IDENTIFIER","iam":{"entitlement":"crm:read"}}]}],"ruleset":{"text":"permit(principal == User::\\"a\\", action, resource);"},"cedarSchema":{"text":"entity User;"}}""";

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void singletonArrayYieldsOneSchema() {
        List<McpSchema> out = new IdentityBundleDigester().digest(utf8("[" + DOC + "]"));
        assertEquals(1, out.size());
        assertEquals("crm-mcp", out.get(0).mcpId());
    }

    @Test
    void multiArrayYieldsTwoDistinctSchemas() {
        List<McpSchema> out =
            new IdentityBundleDigester().digest(utf8("[" + DOC + "," + DOC2 + "]"));
        assertEquals(2, out.size());
        assertEquals("crm-mcp", out.get(0).mcpId());
        assertEquals("billing-mcp", out.get(1).mcpId());
    }

    @Test
    void packagePrivateCapCtorRejectsNonPositiveBounds() {
        assertThrows(IllegalArgumentException.class,
            () -> new IdentityBundleDigester(0, 1024));
        assertThrows(IllegalArgumentException.class,
            () -> new IdentityBundleDigester(8, 0));
        assertThrows(IllegalArgumentException.class,
            () -> new IdentityBundleDigester(-1, -1));
    }

    @Test
    void packagePrivateCapCtorAcceptsGenerousBounds() {
        // Generous bounds behave identically to the public default for good input.
        List<McpSchema> out =
            new IdentityBundleDigester(8_388_608, 1024).digest(utf8("[" + DOC + "]"));
        assertEquals(1, out.size());
        assertEquals("crm-mcp", out.get(0).mcpId());
    }

    @Test
    void bareObjectIsNotAnArrayAndFailsClosed() {
        TrustException ex = assertThrows(TrustException.class,
            () -> new IdentityBundleDigester().digest(utf8("{\"mcpId\":\"x\"}")));
        assertTrue(ex.getMessage().contains("must be a JSON array"));
    }

    @Test
    void jsonStringScalarIsNotAnArrayAndFailsClosed() {
        assertThrows(TrustException.class,
            () -> new IdentityBundleDigester().digest(utf8("\"x\"")));
    }

    @Test
    void jsonNumberScalarIsNotAnArrayAndFailsClosed() {
        assertThrows(TrustException.class,
            () -> new IdentityBundleDigester().digest(utf8("5")));
    }

    @Test
    void malformedJsonFailsClosed() {
        TrustException ex = assertThrows(TrustException.class,
            () -> new IdentityBundleDigester().digest(utf8("not json")));
        assertTrue(ex.getMessage().contains("fail-closed"));
    }

    @Test
    void nullPayloadFailsClosed() {
        TrustException ex = assertThrows(TrustException.class,
            () -> new IdentityBundleDigester().digest(null));
        assertTrue(ex.getMessage().contains("non-null"));
    }
}
