package dev.vestitus.inspect;

/** A detector observation. Sealed: a span-located finding or a semantic verdict. */
public sealed interface Finding permits SpanFinding, SemanticVerdict {
    StageId by();
    ReasonCode reason();
}
