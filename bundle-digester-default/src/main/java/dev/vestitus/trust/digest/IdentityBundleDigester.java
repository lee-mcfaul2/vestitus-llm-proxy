package dev.vestitus.trust.digest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.vestitus.mcpschema.McpSchema;
import dev.vestitus.mcpschema.McpSchemaJson;
import dev.vestitus.trust.BundleDigester;
import dev.vestitus.trust.TrustException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The ADR-003 D4 default <b>identity</b> {@link BundleDigester}: it expects the
 * already-verified payload to be an {@code mcp-schema} JSON array and maps each
 * element through {@code mcp-schema}'s strict {@link McpSchemaJson#read}
 * (Plan 05c).
 *
 * <p>It consumes ONLY already-verified bytes — the caller passes
 * {@code VerificationOutcome.Verified.authenticatedPayload()}, never raw or
 * unverified input. Verification proves <i>provenance</i>, not
 * <i>well-formedness</i>: a compromised/careless signer can ship
 * adversarial-but-signed content, so this is treated as an
 * attacker-shaped-input parser.</p>
 *
 * <p><b>D4 hardening posture.</b> Input size is bounded on the raw byte length
 * <i>before</i> a String/parse is built (no OOM on a huge payload); the
 * top-level element count is bounded; parsing uses Jackson <i>without</i>
 * default/polymorphic typing (no XXE/SSRF/deserialization-gadget surface — the
 * shared {@link #MAPPER} only splits the top-level array; strict per-document
 * validation is {@link McpSchemaJson#read}'s job). This identity digester does
 * NO decompression or un-archiving, so zip-slip / decompression-bomb is
 * structurally absent; the size+element bounds cover oversized / bomb-shaped
 * input. Any failure is wrapped fail-closed as a {@link TrustException}. This
 * is exactly how ADR-003 D4 is satisfied for the identity case.</p>
 *
 * <p><b>Set policy is the core's responsibility, NOT the digester's
 * (ADR-003 D6/D1/D8).</b> An empty array {@code []} yields an empty immutable
 * list; empty-set / authoritative-complete / duplicate-or-missing-{@code mcpId}
 * policy is enforced by the core (Plans 05e/05h), not here. This class's single
 * responsibility is format translation + D4 hardening.</p>
 */
public final class IdentityBundleDigester implements BundleDigester {

    /**
     * Conservative ADR-003 D4 fail-closed default cap: 8 MiB is generous for a
     * JSON array of {@code mcp-schema} documents while bounding heap so an
     * oversized payload cannot OOM the reload path.
     */
    private static final int DEFAULT_MAX_PAYLOAD_BYTES = 8 * 1024 * 1024;

    /**
     * Conservative ADR-003 D4 fail-closed default cap: a bounded top-level
     * element count rejects a bomb-shaped (huge-array) payload before any
     * per-document work.
     */
    private static final int DEFAULT_MAX_ELEMENTS = 1024;

    /**
     * Shared, used ONLY to split the top-level array. JSON + Jackson WITHOUT
     * default/polymorphic typing has no XXE/SSRF/deserialization-gadget
     * surface. Strict per-document validation is {@link McpSchemaJson#read}'s
     * job, not this mapper's.
     */
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final int maxPayloadBytes;
    private final int maxElements;

    /** Production constructor — the secure ADR-003 D4 default caps. */
    public IdentityBundleDigester() {
        this(DEFAULT_MAX_PAYLOAD_BYTES, DEFAULT_MAX_ELEMENTS);
    }

    /**
     * Testability seam (package-private): deterministic small caps for cheap
     * bound tests without allocating an 8 MiB payload. The public no-arg
     * constructor's secure default is unaffected.
     */
    IdentityBundleDigester(int maxPayloadBytes, int maxElements) {
        if (maxPayloadBytes <= 0) {
            throw new IllegalArgumentException("maxPayloadBytes must be > 0");
        }
        if (maxElements <= 0) {
            throw new IllegalArgumentException("maxElements must be > 0");
        }
        this.maxPayloadBytes = maxPayloadBytes;
        this.maxElements = maxElements;
    }

    @Override
    public List<McpSchema> digest(byte[] authenticatedPayload)
            throws TrustException {
        if (authenticatedPayload == null) {
            throw new TrustException(
                "authenticated payload must be non-null", null);
        }
        if (authenticatedPayload.length > maxPayloadBytes) {
            throw new TrustException("payload exceeds max bytes ("
                + authenticatedPayload.length + " > " + maxPayloadBytes + ")",
                null);
        }
        try {
            String json =
                new String(authenticatedPayload, StandardCharsets.UTF_8);
            JsonNode root = MAPPER.readTree(json);
            if (root == null || !root.isArray()) {
                throw new TrustException(
                    "bundle payload must be a JSON array of mcp-schema documents",
                    null);
            }
            if (root.size() > maxElements) {
                throw new TrustException("bundle element count exceeds max ("
                    + root.size() + " > " + maxElements + ")", null);
            }
            List<McpSchema> result = new ArrayList<>();
            for (JsonNode element : root) {
                // Strict per-doc validation: a missing required mcp-schema
                // field surfaces as McpSchemaParseException, caught below and
                // re-wrapped fail-closed.
                result.add(McpSchemaJson.read(MAPPER.writeValueAsString(element)));
            }
            return List.copyOf(result);
        } catch (TrustException e) {
            throw e;
        } catch (Throwable t) {
            throw new TrustException("bundle digest failed (fail-closed)", t);
        }
    }
}
