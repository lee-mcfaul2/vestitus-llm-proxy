package dev.vestitus.mcpschema;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class McpSchemaTest {
    private static ToolDecl tool() {
        return new ToolDecl("findContact", "Find a contact",
            List.of(new FieldDecl("email", PiiType.DIRECT_IDENTIFIER,
                new IamBinding("crm:read"))));
    }

    private static McpSchema valid() {
        return new McpSchema(
            McpSchemaVersion.CURRENT, "crm-mcp", List.of(tool()),
            new CedarRuleset("permit(principal, action, resource);"),
            new CedarSchemaText("entity User;"));
    }

    @Test
    void validRootConstructs() {
        var s = valid();
        assertEquals(McpSchemaVersion.CURRENT, s.schemaVersion());
        assertEquals("crm-mcp", s.mcpId());
        assertEquals(1, s.tools().size());
    }

    @Test
    void nullSchemaVersionRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new McpSchema(null, "m", List.of(tool()),
                new CedarRuleset("p"), new CedarSchemaText("s")));
    }

    @Test
    void blankMcpIdRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new McpSchema(McpSchemaVersion.CURRENT, " ", List.of(tool()),
                new CedarRuleset("p"), new CedarSchemaText("s")));
    }

    @Test
    void nullToolsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new McpSchema(McpSchemaVersion.CURRENT, "m", null,
                new CedarRuleset("p"), new CedarSchemaText("s")));
    }

    @Test
    void emptyToolsRejectedFailClosed() {
        assertThrows(IllegalArgumentException.class,
            () -> new McpSchema(McpSchemaVersion.CURRENT, "m", List.of(),
                new CedarRuleset("p"), new CedarSchemaText("s")));
    }

    @Test
    void nullRulesetRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new McpSchema(McpSchemaVersion.CURRENT, "m", List.of(tool()),
                null, new CedarSchemaText("s")));
    }

    @Test
    void nullCedarSchemaRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new McpSchema(McpSchemaVersion.CURRENT, "m", List.of(tool()),
                new CedarRuleset("p"), null));
    }

    @Test
    void toolsListIsImmutableCopy() {
        var src = new ArrayList<ToolDecl>();
        src.add(tool());
        var s = new McpSchema(McpSchemaVersion.CURRENT, "m", src,
            new CedarRuleset("p"), new CedarSchemaText("s"));
        src.clear();
        assertEquals(1, s.tools().size());
        assertThrows(UnsupportedOperationException.class, () -> s.tools().add(tool()));
    }
}
