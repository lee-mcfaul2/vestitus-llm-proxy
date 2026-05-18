package dev.vestitus.mcpschema;

/**
 * Controlled PII vocabulary for a declared MCP field (spec-silent decision,
 * fixed in this module; {@code PiiTypeDriftTest} pins the constant set so it
 * never grows or renames silently).
 */
public enum PiiType {
    /**
     * Explicitly classified as NOT personally-identifiable. This is still a
     * positive, stated classification: an <em>omitted</em> PII annotation is a
     * rejection (default-deny, see {@code FieldDecl}), never an implicit NONE.
     */
    NONE,
    /** Directly identifies a natural person on its own (e.g. full name, email, SSN). */
    DIRECT_IDENTIFIER,
    /** Re-identifies in combination with other fields (e.g. ZIP, birth date, role). */
    QUASI_IDENTIFIER,
    /** Special-category / sensitive data (e.g. health, biometrics, credentials). */
    SENSITIVE
}
