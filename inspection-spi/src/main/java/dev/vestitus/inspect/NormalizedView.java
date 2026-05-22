package dev.vestitus.inspect;

import java.util.Objects;

/**
 * A possibly-transformed view of a {@link RawContent}. {@code body} is what a
 * {@link SemanticDetector} inspects; {@code source} is the untouched original;
 * {@code toOriginal} is the declared back-mapping. The identity view (body ==
 * source body, identity {@link SpanMap}) is what the executor hands a semantic
 * detector when no {@link Transformer} ran.
 */
public record NormalizedView(String body, RawContent source, SpanMap toOriginal) {
    public NormalizedView {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(toOriginal, "toOriginal");
    }

    /** The identity view over {@code content}: same body, identity SpanMap. */
    public static NormalizedView identityOf(RawContent content) {
        Objects.requireNonNull(content, "content");
        return new NormalizedView(content.body(), content, SpanMap.identity());
    }
}
