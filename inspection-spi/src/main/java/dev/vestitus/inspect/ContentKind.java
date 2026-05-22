package dev.vestitus.inspect;

/**
 * Metadata a detector may use to opt out of irrelevant content (a JSON-value
 * detector may skip when {@code kind != JSON_VALUE}). The SPI never parses the
 * body — this is a hint, not a parse directive.
 */
public enum ContentKind { TEXT, JSON_VALUE, FREEFORM }
