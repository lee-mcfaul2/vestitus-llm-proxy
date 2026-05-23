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
 * A pure-library {@link RawSpanDetector} that finds high-confidence,
 * low-false-positive credential shapes — PEM private-key blocks, AWS
 * access-key IDs, GitHub tokens, Google API keys, Slack tokens, and
 * JWT-shaped triplets. Deterministic; no I/O. Satisfies the credential floor
 * of {@link dev.vestitus.inspect.InspectionPipeline}.
 *
 * <p><b>Coverage candor (design-spec §9.2).</b> Pattern detectors miss novel
 * credential formats — an AWS key without one of the listed prefixes, a
 * homegrown API key shape, a non-JWT bearer string, a Stripe-style
 * {@code sk_live_*} key. This is the named residual risk; the mandatory floor
 * plus the gateway's fail-closed posture bound it. This detector is a
 * credible default, not a guarantee. A deployment wanting stronger coverage
 * supplies a better {@link RawSpanDetector}.
 */
public final class RegexCredentialDetector implements RawSpanDetector {

    private static final StageId DEFAULT_ID = new StageId("inspection.cred-regex");

    private static final List<NamedPattern> PATTERNS = List.of(
        new NamedPattern("cred.pem_private_key", Pattern.compile(
            "-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----")),
        new NamedPattern("cred.aws_access_key_id", Pattern.compile(
            "\\b(?:AKIA|ASIA|AGPA|AIDA|AROA|ANPA|ANVA)[A-Z0-9]{16}\\b")),
        new NamedPattern("cred.github_token", Pattern.compile(
            "\\bgh[opsur]_[A-Za-z0-9]{36,255}\\b")),
        new NamedPattern("cred.google_api_key", Pattern.compile(
            "(?<![A-Za-z0-9_\\-])AIza[0-9A-Za-z_\\-]{35}(?![A-Za-z0-9_\\-])")),
        new NamedPattern("cred.slack_token", Pattern.compile(
            "(?<![A-Za-z0-9\\-])xox[abprs]-[0-9A-Za-z\\-]{10,}(?![A-Za-z0-9\\-])")),
        new NamedPattern("cred.jwt", Pattern.compile(
            "(?<![A-Za-z0-9_\\-])eyJ[A-Za-z0-9_\\-]{10,}"
                + "\\.[A-Za-z0-9_\\-]{10,}"
                + "\\.[A-Za-z0-9_\\-]{10,}(?![A-Za-z0-9_\\-])"))
    );

    private final StageId id;

    /** Builds the detector with the default stage id {@code inspection.cred-regex}. */
    public RegexCredentialDetector() { this(DEFAULT_ID); }

    /** Builds the detector with an operator-supplied stage id. */
    public RegexCredentialDetector(StageId id) {
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
            for (NamedPattern np : PATTERNS) {
                Matcher m = np.pattern.matcher(body);
                while (m.find())
                    findings.add(new SpanFinding(
                        id,
                        new ReasonCode(np.reasonCode),
                        new OriginalOffset(m.start(), m.end()),
                        FindingKind.CREDENTIAL));
            }
            return new RawSpanOutcome.Spans(findings);
        } catch (Throwable t) {
            return new RawSpanOutcome.StageFailed(
                new ReasonCode("cred_regex.error"));
        }
    }

    private record NamedPattern(String reasonCode, Pattern pattern) {}
}
