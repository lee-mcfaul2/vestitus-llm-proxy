package dev.vestitus.authz;

import dev.vestitus.mcpschema.McpSchema;

/**
 * ADR-003 runtime-pull SPI seam: deterministically compiles a digested,
 * structurally-vetted {@link McpSchema} to an {@link Authorizer}. Fail-closed:
 * implementations throw {@link PolicyCompileException} (or any Throwable, which
 * the orchestrator treats as a reload abort) rather than returning a permissive
 * stand-in.
 */
public interface PolicyCompiler {
    Authorizer compile(McpSchema schema);
}
