/**
 * The vestitus content-inspection SPI: the per-request contract gateway-core
 * runs over untrusted content — inbound at ingress, outbound over
 * authorized-only MCP responses before they reach the LLM. See the design spec
 * dated 2026-05-22.
 *
 * <p>Two structural guarantees of spec §5.7 are made enforceable by the Java
 * type system rather than by developer discipline:
 *
 * <ul>
 *   <li><b>Inv. 9</b> — only a {@link dev.vestitus.inspect.RawSpanDetector},
 *       which always inspects the original {@link
 *       dev.vestitus.inspect.RawContent}, can produce a {@link
 *       dev.vestitus.inspect.SpanFinding}. A {@link
 *       dev.vestitus.inspect.SemanticDetector} sees a possibly-LOSSY {@link
 *       dev.vestitus.inspect.NormalizedView} and cannot produce a span.
 *       "Detect on a translation, act on the original" is unrepresentable.</li>
 *   <li><b>Inv. 10</b> — the security floor (mandatory credential detection;
 *       mandatory PII detection before tokenize/deliver) is a constructor
 *       argument of {@link dev.vestitus.inspect.InspectionPipeline}. A pipeline
 *       without the floor does not compile.</li>
 * </ul>
 *
 * <p><b>The §5.7.1 footgun, stated candidly.</b> A {@link
 * dev.vestitus.inspect.Transformer} declares its {@link
 * dev.vestitus.inspect.SpanFidelity}; whether a {@code SPAN_PRESERVING}
 * transformer actually preserves offsets is the transformer's promise and is
 * NOT statically verifiable. The assembly-time validator rejects
 * configurations that merely <i>read</i> as unsafe (a RawSpanDetector ordered
 * after a Transformer); it cannot check span-fidelity correctness. Implementers
 * must exercise the offset-correctness fixtures in this module's tests.
 *
 * <p>A RawSpanDetector that must look inside a decoded payload (base64, etc.)
 * performs the decode and the offset mapping back to the original inside its
 * own implementation; the SPI does not split that out.
 *
 * <p>This module emits findings and a sealed {@link
 * dev.vestitus.inspect.PipelineOutcome}; it does not act on them, run as part
 * of the request path, apply dispositions, or emit audit/metrics — those are
 * gateway-core's responsibility.
 */
package dev.vestitus.inspect;
