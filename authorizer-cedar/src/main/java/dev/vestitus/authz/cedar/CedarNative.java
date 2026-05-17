package dev.vestitus.authz.cedar;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Hand-written {@link java.lang.foreign} (FFM) binding over the {@code cedar-cabi}
 * C ABI. The attested {@code libcedar_cabi.so} is a classpath resource
 * ({@code /native/libcedar_cabi.so}); it is extracted once to a temp file and
 * {@link System#load}ed (in-process; no runtime code-load — ADR-001, spec §5.4).
 *
 * <p>C ABI (verbatim):
 * <pre>
 *   enum CedarResult /int32/ { Deny=0, Allow=1, Valid=2, Invalid=3, Error=-1 };
 *   void cedar_string_free(char *s);
 *   CedarResult cedar_is_authorized(const char *policies, const char *principal,
 *       const char *action, const char *resource, const char *context_json,
 *       const char *entities_json, char **out_diag);
 *   CedarResult cedar_validate(const char *schema_src, const char *policies_src,
 *       char **out_diag);
 * </pre>
 *
 * <p>All wrappers are fail-safe at the boundary: any non-null {@code *out_diag}
 * the library writes is ALWAYS freed via {@code cedar_string_free} in a
 * {@code finally}. This class never returns ALLOW on uncertainty; it returns
 * the raw native code and lets {@link CedarAuthorizer} apply the fail-closed
 * decision mapping.
 */
public final class CedarNative {

    /** Native call outcome: the raw {@code CedarResult} code + optional diag. */
    public record Result(int code, String diag) {}

    private static final class Holder {
        static final CedarNative INSTANCE = new CedarNative();
    }

    /** @return the process-wide singleton (loads the .so on first use). */
    public static CedarNative instance() {
        return Holder.INSTANCE;
    }

    private final MethodHandle isAuthorized;
    private final MethodHandle validate;
    private final MethodHandle stringFree;

    private CedarNative() {
        try {
            Path lib = extractLibrary();
            System.load(lib.toAbsolutePath().toString());
            Linker linker = Linker.nativeLinker();
            SymbolLookup lookup = SymbolLookup.loaderLookup();

            this.stringFree = linker.downcallHandle(
                lookup.find("cedar_string_free").orElseThrow(
                    () -> new IllegalStateException("symbol cedar_string_free not found")),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

            this.isAuthorized = linker.downcallHandle(
                lookup.find("cedar_is_authorized").orElseThrow(
                    () -> new IllegalStateException("symbol cedar_is_authorized not found")),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS));

            this.validate = linker.downcallHandle(
                lookup.find("cedar_validate").orElseThrow(
                    () -> new IllegalStateException("symbol cedar_validate not found")),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("failed to initialise cedar-cabi binding", t);
        }
    }

    private static Path extractLibrary() throws IOException {
        Path tmp = Files.createTempFile("libcedar_cabi", ".so");
        tmp.toFile().deleteOnExit();
        try (InputStream in = CedarNative.class.getResourceAsStream("/native/libcedar_cabi.so")) {
            if (in == null) {
                throw new IllegalStateException(
                    "vendored native lib /native/libcedar_cabi.so not on classpath");
            }
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        return tmp;
    }

    /**
     * Bind {@code cedar_is_authorized}. Returns the raw code (1=Allow, 0=Deny,
     * -1=Error) and the diagnostic string (NULL on a clean Allow). Any non-null
     * {@code *out_diag} is freed before returning.
     */
    public Result isAuthorized(String policies, String principal, String action,
                               String resource, String contextJson, String entitiesJson) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS); // char**
            outDiag.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
            int code = (int) isAuthorized.invokeExact(
                arena.allocateFrom(policies),
                arena.allocateFrom(principal),
                arena.allocateFrom(action),
                arena.allocateFrom(resource),
                arena.allocateFrom(contextJson),
                arena.allocateFrom(entitiesJson),
                outDiag);
            return new Result(code, readAndFreeDiag(outDiag));
        } catch (Throwable t) {
            // Boundary failure is itself fail-closed: synthesize an Error code.
            return new Result(-1, "native binding error: " + t.getClass().getSimpleName()
                + ": " + String.valueOf(t.getMessage()));
        }
    }

    /** Bind {@code cedar_validate} (ABI completeness; not on the auth hot path). */
    public Result validate(String schemaSrc, String policiesSrc) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
            outDiag.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
            int code = (int) validate.invokeExact(
                arena.allocateFrom(schemaSrc),
                arena.allocateFrom(policiesSrc),
                outDiag);
            return new Result(code, readAndFreeDiag(outDiag));
        } catch (Throwable t) {
            return new Result(-1, "native binding error: " + t.getClass().getSimpleName()
                + ": " + String.valueOf(t.getMessage()));
        }
    }

    /**
     * Read {@code *out_diag} (a Rust-owned C string) into a Java String and
     * ALWAYS free it via {@code cedar_string_free}. Returns null when the
     * library wrote NULL (e.g. clean Allow).
     */
    private String readAndFreeDiag(MemorySegment outDiag) throws Throwable {
        MemorySegment ptr = outDiag.get(ValueLayout.ADDRESS, 0);
        if (ptr == null || ptr.address() == 0) {
            return null;
        }
        try {
            // Reinterpret the opaque pointer as a NUL-terminated C string.
            MemorySegment cstr = ptr.reinterpret(Long.MAX_VALUE);
            return cstr.getString(0);
        } finally {
            stringFree.invokeExact(ptr); // null = safe no-op per ABI; we never pass null here
        }
    }
}
