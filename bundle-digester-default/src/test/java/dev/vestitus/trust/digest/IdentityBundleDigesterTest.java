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

    @Test
    void docMissingRequiredPiiFieldFailsClosed() {
        // Same shape as DOC but the field omits the required "pii" annotation.
        String bad = """
            {"schemaVersion":{"value":"1.0.0"},"mcpId":"crm-mcp","tools":[{"name":"findContact","description":"d","fields":[{"name":"email","iam":{"entitlement":"x"}}]}],"ruleset":{"text":"permit(principal == User::\\"a\\", action, resource);"},"cedarSchema":{"text":"entity User;"}}""";
        TrustException ex = assertThrows(TrustException.class,
            () -> new IdentityBundleDigester().digest(utf8("[" + bad + "]")));
        assertTrue(ex.getMessage().contains("fail-closed"));
    }

    @Test
    void emptyArrayYieldsEmptyImmutableList() {
        List<McpSchema> out = new IdentityBundleDigester().digest(utf8("[]"));
        assertTrue(out.isEmpty());
        assertThrows(UnsupportedOperationException.class,
            () -> out.add(null));
    }

    @Test
    void nonEmptyResultIsImmutable() {
        List<McpSchema> out =
            new IdentityBundleDigester().digest(utf8("[" + DOC + "]"));
        assertThrows(UnsupportedOperationException.class,
            () -> out.add(null));
    }

    @Test
    void oversizedPayloadFailsClosedBeforeParse() {
        // 17-byte payload against a 16-byte cap (not even valid JSON — proves
        // the size check fires BEFORE any parse).
        byte[] big = "01234567890123456".getBytes(StandardCharsets.UTF_8);
        assertEquals(17, big.length);
        TrustException ex = assertThrows(TrustException.class,
            () -> new IdentityBundleDigester(16, 1024).digest(big));
        assertTrue(ex.getMessage().contains("payload exceeds max bytes"));
    }

    @Test
    void elementCountOverCapFailsClosed() {
        // 3-element array against a 2-element cap; payload bytes well under
        // the (8 MiB) byte cap so only the element-count gate fires.
        String three = "[" + DOC + "," + DOC2 + "," + DOC + "]";
        TrustException ex = assertThrows(TrustException.class,
            () -> new IdentityBundleDigester(8_388_608, 2).digest(utf8(three)));
        assertTrue(ex.getMessage().contains("element count exceeds max"));
    }
}
