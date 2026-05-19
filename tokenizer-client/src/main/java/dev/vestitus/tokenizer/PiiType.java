package dev.vestitus.tokenizer;

/**
 * The PII taxonomy, verbatim from the published pii-tokenizer v0.1.0 contract.
 * Each constant name is exactly the service's wire {@code type} string, so the
 * shipped HTTP client maps by identity. Opaque pass-through for this module:
 * whatever found the PII upstream supplies it; this module never inspects or
 * derives it, and an alternative {@link Tokenizer} may ignore it.
 */
public enum PiiType {
    EMAIL, PHONE, SSN, ADDRESS, POSTAL_CODE, NAME, CREDIT_CARD, IBAN, IP_ADDRESS, DOB
}
