package dev.vestitus.gate.cli;

import dev.vestitus.mcpschema.FieldDecl;
import dev.vestitus.mcpschema.McpSchema;
import dev.vestitus.mcpschema.ToolDecl;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Deterministic, byte-stable canonical serialization of an {@code McpSchema}
 * (set) — the data the gate stamps and the core loads (ADR-002 §5: a required
 * transform, not a checkpoint). Members are written in a FIXED declared order
 * (schemaVersion, mcpId, tools[name, description, fields[name, pii,
 * iam.entitlement]], ruleset.text, cedarSchema.text); no map/iteration order,
 * no timestamps, no insignificant whitespace, UTF-8. Reproducible by
 * construction so two independent runs / two container instances produce
 * identical bytes.
 */
final class CanonicalJson {

    private CanonicalJson() {}

    static byte[] canonical(McpSchema s) {
        StringBuilder b = new StringBuilder(256);
        writeSchema(b, s);
        return b.toString().getBytes(StandardCharsets.UTF_8);
    }

    static byte[] canonicalArray(List<McpSchema> set) {
        return canonicalArrayString(set).getBytes(StandardCharsets.UTF_8);
    }

    static String canonicalArrayString(List<McpSchema> set) {
        StringBuilder b = new StringBuilder(512);
        b.append('[');
        for (int i = 0; i < set.size(); i++) {
            if (i > 0) b.append(',');
            writeSchema(b, set.get(i));
        }
        b.append(']');
        return b.toString();
    }

    private static void writeSchema(StringBuilder b, McpSchema s) {
        b.append('{');
        key(b, "schemaVersion"); str(b, s.schemaVersion().value());
        b.append(',');
        key(b, "mcpId"); str(b, s.mcpId());
        b.append(',');
        key(b, "tools"); b.append('[');
        List<ToolDecl> tools = s.tools();
        for (int i = 0; i < tools.size(); i++) {
            if (i > 0) b.append(',');
            writeTool(b, tools.get(i));
        }
        b.append(']');
        b.append(',');
        key(b, "ruleset"); str(b, s.ruleset().text());
        b.append(',');
        key(b, "cedarSchema"); str(b, s.cedarSchema().text());
        b.append('}');
    }

    private static void writeTool(StringBuilder b, ToolDecl t) {
        b.append('{');
        key(b, "name"); str(b, t.name());
        b.append(',');
        key(b, "description"); str(b, t.description());
        b.append(',');
        key(b, "fields"); b.append('[');
        List<FieldDecl> fields = t.fields();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) b.append(',');
            writeField(b, fields.get(i));
        }
        b.append(']');
        b.append('}');
    }

    private static void writeField(StringBuilder b, FieldDecl f) {
        b.append('{');
        key(b, "name"); str(b, f.name());
        b.append(',');
        key(b, "pii"); str(b, f.pii().name());
        b.append(',');
        key(b, "iam"); str(b, f.iam().entitlement());
        b.append('}');
    }

    private static void key(StringBuilder b, String k) {
        str(b, k);
        b.append(':');
    }

    private static void str(StringBuilder b, String v) {
        b.append('"');
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        b.append('"');
    }
}
