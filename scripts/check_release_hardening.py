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

# `owner/repo@<40-hex>` optionally followed by whitespace + `# v...` comment.
USES_RE = re.compile(r"uses:\s*(\S+)")
PINNED_USES_RE = re.compile(
    r"^[\w.-]+/[\w.-]+(?:/[\w.-]+)*@[0-9a-f]{40}(?:\s+#\s*v.*)?$"
)
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
    for raw_line in text.splitlines():
        stripped = raw_line.strip().lstrip("- ").strip()
        if not stripped.startswith("uses:"):
            continue
        m = USES_RE.match(stripped)
        if not m:
            failures.append("%s: malformed uses: line: %r" % (name, raw_line))
            continue
        ref = m.group(1)
        # Re-attach any trailing `# v...` comment for the full-line pattern.
        comment_idx = stripped.find("#")
        full = ref if comment_idx == -1 else stripped[len("uses:"):].strip()
        if not PINNED_USES_RE.match(full):
            failures.append(
                "%s: uses: not pinned to owner/repo@<40-hex>: %r"
                % (name, stripped)
            )


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
    needle = "attest-build-provenance@" + PROVENANCE_SHA
    if needle not in release_text and needle not in build_text:
        failures.append(
            "neither workflow references "
            "actions/attest-build-provenance@%s" % PROVENANCE_SHA
        )


def check_repo_guard_count(failures, release_text, build_text):
    guard = "github.repository == '%s'" % EXPECTED_REPO
    count = release_text.count(guard) + build_text.count(guard)
    if count < 4:
        failures.append(
            "expected >=4 \"%s\" guards across the two workflows, found %d"
            % (guard, count)
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

    for path in (RELEASE_WF, BUILD_CONTAINER_WF):
        if not os.path.isfile(path):
            failures.append("missing required workflow file: %s" % path)
    if failures:
        for f in failures:
            print("FAIL: %s" % f)
        print("\n%d invariant violation(s) found." % len(failures))
        return 1

    release_text = read(RELEASE_WF)
    build_text = read(BUILD_CONTAINER_WF)

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
