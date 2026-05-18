package dev.vestitus.bundle;

import dev.vestitus.trust.BundleVersion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;

/**
 * A single-{@code long} floor persisted to a configured {@link Path}
 * (ADR-003 D7: the floor "ratchets forward and survives restart").
 *
 * <p><b>Absent file == pristine</b> ({@link #currentFloor()} returns
 * {@link Optional#empty()} — a first-ever store). <b>Corrupt == fail-closed,
 * NOT empty:</b> any parse/IO failure or a negative/garbage value throws a
 * {@link VersionStoreException} (silently treating corrupt-as-empty would be a
 * rollback hole — the gate converts the throwable to a fail-closed Reject).
 * <b>Crash-safe write:</b> {@link #ratchet(BundleVersion)} writes a sibling
 * temp file then {@code Files.move(tmp, path, ATOMIC_MOVE, REPLACE_EXISTING)},
 * so a crash mid-write cannot corrupt or lower the persisted floor. <b>Monotone:
 * </b> the {@code Math.max} guarantees the floor never lowers.</p>
 */
public final class FileVersionFloorStore implements VersionFloorStore {

    private final Path path;

    public FileVersionFloorStore(Path path) {
        this.path = Objects.requireNonNull(path, "floor path required");
    }

    @Override
    public Optional<BundleVersion> currentFloor() {
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            String raw =
                Files.readString(path, StandardCharsets.UTF_8).trim();
            long value = Long.parseLong(raw);
            return Optional.of(new BundleVersion(value));
        } catch (IOException | IllegalArgumentException e) {
            throw new VersionStoreException(
                "persisted version floor is corrupt or unreadable: " + path,
                e);
        }
    }

    @Override
    public void ratchet(BundleVersion accepted) {
        Objects.requireNonNull(accepted, "accepted version required");
        try {
            long existing = 0L;
            if (Files.exists(path)) {
                existing = Long.parseLong(
                    Files.readString(path, StandardCharsets.UTF_8).trim());
            }
            long newValue = Math.max(existing, accepted.value());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(tmp, Long.toString(newValue),
                StandardCharsets.UTF_8);
            Files.move(tmp, path,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | IllegalArgumentException e) {
            throw new VersionStoreException(
                "could not persist version floor: " + path, e);
        }
    }
}
