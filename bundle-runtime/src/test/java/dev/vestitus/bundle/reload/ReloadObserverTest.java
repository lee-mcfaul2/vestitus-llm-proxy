package dev.vestitus.bundle.reload;

import dev.vestitus.trust.BundleVersion;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ReloadObserverTest {

    @Test
    void noOpObserverSwallowsEveryCallbackWithoutThrowing() {
        ReloadObserver o = new NoOpReloadObserver();
        assertDoesNotThrow(() -> {
            o.onFetchUnreachable("net");
            o.onVerifyRejected("sig");
            o.onAggregateRejected("rollback");
            o.onApplied(new BundleVersion(7), 3);
            o.onRetainedLastGood("within window");
            o.onFailedClosed("beyond window");
        });
    }

    @Test
    void aRecordingObserverCapturesTheLifecycleSequence() {
        List<String> log = new ArrayList<>();
        ReloadObserver o = new ReloadObserver() {
            public void onFetchUnreachable(String r) { log.add("fetch:" + r); }
            public void onVerifyRejected(String r) { log.add("verify:" + r); }
            public void onAggregateRejected(String r) { log.add("agg:" + r); }
            public void onApplied(BundleVersion v, int n) { log.add("applied:" + v.value() + ":" + n); }
            public void onRetainedLastGood(String r) { log.add("retained:" + r); }
            public void onFailedClosed(String r) { log.add("failed:" + r); }
        };
        o.onApplied(new BundleVersion(9), 2);
        o.onRetainedLastGood("ok");
        assertEquals(List.of("applied:9:2", "retained:ok"), log);
    }
}
