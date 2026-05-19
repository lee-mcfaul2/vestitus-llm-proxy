package dev.vestitus.authz.cedar;

import dev.vestitus.authz.Authorizer;
import dev.vestitus.authz.PolicyCompileException;
import dev.vestitus.authz.PolicyCompiler;
import dev.vestitus.mcpschema.McpSchema;

/**
 * Default {@link PolicyCompiler}: a deterministic pre-native size/complexity
 * guard on the ruleset text, then {@code new CedarAuthorizer(...)}. The guard
 * runs BEFORE any native call so a pathological ruleset is rejected without
 * touching the engine. Fail-closed: a bound breach throws
 * {@link PolicyCompileException}; the orchestrator treats that as a reload
 * abort.
 */
public final class CedarPolicyCompiler implements PolicyCompiler {

    private static final int DEFAULT_MAX_RULESET_CHARS = 20_000;
    private static final int DEFAULT_MAX_STATEMENTS = 256;

    private final int maxRulesetChars;
    private final int maxStatements;

    public CedarPolicyCompiler() {
        this(DEFAULT_MAX_RULESET_CHARS, DEFAULT_MAX_STATEMENTS);
    }

    /** Test seam (mirrors IdentityBundleDigester(int,int)): lower the bounds. */
    CedarPolicyCompiler(int maxRulesetChars, int maxStatements) {
        if (maxRulesetChars <= 0 || maxStatements <= 0) {
            throw new IllegalArgumentException("bounds must be > 0");
        }
        this.maxRulesetChars = maxRulesetChars;
        this.maxStatements = maxStatements;
    }

    @Override
    public Authorizer compile(McpSchema schema) {
        String text = schema.ruleset().text();
        if (text.length() > maxRulesetChars) {
            throw new PolicyCompileException(
                "ruleset text length " + text.length()
                    + " exceeds bound " + maxRulesetChars, null);
        }
        int statements = countKeyword(text, "permit") + countKeyword(text, "forbid");
        if (statements > maxStatements) {
            throw new PolicyCompileException(
                "ruleset statement count " + statements
                    + " exceeds bound " + maxStatements, null);
        }
        try {
            return new CedarAuthorizer(text);
        } catch (Throwable t) {
            throw new PolicyCompileException(
                "Cedar engine rejected the ruleset", t);
        }
    }

    private static int countKeyword(String text, String kw) {
        int n = 0;
        int from = 0;
        while (true) {
            int idx = text.indexOf(kw, from);
            if (idx < 0) {
                return n;
            }
            n++;
            from = idx + kw.length();
        }
    }
}
