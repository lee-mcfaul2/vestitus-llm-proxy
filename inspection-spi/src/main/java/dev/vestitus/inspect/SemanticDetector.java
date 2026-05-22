package dev.vestitus.inspect;

import java.util.Set;

/**
 * A {@link Detector} that inspects a (possibly transformed) {@link
 * NormalizedView} and may raise a verdict — but never with an offset.
 * {@code declaredActions()} is the static authority bound checked by the
 * assembly validator (design-spec §7 rule 5) and RE-CHECKED by the executor: a
 * Verdict whose action is not declared is treated as a StageFailed (a detector
 * cannot widen its authority at runtime past what it declared at assembly). An
 * implementation MUST NOT throw — any failure is a {@link
 * SemanticOutcome.StageFailed}.
 */
public non-sealed interface SemanticDetector extends Detector {

    /** The actions this detector is permitted to raise (BLOCK and/or INCIDENT). */
    Set<SemanticAction> declaredActions();

    /** Inspects {@code view}; never throws. */
    SemanticOutcome inspect(NormalizedView view);
}
