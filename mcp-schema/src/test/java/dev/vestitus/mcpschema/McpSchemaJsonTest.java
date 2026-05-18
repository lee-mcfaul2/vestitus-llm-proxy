package dev.vestitus.mcpschema;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class McpSchemaJsonTest {
    private static McpSchema sample() {
        return new McpSchema(
            McpSchemaVersion.CURRENT, "crm-mcp",
            List.of(new ToolDecl("findContact", "Find a contact",
                List.of(new FieldDecl("email", PiiType.DIRECT_IDENTIFIER,
                    new IamBinding("crm:read"))))),
            new CedarRuleset("permit(principal, action, resource);"),
            new CedarSchemaText("entity User;"));
    }

    @Test
    void roundTripsIdentity() {
        McpSchema m = sample();
        String json = McpSchemaJson.write(m);
        McpSchema back = McpSchemaJson.read(json);
        assertEquals(m, back);
    }

    @Test
    void unknownPropertyIsFailClosed() {
        String json = McpSchemaJson.write(sample());
        String tampered = json.substring(0, json.length() - 1) + ",\"rogue\":1}";
        assertThrows(McpSchemaParseException.class, () -> McpSchemaJson.read(tampered));
    }

    @Test
    void trailingTokenIsFailClosed() {
        String valid = McpSchemaJson.write(sample());
        assertThrows(McpSchemaParseException.class, () -> McpSchemaJson.read(valid + " {}"));
    }

    @Test
    void garbageIsFailClosed() {
        assertThrows(McpSchemaParseException.class, () -> McpSchemaJson.read("not json"));
    }

    @Test
    void missingFieldPiiIsFailClosedDefaultDeny() {
        // A field declaration with no "pii" property: the FieldDecl compact
        // ctor sees pii == null and throws; McpSchemaJson wraps it fail-closed.
        String json = """
            {"schemaVersion":{"value":"1.0.0"},"mcpId":"crm-mcp",
             "tools":[{"name":"findContact","description":"d",
               "fields":[{"name":"email","iam":{"entitlement":"crm:read"}}]}],
             "ruleset":{"text":"permit(principal, action, resource);"},
             "cedarSchema":{"text":"entity User;"}}""";
        assertThrows(McpSchemaParseException.class, () -> McpSchemaJson.read(json));
    }

    @Test
    void missingFieldIamIsFailClosedDefaultDeny() {
        String json = """
            {"schemaVersion":{"value":"1.0.0"},"mcpId":"crm-mcp",
             "tools":[{"name":"findContact","description":"d",
               "fields":[{"name":"email","pii":"NONE"}]}],
             "ruleset":{"text":"permit(principal, action, resource);"},
             "cedarSchema":{"text":"entity User;"}}""";
        assertThrows(McpSchemaParseException.class, () -> McpSchemaJson.read(json));
    }
}
