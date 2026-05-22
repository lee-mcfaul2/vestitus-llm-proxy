package dev.vestitus.inspect;

/**
 * A stable, deployment-uniform string-keyed identifier for audit and metric
 * tags. A PII {@link SpanFinding} carries its specific type here under a
 * stable {@code pii.*} namespace ({@code pii.email}, {@code pii.us_ssn}, …);
 * gateway-core owns the {@code ReasonCode -> tokenizer PiiType} mapping. The
 * SPI never couples to {@code tokenizer-client}.
 */
public record ReasonCode(String code) {
    public ReasonCode {
        if (code == null || code.isBlank())
            throw new IllegalArgumentException("reason code required");
    }
}
