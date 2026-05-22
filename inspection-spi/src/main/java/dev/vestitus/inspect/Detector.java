package dev.vestitus.inspect;

/** A {@link Stage} that inspects content and emits findings without mutating it. */
public sealed interface Detector extends Stage permits RawSpanDetector, SemanticDetector {}
