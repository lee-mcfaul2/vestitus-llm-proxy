package dev.vestitus.inspect;

import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Inv. 13 reachability property: no PipelineOutcome variant can transitively
 * reach raw content (RawContent / NormalizedView / SpanMap). Acting on a
 * matched secret or PII value is unrepresentable in the seam to gateway-core
 * because the raw value type is not reachable from the outcome type.
 */
class PipelineOutcomeNoLeakTest {

    private static final Set<Class<?>> FORBIDDEN =
        Set.of(RawContent.class, NormalizedView.class, SpanMap.class);

    @Test
    void noPipelineOutcomeVariantCanReachRawContent() {
        Set<Class<?>> visited = new HashSet<>();
        Deque<Class<?>> queue = new ArrayDeque<>();
        queue.add(PipelineOutcome.class);

        while (!queue.isEmpty()) {
            Class<?> c = queue.poll();
            if (c == null || !visited.add(c)) continue;
            assertFalse(FORBIDDEN.contains(c),
                "PipelineOutcome transitively reaches forbidden raw-content type "
                    + c.getName());
            if (c.isSealed())
                for (Class<?> sub : c.getPermittedSubclasses())
                    queue.add(sub);
            if (c.isRecord())
                for (RecordComponent rc : c.getRecordComponents())
                    addReachable(rc.getGenericType(), queue);
        }
    }

    private static void addReachable(Type t, Deque<Class<?>> queue) {
        switch (t) {
            case Class<?> cls -> queue.add(cls);
            case ParameterizedType pt -> {
                addReachable(pt.getRawType(), queue);
                for (Type arg : pt.getActualTypeArguments())
                    addReachable(arg, queue);
            }
            default -> { /* wildcards / type variables carry no concrete type */ }
        }
    }
}
