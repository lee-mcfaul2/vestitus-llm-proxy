package dev.vestitus.bundle.reload;

import dev.vestitus.trust.Bundle;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FetchResultTest {

    private static Bundle b() {
        return new Bundle(new byte[]{1}, new byte[]{2}, "ref");
    }

    @Test
    void fetchedCarriesAnImmutableBundleList() {
        var f = new FetchResult.Fetched(List.of(b(), b()));
        assertEquals(2, f.bundles().size());
        assertThrows(UnsupportedOperationException.class,
            () -> f.bundles().add(b()));
    }

    @Test
    void fetchedRejectsNullOrEmptyList() {
        assertThrows(NullPointerException.class,
            () -> new FetchResult.Fetched(null));
        assertThrows(IllegalArgumentException.class,
            () -> new FetchResult.Fetched(List.of()));
    }

    @Test
    void unreachableRequiresNonBlankReason() {
        assertThrows(IllegalArgumentException.class,
            () -> new FetchResult.Unreachable("  "));
        assertEquals("dns", new FetchResult.Unreachable("dns").reason());
    }

    @Test
    void fetchResultIsSealedAndPatternMatchable() {
        FetchResult r = new FetchResult.Unreachable("x");
        String s = switch (r) {
            case FetchResult.Fetched ff -> "fetched:" + ff.bundles().size();
            case FetchResult.Unreachable uu -> "unreachable:" + uu.reason();
        };
        assertEquals("unreachable:x", s);
    }

    @Test
    void aFakeBundleSourceReturnsAFetchResult() {
        BundleSource src = () -> new FetchResult.Fetched(List.of(b()));
        assertInstanceOf(FetchResult.Fetched.class, src.fetch());
    }
}
