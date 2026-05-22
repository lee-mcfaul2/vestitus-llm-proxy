package dev.vestitus.inspect;

/**
 * A {@link Transformer}'s declared offset behaviour. SPAN_PRESERVING views
 * keep original offsets; LOSSY views are for semantic inspection only.
 */
public enum SpanFidelity { SPAN_PRESERVING, LOSSY }
