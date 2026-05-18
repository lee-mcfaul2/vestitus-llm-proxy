package dev.vestitus.gate;

import dev.vestitus.mcpschema.McpSchema;
import java.util.ArrayList;
import java.util.List;

/**
 * The ADR-002 §7 blocking static-analysis gate logic: {@code cedar validate}
 * typecheck + identity-predicate lint + cross-MCP injectivity check, over a
 * declared MCP schema (set). Pass iff every check passes; otherwise Reject with
 * the union of all reasons. Fully fail-closed. Verdict-only — the canonical
 * stamped-output transform + content-bound HMAC stamp is Plan 04c; the cvc5
 * symbolic upgrade is the named ADR-002 §7 deferred follow-on.
 */
public final class StaticAnalysisGate {

    private StaticAnalysisGate() {}

    public static GateVerdict vet(McpSchema schema) {
        try {
            List<String> reasons = new ArrayList<>();
            collect(reasons, CedarValidateCheck.check(schema));
            collect(reasons, IdentityPredicateLint.check(schema.ruleset()));
            collect(reasons, CrossMcpInjectivityCheck.checkOne(schema));
            return reasons.isEmpty() ? GateVerdict.pass() : GateVerdict.reject(reasons);
        } catch (Throwable t) {
            return GateVerdict.reject("static-analysis gate error (fail-closed): " + t);
        }
    }

    public static GateVerdict vetAll(List<McpSchema> schemas) {
        try {
            List<String> reasons = new ArrayList<>();
            collect(reasons, CrossMcpInjectivityCheck.checkSet(schemas));
            for (McpSchema s : schemas) {
                collect(reasons, vet(s));
            }
            return reasons.isEmpty() ? GateVerdict.pass() : GateVerdict.reject(reasons);
        } catch (Throwable t) {
            return GateVerdict.reject("static-analysis gate set error (fail-closed): " + t);
        }
    }

    private static void collect(List<String> reasons, GateVerdict v) {
        if (v instanceof GateVerdict.Reject r) {
            reasons.addAll(r.reasons());
        }
    }
}
