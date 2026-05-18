#!/usr/bin/env python3
"""Deterministic supply-chain invariants gate for the bundle-verifier-sigstore
integration-test workflow.

Pure Python 3 standard library. Text/regex assertions over the raw contents of
`.github/workflows/bundle-verifier-sigstore-itest.yml`. Does NOT parse YAML and
does NOT compile anything. Collects every violation, prints all of them, then
exits non-zero if any check failed.

This is a SIBLING of `scripts/check_release_hardening.py`: it does not import,
touch, or reference that script. The cedar-cabi release invariants stay
enforced by their own, unchanged script. The shared regexes / scan functions
below are reused verbatim (same wording, same anchoring) so the two gates
behave identically where they overlap.

There is no two-phase `@sha256:` container digest here: this workflow runs
cosign/gh directly on the runner (no hermetic build container), so unlike
`check_release_hardening.py` there is no `--require-real-digest` phase and no
placeholder token — deliberately N/A, stated not dropped.
"""

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WF_DIR = os.path.join(REPO_ROOT, ".github", "workflows")
ITEST_WF = os.path.join(WF_DIR, "bundle-verifier-sigstore-itest.yml")

EXPECTED_REPO = "lee-mcfaul2/vestitus-llm-proxy"
PROVENANCE_SHA = "a2bbfa25375fe432b6a289bc6b6cd05ecd0c4c32"
TAG_GUARD = "refs/tags/bundle-verifier-sigstore-v*"

# Find every `uses` *key* regardless of YAML style. The key must be anchored
# so we never match the literal word "uses" inside prose / echo / step names:
# it has to sit at start-of-line (optionally after a `- ` sequence marker) or
# directly after a flow-collection opener (`{`, `,`) — i.e. a real mapping key.
# Whitespace is permitted before the `:` (valid YAML: `uses : foo`). The ref
# token is captured greedily up to the first whitespace / `#` / `}` / `,` /
# quote, which terminates a ref in both block and flow styles.
USES_KEY_RE = re.compile(
    r"(?:^[ \t]*(?:-[ \t]+)?|[{,][ \t]*)uses[ \t]*:[ \t]*"
    r"(['\"]?)([^\s#}\],'\"]+)",
    re.MULTILINE,
)
# A correctly pinned ref: `owner/repo[/subpath...]@<40-lowercase-hex>`.
PINNED_REF_RE = re.compile(r"^[\w.-]+/[\w.-]+(?:/[\w.-]+)*@[0-9a-f]{40}$")
# After a pinned ref, only an optional `# v...` version comment may trail.
TRAILING_VERSION_RE = re.compile(r"^[ \t]*#[ \t]*v.*$")
# An actual pipe of a remote fetch into a shell, e.g. `curl https://x | bash`.
# Requires real command structure: whitespace + at least one non-pipe arg
# char after curl/wget before the `|`. This deliberately does NOT match the
# legitimate `curl -fsSL -o /tmp/cosign ...` (no pipe) nor a descriptive step
# name that merely contains the literal text "curl|bash".
PIPE_TO_SHELL_RE = re.compile(
    r"\b(?:curl|wget)\s+[^\n|]+\|\s*(?:sudo\s+)?(?:sh|bash)\b"
)


def read(path):
    with open(path, "r", encoding="utf-8") as fh:
        return fh.read()


def check_uses_pinned(failures, name, text):
    """Every `uses` key (block OR flow style, any whitespace) must reference
    `owner/repo[/subpath]@<40-lowercase-hex>` with at most a trailing
    `# v...` version comment. Full-text scan, not line classification, so
    flow-style and `uses : x` forms cannot slip past the pin gate."""
    found = False
    for m in USES_KEY_RE.finditer(text):
        found = True
        ref = m.group(2)
        rest = text[m.end():]
        if not PINNED_REF_RE.match(ref):
            failures.append(
                "%s: uses: not pinned to owner/repo@<40-lowercase-hex>: %r"
                % (name, ref)
            )
            continue
        # Whatever remains on this logical position up to end-of-line must be
        # nothing, a flow terminator, or a `# v...` comment. Anything else
        # (e.g. `@<hex> # main`, junk, branch noise) is rejected.
        line_tail = rest.split("\n", 1)[0]
        # If the ref was opened with a quote, a single matching closing quote
        # is part of the YAML scalar, not trailing junk -- consume it before
        # judging the remainder (e.g. `uses: "owner/repo@<hex>"`).
        quote = m.group(1)
        if quote and line_tail[:1] == quote:
            line_tail = line_tail[1:]
        # Strip a single flow terminator (`}` / `]` / `,`) so inline
        # `- { uses: owner/repo@<hex> }` is accepted.
        stripped_tail = line_tail.lstrip()
        if stripped_tail[:1] in ("}", "]", ","):
            continue
        if stripped_tail == "":
            continue
        if not TRAILING_VERSION_RE.match(line_tail):
            failures.append(
                "%s: uses: trailing junk after pinned ref (only `# v...` "
                "allowed): %r" % (name, (ref + line_tail))
            )
    if not found:
        failures.append("%s: no `uses:` action references found" % name)


def check_no_pipe_to_shell(failures, name, text):
    for i, line in enumerate(text.splitlines(), 1):
        if PIPE_TO_SHELL_RE.search(line):
            failures.append(
                "%s:%d: remote script piped into a shell: %r"
                % (name, i, line.strip())
            )


def isolate_job_block(text, job):
    """Isolate a `  <job>:` block (from `\\n  <job>:\\n` up to the next
    top-level two-space job key), mirroring check_release_hardening.py's
    check_build_job_block / check_attest_job_permissions block isolation."""
    m = re.search(r"\n  %s:\n" % re.escape(job), text)
    if not m:
        return None
    start = m.start()
    nxt = re.search(r"\n  [A-Za-z][\w-]*:\n", text[start + 1:])
    return text[start:] if not nxt else text[start: start + 1 + nxt.start()]


def check_cosign_pinning(failures, name, text):
    if "COSIGN_VERSION: v3.0.6" not in text:
        failures.append(
            "%s: COSIGN_VERSION must be pinned to v3.0.6" % name
        )
    if "cosign checksum mismatch" not in text:
        failures.append(
            "%s: missing fail-closed 'cosign checksum mismatch' line" % name
        )


def check_provenance_sha(failures, name, text):
    needle = "attest-build-provenance@" + PROVENANCE_SHA
    if needle not in text:
        failures.append(
            "%s: must reference actions/attest-build-provenance@%s"
            % (name, PROVENANCE_SHA)
        )


def check_tag_ref_guard(failures, name, text):
    if TAG_GUARD not in text:
        failures.append(
            "%s: missing tag-ref guard %r" % (name, TAG_GUARD)
        )


def check_repo_guard_count(failures, name, text):
    # Count only *real conditional guards*: a line whose stripped form starts
    # with `if:` and contains the repo-equality expression. This ignores the
    # same string occurring inside comments or `echo`s, so deleting the actual
    # job-level guards (and thus running on forks) can no longer be masked.
    # One per job: preflight, sign, verify -> >= 3.
    guard = "github.repository == '%s'" % EXPECTED_REPO
    count = 0
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith("if:") and guard in stripped:
            count += 1
    if count < 3:
        failures.append(
            "%s: expected >=3 real `if:` \"%s\" job guards "
            "(preflight, sign, verify), found %d" % (name, guard, count)
        )


def check_oidc_isolated_to_sign(failures, name, text):
    # The OIDC / attestation write grant must live ONLY in the `sign:` job.
    # The `verify:` job is read-only (id-token: read / attestations: read).
    sign = isolate_job_block(text, "sign")
    if sign is None:
        failures.append("%s: missing 'sign:' job" % name)
    else:
        if "id-token: write" not in sign:
            failures.append(
                "%s: sign job must grant 'id-token: write' "
                "(keyless cosign needs OIDC)" % name
            )
        if "attestations: write" not in sign:
            failures.append(
                "%s: sign job must grant 'attestations: write' "
                "(SLSA attest-build-provenance)" % name
            )
    verify = isolate_job_block(text, "verify")
    if verify is None:
        failures.append("%s: missing 'verify:' job" % name)
    else:
        if "id-token: write" in verify:
            failures.append(
                "%s: verify job must NOT grant 'id-token: write' "
                "(it is read-only by design)" % name
            )
        if "attestations: write" in verify:
            failures.append(
                "%s: verify job must NOT grant 'attestations: write' "
                "(it is read-only by design)" % name
            )


def main():
    failures = []

    if not os.path.isfile(ITEST_WF):
        print("FAIL: missing required workflow file: %s" % ITEST_WF)
        print("\n1 invariant violation(s) found.")
        return 1
    if not os.access(ITEST_WF, os.R_OK):
        print("FAIL: %s: file is not readable" % ITEST_WF)
        print("\n1 invariant violation(s) found.")
        return 1
    try:
        text = read(ITEST_WF)
    except OSError as exc:
        print("FAIL: %s: could not read file: %s" % (ITEST_WF, exc))
        print("\n1 invariant violation(s) found.")
        return 1

    name = "bundle-verifier-sigstore-itest.yml"
    check_uses_pinned(failures, name, text)
    check_no_pipe_to_shell(failures, name, text)
    check_cosign_pinning(failures, name, text)
    check_provenance_sha(failures, name, text)
    check_tag_ref_guard(failures, name, text)
    check_repo_guard_count(failures, name, text)
    check_oidc_isolated_to_sign(failures, name, text)

    if failures:
        for f in failures:
            print("FAIL: %s" % f)
        print("\n%d invariant violation(s) found." % len(failures))
        return 1

    print(
        "OK: all supply-chain invariants hold over "
        "bundle-verifier-sigstore-itest.yml"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
