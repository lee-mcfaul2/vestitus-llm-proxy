package dev.vestitus.gate.cli;

import dev.vestitus.mcpschema.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalJsonTest {

    private static McpSchema schema(String mcpId) {
        return new McpSchema(
            McpSchemaVersion.CURRENT, mcpId,
            List.of(new ToolDecl("findContact", "Find a \"contact\"",
                List.of(new FieldDecl("email", PiiType.DIRECT_IDENTIFIER,
                    new IamBinding("crm:read"))))),
            new CedarRuleset("permit(principal == User::\"a\", action, resource);"),
            new CedarSchemaText("entity User; entity Resource;"));
    }

    @Test
    void canonicalBytesAreStableAcrossRuns() {
        byte[] a = CanonicalJson.canonical(schema("crm-mcp"));
        byte[] b = CanonicalJson.canonical(schema("crm-mcp"));
        assertArrayEquals(a, b);
    }

    @Test
    void canonicalIsFixedOrderAndWhitespaceFree() {
        String s = new String(CanonicalJson.canonical(schema("crm-mcp")),
            StandardCharsets.UTF_8);
        // Fixed declared key order; no insignificant whitespace.
        assertTrue(s.startsWith(
            "{\"schemaVersion\":\"1.0.0\",\"mcpId\":\"crm-mcp\",\"tools\":["),
            s);
        assertEquals(-1, s.indexOf("\n"));
        assertEquals(-1, s.indexOf(" \""));
        assertTrue(s.indexOf("\"name\":\"findContact\"") < s.indexOf("\"fields\":["));
        assertTrue(s.indexOf("\"ruleset\":") < s.indexOf("\"cedarSchema\":"));
        // Quote inside the description is JSON-escaped.
        assertTrue(s.contains("Find a \\\"contact\\\""), s);
    }

    @Test
    void canonicalArrayPreservesInputOrderAndIsAnArray() {
        String s = CanonicalJson.canonicalArrayString(
            List.of(schema("b-mcp"), schema("a-mcp")));
        assertTrue(s.startsWith("[{"));
        assertTrue(s.endsWith("}]"));
        // Input order preserved (NOT sorted): b-mcp before a-mcp.
        assertTrue(s.indexOf("\"mcpId\":\"b-mcp\"")
            < s.indexOf("\"mcpId\":\"a-mcp\""));
    }

    @Test
    void canonicalArrayStringDecodesTheSameBytes() {
        var list = List.of(schema("x-mcp"));
        assertArrayEquals(CanonicalJson.canonicalArray(list),
            CanonicalJson.canonicalArrayString(list)
                .getBytes(StandardCharsets.UTF_8));
    }
}
