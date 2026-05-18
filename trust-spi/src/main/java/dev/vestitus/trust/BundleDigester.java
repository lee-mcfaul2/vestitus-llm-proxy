package dev.vestitus.trust;

import dev.vestitus.mcpschema.McpSchema;
import java.util.List;

/**
 * The verified-payload-to-internal-types SPI seam (ADR-003 D2/D4). An OPEN
 * interface (a compile-time extension point, not sealed; no runtime
 * code-load). The default identity impl is Plan 05c.
 *
 * <p><b>Hardening contract a conformant implementation MUST uphold
 * (ADR-003 D4):</b></p>
 * <ul>
 *   <li><b>Post-verify only:</b> operates ONLY on already-verified bytes —
 *       the caller passes {@link VerificationOutcome.Verified#authenticatedPayload()},
 *       NEVER raw/unverified input ({@link Bundle#payload()}).</li>
 *   <li><b>Fail-closed:</b> MUST throw {@link TrustException} on ANY
 *       malformed/oversized/ambiguous input. The core treats that as a
 *       fail-closed reload abort, keeping last-good (never a grant).</li>
 *   <li><b>Attacker-shaped-input parser:</b> verification proves provenance,
 *       not well-formedness — a compromised/careless signer can ship
 *       adversarial-but-signed content. MUST enforce bounded input size,
 *       bounded element count, and MUST NOT perform external entity
 *       resolution / SSRF, archive auto-expansion (zip-slip /
 *       decompression-bomb), or native/YAML deserialization-gadget parsing.</li>
 *   <li><b>Immutable result:</b> the returned {@code List<McpSchema>} MUST be
 *       an immutable copy ({@code List.copyOf}).</li>
 * </ul>
 * The default identity impl (Plan 05c) maps the payload via {@code
 * mcp-schema}'s strict {@code McpSchemaJson.read}.
 */
public interface BundleDigester {
    List<McpSchema> digest(byte[] authenticatedPayload) throws TrustException;
}
