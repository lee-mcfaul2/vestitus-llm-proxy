package dev.vestitus.bundle.reload;

import dev.vestitus.authz.Authorizer;
import dev.vestitus.authz.AuthorizationDecision;
import dev.vestitus.authz.PolicyCompiler;
import dev.vestitus.bundle.NoRollbackGate;
import dev.vestitus.bundle.VersionFloorStore;
import dev.vestitus.mcpschema.*;
import dev.vestitus.trust.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Pure offline fakes shared by the orchestrator tests. */
final class ReloadFakes {

    private ReloadFakes() { }

    static McpSchema schema(String mcpId) {
        return new McpSchema(McpSchemaVersion.CURRENT, mcpId,
            List.of(new ToolDecl("t", "d",
                List.of(new FieldDecl("email", PiiType.NONE, new IamBinding("x"))))),
            new CedarRuleset("permit(principal == User::\"a\", action, resource);"),
            new CedarSchemaText("entity User;"));
    }

    static McpSchema selfPermissiveSchema(String mcpId) {
        return new McpSchema(McpSchemaVersion.CURRENT, mcpId,
            List.of(new ToolDecl("t", "d",
                List.of(new FieldDecl("a", PiiType.NONE, new IamBinding("x"))))),
            new CedarRuleset("permit(principal, action, resource);"),
            new CedarSchemaText("entity User;"));
    }

    static Bundle bundle(String ref) {
        return new Bundle(new byte[]{1}, new byte[]{2}, ref);
    }

    /** A digester that ignores the payload and returns preset schemas. */
    static BundleDigester digesterReturning(List<McpSchema> schemas) {
        return payload -> schemas;
    }

    static BundleDigester digesterThrowing() {
        return payload -> { throw new TrustException("digest blew up", null); };
    }

    /** Verifier that returns Verified(payload, subject, version) for any bundle. */
    static BundleVerifier verifierVerifying(String subject, long version) {
        return (b, cfg) -> VerificationOutcome.verified(
            b.payload(), subject, new BundleVersion(version));
    }

    static BundleVerifier verifierRejecting(String reason) {
        return (b, cfg) -> VerificationOutcome.rejected(reason);
    }

    /** A throwaway in-memory VersionFloorStore (pristine at start). */
    static final class MemFloorStore implements VersionFloorStore {
        private BundleVersion floor;
        @Override public Optional<BundleVersion> currentFloor() {
            return Optional.ofNullable(floor);
        }
        @Override public void ratchet(BundleVersion accepted) {
            if (floor == null || accepted.value() > floor.value()) {
                floor = accepted;
            }
        }
    }

    static NoRollbackGate freshGate() {
        return new NoRollbackGate(new MemFloorStore());
    }

    /** PolicyCompiler returning a stub Authorizer that allows action "read". */
    static PolicyCompiler stubCompiler() {
        return s -> req -> "read".equals(req.action())
            ? AuthorizationDecision.allow()
            : AuthorizationDecision.deny("stub-deny");
    }

    static PolicyCompiler compilerThrowing() {
        return s -> { throw new RuntimeException("compile boom"); };
    }

    /** A recording observer. */
    static final class RecordingObserver implements ReloadObserver {
        final List<String> events = new ArrayList<>();
        @Override public void onFetchUnreachable(String r) { events.add("unreachable:" + r); }
        @Override public void onVerifyRejected(String r) { events.add("verifyRejected:" + r); }
        @Override public void onAggregateRejected(String r) { events.add("aggRejected:" + r); }
        @Override public void onApplied(BundleVersion v, int n) { events.add("applied:" + v.value() + ":" + n); }
        @Override public void onRetainedLastGood(String r) { events.add("retained:" + r); }
        @Override public void onFailedClosed(String r) { events.add("failedClosed:" + r); }
    }

    /** Driver-controlled monotonic clock. */
    static final class FakeClock implements MonotonicClock {
        final AtomicLong t = new AtomicLong(0);
        @Override public long nanos() { return t.get(); }
        void advance(long ns) { t.addAndGet(ns); }
        void set(long ns) { t.set(ns); }
    }
}
