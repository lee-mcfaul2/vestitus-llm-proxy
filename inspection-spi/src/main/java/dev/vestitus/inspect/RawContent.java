package dev.vestitus.inspect;

import java.util.Objects;

/**
 * One unit of content fed to the pipeline, never mutated by the executor.
 * gateway-core decides how to split a structured MCP response into RawContent
 * values (typically one per authorized field); the SPI sees one at a time.
 * {@code body} may be empty (an authorized field can legitimately be "").
 */
public record RawContent(String body, ContentKind kind) {
    public RawContent {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(kind, "kind");
    }
}
