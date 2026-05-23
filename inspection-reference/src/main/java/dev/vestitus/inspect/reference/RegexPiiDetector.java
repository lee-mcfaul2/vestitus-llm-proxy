package dev.vestitus.inspect.reference;

import dev.vestitus.inspect.FindingKind;
import dev.vestitus.inspect.OriginalOffset;
import dev.vestitus.inspect.RawContent;
import dev.vestitus.inspect.RawSpanDetector;
import dev.vestitus.inspect.RawSpanOutcome;
import dev.vestitus.inspect.ReasonCode;
import dev.vestitus.inspect.SpanFinding;
import dev.vestitus.inspect.StageId;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A pure-library {@link RawSpanDetector} that finds common PII shapes — email,
 * US SSN (with basic structural validity), North-American phone (NANP), and
 * Luhn-checked card numbers. Deterministic; no I/O. Satisfies the PII floor
 * of {@link dev.vestitus.inspect.InspectionPipeline}.
 *
 * <p>Each finding carries its specific PII type in a stable {@code pii.*}
 * {@link ReasonCode}; {@code gateway-core} owns the {@code ReasonCode ->
 * tokenizer PiiType} mapping (design-spec §9.1). This module never couples to
 * {@code tokenizer-client}.
 *
 * <p><b>Coverage candor (design-spec §9.2).</b> Pattern detectors miss novel
 * PII shapes: international SSNs, non-NANP phone formats, unhyphenated US
 * SSNs, formatted-without-separators card numbers, and many edge cases. This
 * is the named residual risk; the mandatory floor plus the gateway's
 * fail-closed posture bound it.
 */
public final class RegexPiiDetector implements RawSpanDetector {

    private static final StageId DEFAULT_ID = new StageId("inspection.pii-regex");

    private static final Pattern EMAIL = Pattern.compile(
        "\\b[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}\\b");

    private static final Pattern US_SSN = Pattern.compile(
        "\\b(?!000|666|9\\d{2})\\d{3}-(?!00)\\d{2}-(?!0000)\\d{4}\\b");

    private static final Pattern PHONE_NA = Pattern.compile(
        "(?<![\\d\\-])(?:\\+?1[\\s\\-]?)?"
            + "(?:\\(\\d{3}\\)[\\s\\-]?|\\d{3}[\\s\\-])"
            + "\\d{3}[\\s\\-]\\d{4}(?![\\d\\-])");

    private static final Pattern CARD = Pattern.compile(
        "(?<!\\d)(?:\\d[ \\-]?){12,18}\\d(?!\\d)");

    private final StageId id;

    public RegexPiiDetector() { this(DEFAULT_ID); }

    public RegexPiiDetector(StageId id) {
        if (id == null)
            throw new IllegalArgumentException("id required");
        this.id = id;
    }

    @Override
    public StageId id() { return id; }

    @Override
    public RawSpanOutcome inspect(RawContent in) {
        try {
            String body = in.body();
            List<SpanFinding> findings = new ArrayList<>();
            scan(body, EMAIL,    "pii.email",    findings);
            scan(body, US_SSN,   "pii.us_ssn",   findings);
            scan(body, PHONE_NA, "pii.phone_na", findings);

            Matcher cm = CARD.matcher(body);
            while (cm.find()) {
                String digits = cm.group().replaceAll("[^0-9]", "");
                if (digits.length() >= 13 && digits.length() <= 19
                        && luhn(digits))
                    findings.add(new SpanFinding(
                        id, new ReasonCode("pii.card"),
                        new OriginalOffset(cm.start(), cm.end()),
                        FindingKind.PII));
            }
            return new RawSpanOutcome.Spans(findings);
        } catch (Throwable t) {
            return new RawSpanOutcome.StageFailed(
                new ReasonCode("pii_regex.error"));
        }
    }

    private void scan(String body, Pattern p, String reasonCode,
                      List<SpanFinding> out) {
        Matcher m = p.matcher(body);
        while (m.find())
            out.add(new SpanFinding(
                id, new ReasonCode(reasonCode),
                new OriginalOffset(m.start(), m.end()),
                FindingKind.PII));
    }

    /** The standard Luhn checksum over a digit-only string. */
    private static boolean luhn(String digits) {
        int sum = 0;
        boolean alt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (alt) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alt = !alt;
        }
        return sum % 10 == 0;
    }
}
