package dev.vestitus.authz.cedar;

import dev.vestitus.authz.AuthorizationDecision;
import dev.vestitus.authz.AuthorizationRequest;
import dev.vestitus.authz.Authorizer;
import dev.vestitus.authz.Principal;
import dev.vestitus.authz.ResourceRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Default Cedar-engine {@link Authorizer} (spec §5.4). Evaluates a fixed,
 * per-cell Cedar policy text (constructor argument — the SPI request carries
 * no policy; the per-MCP signed-ruleset lifecycle is §5.3 / a later phase)
 * against the authenticated request via the vendored {@code cedar-cabi} shim.
 *
 * <h2>Entity-mapping convention (fixed)</h2>
 * <ul>
 *   <li>principal &rarr; {@code User::"<escaped principal.id>"}</li>
 *   <li>action    &rarr; {@code Action::"<escaped action>"}</li>
 *   <li>resource  &rarr; {@code Resource::"<mcpId>/<tool>/<field>"} (each escaped)</li>
 *   <li>context_json &rarr; JSON object:
 *     {@code {"scopes":[...],"principal_attrs":{...},"resource_tags":{...},
 *     "mcp":"...","tool":"...","field":"...","request":{...}}}</li>
 *   <li>entities_json &rarr; {@code []} (no entity store at this layer)</li>
 * </ul>
 * All context keys are always present (stable shape ⇒ no missing-attribute
 * eval errors). Strings are JSON/Cedar escaped.
 *
 * <h2>Fail-closed</h2>
 * Only a clean native {@code Allow} (code 1) yields {@link AuthorizationDecision#allow()}.
 * Native {@code Deny} (0), {@code Error} (-1), any unexpected code, a null
 * request, or any {@link Throwable} in the mapping/binding path yields
 * {@link AuthorizationDecision#deny(String)} with a non-blank, reason-labeled
 * message (spec §6 inv.7; §7 "engine error ⇒ deny, never pass-through").
 * This implementation never throws (SPI contract).
 */
public final class CedarAuthorizer implements Authorizer {

    private final String policyText;
    private final CedarNative cedar;

    public CedarAuthorizer(String policyText) {
        if (policyText == null || policyText.isBlank()) {
            throw new IllegalArgumentException("cedar policy text required");
        }
        this.policyText = policyText;
        this.cedar = CedarNative.instance();
    }

    @Override
    public AuthorizationDecision authorize(AuthorizationRequest request) {
        try {
            if (request == null) {
                return AuthorizationDecision.deny("null request (fail-closed)");
            }
            Principal p = request.principal();
            ResourceRef r = request.resource();

            String principalUid = "User::\"" + cedarId(p.id()) + "\"";
            String actionUid    = "Action::\"" + cedarId(request.action()) + "\"";
            String resourceUid  = "Resource::\"" + cedarId(
                r.mcpId() + "/" + r.tool() + "/" + r.field()) + "\"";
            String contextJson  = buildContextJson(p, r, request.context());

            CedarNative.Result res = cedar.isAuthorized(
                policyText, principalUid, actionUid, resourceUid, contextJson, "[]");

            return switch (res.code()) {
                case 1 -> AuthorizationDecision.allow();
                case 0 -> AuthorizationDecision.deny(
                    "cedar deny (fail-closed): "
                        + (res.diag() == null ? "no diagnostic" : res.diag()));
                case -1 -> AuthorizationDecision.deny(
                    "cedar engine error (fail-closed): "
                        + (res.diag() == null ? "no diagnostic" : res.diag()));
                default -> AuthorizationDecision.deny(
                    "cedar unexpected code " + res.code() + " (fail-closed)");
            };
        } catch (Throwable t) {
            // Absolute fail-closed backstop: never throw out of the SPI.
            return AuthorizationDecision.deny(
                "cedar authorizer error (fail-closed): " + t.getClass().getSimpleName());
        }
    }

    /** Escape a Cedar entity-id literal body: backslash and quote. */
    private static String cedarId(String raw) {
        StringBuilder b = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' || c == '"') b.append('\\');
            b.append(c);
        }
        return b.toString();
    }

    private static String buildContextJson(Principal p, ResourceRef r,
                                           Map<String, String> requestCtx) {
        StringBuilder b = new StringBuilder(256);
        b.append('{');
        b.append("\"scopes\":");
        jsonStringArray(b, new TreeSet<>(p.scopes())); // sorted -> deterministic
        b.append(",\"principal_attrs\":");
        jsonStringMap(b, p.attributes());
        b.append(",\"resource_tags\":");
        jsonStringMap(b, r.tags());
        b.append(",\"mcp\":");
        jsonString(b, r.mcpId());
        b.append(",\"tool\":");
        jsonString(b, r.tool());
        b.append(",\"field\":");
        jsonString(b, r.field());
        b.append(",\"request\":");
        jsonStringMap(b, requestCtx);
        b.append('}');
        return b.toString();
    }

    private static void jsonStringArray(StringBuilder b, Iterable<String> values) {
        b.append('[');
        boolean first = true;
        for (String v : values) {
            if (!first) b.append(',');
            first = false;
            jsonString(b, v);
        }
        b.append(']');
    }

    private static void jsonStringMap(StringBuilder b, Map<String, String> m) {
        b.append('{');
        // Deterministic key order (TreeMap-style) for stable JSON.
        List<String> keys = new ArrayList<>(m.keySet());
        keys.sort(String::compareTo);
        boolean first = true;
        for (String k : keys) {
            if (!first) b.append(',');
            first = false;
            jsonString(b, k);
            b.append(':');
            jsonString(b, m.get(k));
        }
        b.append('}');
    }

    /** RFC 8259 string escaping. */
    private static void jsonString(StringBuilder b, String s) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\b' -> b.append("\\b");
                case '\t' -> b.append("\\t");
                case '\n' -> b.append("\\n");
                case '\f' -> b.append("\\f");
                case '\r' -> b.append("\\r");
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
