#!/usr/bin/env python3
"""Checks that the pull request title uses Conventional Commits.

Usage: PR_TITLE="fix(player): keep the speed after a seek" .github/scripts/check-commits.py

The repository allows only squash merges. The pull request title becomes the commit subject on main,
and the release notes (changelog.py) use that subject. The commits in the pull request are not checked,
because the squash merge removes them.
"""
import os
import re
import sys

TYPES = ("feat", "fix", "perf", "docs", "build", "ci", "refactor", "style", "test", "chore", "revert")
SUBJECT = re.compile(r"^(?:" + "|".join(TYPES) + r")(?:\([a-z0-9._/-]+\))?!?: \S.{0,99}$")


def main() -> int:
    title = os.environ.get("PR_TITLE", "")
    if SUBJECT.match(title):
        print(f"OK: {title}")
        return 0
    # GitHub annotation: shows the problem on the pull request.
    print(f"::error title=The pull request title is not a Conventional Commit::{title}")
    print()
    print("Change the title of the pull request. Use the format: type(scope): description")
    print("The scope and '!' (breaking change) are optional.")
    print("Types: " + ", ".join(TYPES))
    print("Example: fix(player): keep the speed after a seek")
    print("Refer to CONTRIBUTING.md.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
