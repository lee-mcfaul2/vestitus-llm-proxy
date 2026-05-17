#!/usr/bin/env python3
"""Deterministic supply-chain invariants gate for the cedar-cabi release pipeline.

Pure Python 3 standard library. Text/regex assertions over the raw contents of
the two committed workflow files. Does NOT parse YAML and does NOT compile
anything. Collects every violation, prints all of them, then exits non-zero if
any check failed.

Two-phase digest handling:
  * default               -> CEDAR_BUILD_IMAGE may pin either the literal
                             placeholder REPLACE_AFTER_FIRST_CONTAINER_PUBLISH
                             or a real 64-hex digest (pre-pin state).
  * --require-real-digest  -> CEDAR_BUILD_IMAGE must pin a real 64-lowercase-hex
                             digest and must NOT be the placeholder.
"""

import argparse
import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WF_DIR = os.path.join(REPO_ROOT, ".github", "workflows")
RELEASE_WF = os.path.join(WF_DIR, "cedar-cabi-release.yml")
BUILD_CONTAINER_WF = os.path.join(WF_DIR, "cedar-cabi-build-container.yml")

PLACEHOLDER_DIGEST = "REPLACE_AFTER_FIRST_CONTAINER_PUBLISH"
EXPECTED_REPO = "lee-mcfaul2/vestitus-llm-proxy"
PROVENANCE_SHA = "a2bbfa25375fe432b6a289bc6b6cd05ecd0c4c32"

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


def check_cedar_build_image(failures, text, require_real_digest):
    m = re.search(
        r"CEDAR_BUILD_IMAGE:\s*"
        r"ghcr\.io/lee-mcfaul2/cedar-cabi-build@sha256:(\S+)",
        text,
    )
    if not m:
        failures.append(
            "cedar-cabi-release.yml: CEDAR_BUILD_IMAGE must be "
            "ghcr.io/lee-mcfaul2/cedar-cabi-build@sha256:<digest>"
        )
        return
    digest = m.group(1)
    real = re.fullmatch(r"[0-9a-f]{64}", digest) is not None
    if require_real_digest:
        if digest == PLACEHOLDER_DIGEST:
            failures.append(
                "cedar-cabi-release.yml: CEDAR_BUILD_IMAGE still uses the "
                "placeholder %r; --require-real-digest demands a real "
                "@sha256:<64-hex> digest (resolved by Task 4)"
                % PLACEHOLDER_DIGEST
            )
        elif not real:
            failures.append(
                "cedar-cabi-release.yml: CEDAR_BUILD_IMAGE digest %r is not "
                "64 lowercase hex characters" % digest
            )
    else:
        if digest != PLACEHOLDER_DIGEST and not real:
            failures.append(
                "cedar-cabi-release.yml: CEDAR_BUILD_IMAGE digest %r is "
                "neither the placeholder %r nor a real 64-hex digest"
                % (digest, PLACEHOLDER_DIGEST)
            )


def check_build_job_block(failures, text):
    # Isolate the `build:` job block (from `  build:` up to the next
    # top-level two-space job key).
    m = re.search(r"\n  build:\n", text)
    if not m:
        failures.append("cedar-cabi-release.yml: missing 'build:' job")
        return
    start = m.start()
    nxt = re.search(r"\n  [A-Za-z][\w-]*:\n", text[start + 1:])
    block = text[start:] if not nxt else text[start: start + 1 + nxt.start()]

    if "contents: read" not in block:
        failures.append(
            "cedar-cabi-release.yml: build job must declare 'contents: read'"
        )
    if "# NO id-token" not in block:
        failures.append(
            "cedar-cabi-release.yml: build job must carry the "
            "'# NO id-token' marker"
        )
    if not re.search(r"matrix:\s*\{\s*instance:\s*\[1,\s*2\]\s*\}", block) \
            and not re.search(
                r"instance:\s*\n(?:\s*-\s*1\s*\n\s*-\s*2)", block):
        failures.append(
            "cedar-cabi-release.yml: build job must use matrix "
            "instance: [1, 2]"
        )
    # Absence assertion: the build job must NOT grant id-token / attestations.
    # The legitimate advisory comment is
    #   `contents: read            # NO id-token, NO attestations here ...`
    # which contains the words "id-token"/"attestations" but NOT the grant
    # substrings `id-token: write` / `attestations: write`, so this does not
    # false-positive on the real workflow.
    if "id-token: write" in block:
        failures.append(
            "cedar-cabi-release.yml: build job must NOT grant "
            "'id-token: write' (no OIDC in the build job by design)"
        )
    if "attestations: write" in block:
        failures.append(
            "cedar-cabi-release.yml: build job must NOT grant "
            "'attestations: write' (no attestations in the build job "
            "by design)"
        )


def check_attest_job_permissions(failures, text):
    m = re.search(r"\n  attest-sign-publish:\n", text)
    if not m:
        failures.append(
            "cedar-cabi-release.yml: missing 'attest-sign-publish:' job"
        )
        return
    start = m.start()
    nxt = re.search(r"\n  [A-Za-z][\w-]*:\n", text[start + 1:])
    block = text[start:] if not nxt else text[start: start + 1 + nxt.start()]
    if "id-token: write" not in block:
        failures.append(
            "cedar-cabi-release.yml: attest-sign-publish must have "
            "'id-token: write'"
        )
    if "attestations: write" not in block:
        failures.append(
            "cedar-cabi-release.yml: attest-sign-publish must have "
            "'attestations: write'"
        )


def check_simple_substrings(failures, release_text):
    if "refs/tags/cedar-cabi-v*" not in release_text:
        failures.append(
            "cedar-cabi-release.yml: missing tag-ref guard "
            "'refs/tags/cedar-cabi-v*'"
        )
    if "COSIGN_VERSION: v3.0.6" not in release_text:
        failures.append(
            "cedar-cabi-release.yml: COSIGN_VERSION must be pinned to v3.0.6"
        )
    if "cosign checksum mismatch" not in release_text:
        failures.append(
            "cedar-cabi-release.yml: missing fail-closed "
            "'cosign checksum mismatch' line"
        )


def check_provenance_sha(failures, release_text, build_text):
    # Both workflows independently invoke attest-build-provenance, so each
    # must pin it to the expected SHA on its own (per-file, not OR).
    needle = "attest-build-provenance@" + PROVENANCE_SHA
    for fname, text in (
        ("cedar-cabi-release.yml", release_text),
        ("cedar-cabi-build-container.yml", build_text),
    ):
        if needle not in text:
            failures.append(
                "%s: must reference "
                "actions/attest-build-provenance@%s" % (fname, PROVENANCE_SHA)
            )


def check_repo_guard_count(failures, release_text, build_text):
    # Count only *real conditional guards*: a line whose stripped form starts
    # with `if:` and contains the repo-equality expression. This ignores the
    # same string occurring inside comments or `echo`s, so deleting the actual
    # job-level guards (and thus running on forks) can no longer be masked.
    guard = "github.repository == '%s'" % EXPECTED_REPO
    count = 0
    for text in (release_text, build_text):
        for line in text.splitlines():
            stripped = line.strip()
            if stripped.startswith("if:") and guard in stripped:
                count += 1
    if count < 4:
        failures.append(
            "expected >=4 real `if:` \"%s\" job guards across the two "
            "workflows, found %d" % (guard, count)
        )


def main():
    parser = argparse.ArgumentParser(
        description="Supply-chain invariants gate for cedar-cabi workflows."
    )
    parser.add_argument(
        "--require-real-digest",
        action="store_true",
        help="Require CEDAR_BUILD_IMAGE to pin a real 64-hex sha256 digest "
        "(not the pre-pin placeholder).",
    )
    args = parser.parse_args()

    failures = []

    texts = {}
    for path in (RELEASE_WF, BUILD_CONTAINER_WF):
        if not os.path.isfile(path):
            failures.append("missing required workflow file: %s" % path)
            continue
        if not os.access(path, os.R_OK):
            failures.append("%s: file is not readable" % path)
            continue
        try:
            texts[path] = read(path)
        except OSError as exc:
            failures.append("%s: could not read file: %s" % (path, exc))
    if failures:
        for f in failures:
            print("FAIL: %s" % f)
        print("\n%d invariant violation(s) found." % len(failures))
        return 1

    release_text = texts[RELEASE_WF]
    build_text = texts[BUILD_CONTAINER_WF]

    check_uses_pinned(failures, "cedar-cabi-release.yml", release_text)
    check_uses_pinned(failures, "cedar-cabi-build-container.yml", build_text)

    check_no_pipe_to_shell(failures, "cedar-cabi-release.yml", release_text)
    check_no_pipe_to_shell(
        failures, "cedar-cabi-build-container.yml", build_text
    )

    check_cedar_build_image(failures, release_text, args.require_real_digest)
    check_build_job_block(failures, release_text)
    check_attest_job_permissions(failures, release_text)
    check_simple_substrings(failures, release_text)
    check_provenance_sha(failures, release_text, build_text)
    check_repo_guard_count(failures, release_text, build_text)

    if failures:
        for f in failures:
            print("FAIL: %s" % f)
        print("\n%d invariant violation(s) found." % len(failures))
        return 1

    print(
        "OK: all supply-chain invariants hold over "
        "cedar-cabi-release.yml and cedar-cabi-build-container.yml"
        + (" (real digest required)" if args.require_real_digest else "")
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
