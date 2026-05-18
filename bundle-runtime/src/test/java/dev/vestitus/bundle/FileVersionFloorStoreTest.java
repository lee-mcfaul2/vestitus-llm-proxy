package dev.vestitus.bundle;

import dev.vestitus.trust.BundleVersion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FileVersionFloorStoreTest {

    @Test
    void pristineNoFileYieldsEmpty(@TempDir Path dir) {
        Path p = dir.resolve("floor");
        FileVersionFloorStore s = new FileVersionFloorStore(p);
        assertTrue(s.currentFloor().isEmpty());
    }

    @Test
    void persistenceSurvivesRestart(@TempDir Path dir) {
        Path p = dir.resolve("floor");
        new FileVersionFloorStore(p).ratchet(new BundleVersion(5L));
        // A NEW instance on the same path == a process restart.
        FileVersionFloorStore reborn = new FileVersionFloorStore(p);
        assertEquals(5L, reborn.currentFloor().orElseThrow().value());
    }

    @Test
    void ratchetNeverLowersTheFloor(@TempDir Path dir) {
        Path p = dir.resolve("floor");
        FileVersionFloorStore s = new FileVersionFloorStore(p);
        s.ratchet(new BundleVersion(5L));
        s.ratchet(new BundleVersion(3L));
        assertEquals(5L, s.currentFloor().orElseThrow().value());
    }

    @Test
    void ratchetMovesTheFloorUp(@TempDir Path dir) {
        Path p = dir.resolve("floor");
        FileVersionFloorStore s = new FileVersionFloorStore(p);
        s.ratchet(new BundleVersion(5L));
        s.ratchet(new BundleVersion(9L));
        assertEquals(9L, s.currentFloor().orElseThrow().value());
    }

    @Test
    void corruptGarbageFloorThrowsNotEmpty(@TempDir Path dir) throws IOException {
        Path p = dir.resolve("floor");
        Files.writeString(p, "garbage");
        FileVersionFloorStore s = new FileVersionFloorStore(p);
        assertThrows(VersionStoreException.class, s::currentFloor);
    }

    @Test
    void emptyFloorFileThrowsNotEmpty(@TempDir Path dir) throws IOException {
        Path p = dir.resolve("floor");
        Files.writeString(p, "");
        FileVersionFloorStore s = new FileVersionFloorStore(p);
        assertThrows(VersionStoreException.class, s::currentFloor);
    }

    @Test
    void negativeFloorValueThrowsNotEmpty(@TempDir Path dir) throws IOException {
        Path p = dir.resolve("floor");
        Files.writeString(p, "-1");
        FileVersionFloorStore s = new FileVersionFloorStore(p);
        assertThrows(VersionStoreException.class, s::currentFloor);
    }

    @Test
    void ratchetLeavesNoLeftoverTempSibling(@TempDir Path dir)
            throws IOException {
        Path p = dir.resolve("floor");
        FileVersionFloorStore s = new FileVersionFloorStore(p);
        s.ratchet(new BundleVersion(5L));
        assertTrue(Files.exists(p));
        try (Stream<Path> entries = Files.list(dir)) {
            // exactly the floor file, no leftover *.tmp sibling
            assertEquals(1L, entries.filter(Files::isRegularFile).count());
        }
    }
}
