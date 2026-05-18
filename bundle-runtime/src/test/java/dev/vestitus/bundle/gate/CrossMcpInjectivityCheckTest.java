package dev.vestitus.bundle.gate;

import dev.vestitus.mcpschema.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CrossMcpInjectivityCheckTest {

    private static FieldDecl f(String name) {
        return new FieldDecl(name, PiiType.NONE, new IamBinding("x"));
    }

    private static McpSchema schema(String mcpId, ToolDecl... tools) {
        return new McpSchema(McpSchemaVersion.CURRENT, mcpId, List.of(tools),
            new CedarRuleset("permit(principal == User::\"a\", action, resource);"),
            new CedarSchemaText("entity User;"));
    }

    @Test
    void cleanSchemaPasses() {
        var s = schema("crm-mcp",
            new ToolDecl("findContact", "d", List.of(f("email"), f("phone"))));
        assertTrue(CrossMcpInjectivityCheck.checkOne(s).passed());
    }

    @Test
    void slashInMcpIdRejected() {
        assertFalse(CrossMcpInjectivityCheck.checkOne(
            schema("crm/mcp", new ToolDecl("t", "d", List.of(f("a"))))).passed());
    }

    @Test
    void slashInToolNameRejected() {
        assertFalse(CrossMcpInjectivityCheck.checkOne(
            schema("crm", new ToolDecl("a/b", "d", List.of(f("a"))))).passed());
    }

    @Test
    void controlCharInFieldNameRejected() {
        assertFalse(CrossMcpInjectivityCheck.checkOne(
            schema("crm", new ToolDecl("t", "d", List.of(f("ab"))))).passed());
    }

    @Test
    void duplicateToolNameRejected() {
        assertFalse(CrossMcpInjectivityCheck.checkOne(
            schema("crm",
                new ToolDecl("t", "d", List.of(f("a"))),
                new ToolDecl("t", "d", List.of(f("b"))))).passed());
    }

    @Test
    void duplicateFieldNameWithinToolRejected() {
        assertFalse(CrossMcpInjectivityCheck.checkOne(
            schema("crm",
                new ToolDecl("t", "d", List.of(f("a"), f("a"))))).passed());
    }

    @Test
    void distinctMcpIdSetPasses() {
        var a = schema("crm", new ToolDecl("t", "d", List.of(f("a"))));
        var b = schema("hr", new ToolDecl("u", "d", List.of(f("b"))));
        assertTrue(CrossMcpInjectivityCheck.checkSet(List.of(a, b)).passed());
    }

    @Test
    void duplicateMcpIdAcrossSetRejected() {
        var a = schema("crm", new ToolDecl("t", "d", List.of(f("a"))));
        var b = schema("crm", new ToolDecl("u", "d", List.of(f("b"))));
        GateVerdict v = CrossMcpInjectivityCheck.checkSet(List.of(a, b));
        assertFalse(v.passed());
        assertTrue(((GateVerdict.Reject) v).reasons().get(0)
            .toLowerCase().contains("duplicate"));
    }
}
