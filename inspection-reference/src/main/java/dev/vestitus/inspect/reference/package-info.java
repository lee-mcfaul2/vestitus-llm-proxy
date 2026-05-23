/**
 * Pure-library reference {@link dev.vestitus.inspect.RawSpanDetector}
 * implementations that satisfy the {@link
 * dev.vestitus.inspect.InspectionPipeline} structural floor. See the design
 * spec dated 2026-05-22, §9.
 *
 * <ul>
 *   <li>{@link dev.vestitus.inspect.reference.RegexCredentialDetector} &rarr;
 *       {@code FindingKind.CREDENTIAL}: PEM private-key blocks, AWS
 *       access-key IDs, GitHub tokens, Google API keys, Slack tokens,
 *       JWT-shaped triplets. Each finding carries a stable {@code cred.*}
 *       {@link dev.vestitus.inspect.ReasonCode}.</li>
 *   <li>{@link dev.vestitus.inspect.reference.RegexPiiDetector} &rarr;
 *       {@code FindingKind.PII}: email, US SSN (with basic structural
 *       validity), North-American phone, Luhn-checked card numbers. Each
 *       finding carries its specific PII type in a stable {@code pii.*}
 *       {@link dev.vestitus.inspect.ReasonCode}; {@code gateway-core} owns
 *       the {@code ReasonCode -> tokenizer PiiType} mapping (design-spec
 *       §9.1). This module never couples to {@code tokenizer-client}.</li>
 * </ul>
 *
 * <p><b>Coverage candor (design-spec §9.2).</b> Pattern detectors miss novel
 * formats: an AWS key without one of the listed prefixes, a Stripe-style
 * {@code sk_live_*} key, a homegrown bearer token, international SSNs,
 * non-NANP phone formats. This is the named residual risk; the mandatory
 * floor plus the gateway-server's fail-closed posture bound it. These
 * detectors are a credible default, not a guarantee. A deployment wanting
 * stronger coverage swaps in a better {@code RawSpanDetector}.
 *
 * <p>Both detectors are deterministic with no I/O. The {@code inspect} body
 * is wrapped {@code try { ... } catch (Throwable t) { return StageFailed(...)
 * }} per the SPI's never-throw contract.
 */
package dev.vestitus.inspect.reference;
