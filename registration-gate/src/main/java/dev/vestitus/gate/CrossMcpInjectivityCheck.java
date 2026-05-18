package dev.vestitus.gate;

import dev.vestitus.mcpschema.FieldDecl;
import dev.vestitus.mcpschema.McpSchema;
import dev.vestitus.mcpschema.ToolDecl;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ADR-002 §7 check 3 / spec §5.4 / Inv. 11. The engine composes the Cedar
 * resource UID as {@code mcpId/tool/field} (mirroring {@code CedarAuthorizer}).
 * {@code /}-free + control-char-free components, set-unique {@code mcpId}s and
 * intra-scope-unique tool/field names make the (mcpId, tool, field) -> UID
 * mapping injective across the whole assembled image — no cross-cell collision
 * or reach. {@link #unsafeComponent} is deliberately reimplemented because the
 * original is {@code private static} in {@code CedarAuthorizer}, not reusable.
 */
public final class CrossMcpInjectivityCheck {

    private CrossMcpInjectivityCheck() {}

    private static boolean unsafeComponent(String s) {
        if (s == null) return true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '/' || c < 0x20) return true;
        }
        return false;
    }

    public static GateVerdict checkOne(McpSchema s) {
        try {
            List<String> reasons = new ArrayList<>();
            if (unsafeComponent(s.mcpId())) {
                reasons.add("unsafe mcpId component (contains '/' or control char): "
                    + s.mcpId());
            }
            Set<String> toolNames = new HashSet<>();
            for (ToolDecl tool : s.tools()) {
                if (unsafeComponent(tool.name())) {
                    reasons.add("unsafe tool name component: " + tool.name());
                }
                if (!toolNames.add(tool.name())) {
                    reasons.add("duplicate tool name in mcp '" + s.mcpId()
                        + "': " + tool.name());
                }
                Set<String> fieldNames = new HashSet<>();
                for (FieldDecl field : tool.fields()) {
                    if (unsafeComponent(field.name())) {
                        reasons.add("unsafe field name component: " + field.name());
                    }
                    if (!fieldNames.add(field.name())) {
                        reasons.add("duplicate field name in tool '" + tool.name()
                            + "': " + field.name());
                    }
                }
            }
            return reasons.isEmpty() ? GateVerdict.pass() : GateVerdict.reject(reasons);
        } catch (Throwable t) {
            return GateVerdict.reject("injectivity check error (fail-closed): " + t);
        }
    }

    public static GateVerdict checkSet(List<McpSchema> schemas) {
        try {
            List<String> reasons = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (McpSchema s : schemas) {
                if (!seen.add(s.mcpId())) {
                    reasons.add("duplicate mcpId across the assembled set: "
                        + s.mcpId());
                }
            }
            for (McpSchema s : schemas) {
                GateVerdict v = checkOne(s);
                if (v instanceof GateVerdict.Reject r) {
                    reasons.addAll(r.reasons());
                }
            }
            return reasons.isEmpty() ? GateVerdict.pass() : GateVerdict.reject(reasons);
        } catch (Throwable t) {
            return GateVerdict.reject("injectivity set check error (fail-closed): " + t);
        }
    }
}
