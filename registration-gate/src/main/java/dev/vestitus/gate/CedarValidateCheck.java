package dev.vestitus.gate;

import dev.vestitus.authz.cedar.CedarNative;
import dev.vestitus.mcpschema.McpSchema;

/**
 * ADR-002 §7 check 1: the MCP's Cedar ruleset must typecheck against its
 * per-MCP Cedar schema. Reuses the already-tested {@link CedarNative#validate}
 * binding (no Cedar reimplementation). CedarResult mapping (from the C ABI):
 * Deny=0, Allow=1, Valid=2, Invalid=3, Error=-1. Only code 2 (Valid) passes;
 * everything else (including any boundary error) is fail-closed reject.
 */
public final class CedarValidateCheck {

    private static final int CEDAR_VALID = 2;

    private CedarValidateCheck() {}

    public static GateVerdict check(McpSchema schema) {
        try {
            var r = CedarNative.instance().validate(
                schema.cedarSchema().text(), schema.ruleset().text());
            if (r.code() == CEDAR_VALID) {
                return GateVerdict.pass();
            }
            return GateVerdict.reject(
                "cedar validate failed (code=" + r.code() + "): " + r.diag());
        } catch (Throwable t) {
            return GateVerdict.reject("cedar validate error (fail-closed): " + t);
        }
    }
}
