#!/usr/bin/env python3
"""Makes release notes from Conventional Commits.

Usage:
  .github/scripts/changelog.py [--from REF] [--to REF] [--version X.Y.Z] [--repo OWNER/REPO]

--from  The last commit that is not in the release. Default: the last "v*" tag before --to.
        With no earlier tag, all commits are used.
--to    The last commit in the release. Default: HEAD.
--no-authors  Do not ask GitHub for the authors (for example, with no network).

With --repo, each line names its author with an @mention, so that GitHub notifies the contributor.
The maintainers (the users in .github/CODEOWNERS) are not named: they make most of the changes.
The author of a pull request comes from the pull request. The author of a commit with no pull request
comes from the commit. The script uses the GitHub CLI (gh) for this. In GitHub Actions, set GH_TOKEN.
If gh cannot get an author, the line has no author.

The script uses only git and the Python standard library. Merge commits are not shown.
Run it on your computer to see the notes before you push a tag.
"""
import argparse
import re
import subprocess
import sys

# Commit type to section title. The order of this list is the order in the notes.
SECTIONS = [
    ("feat", "Features"),
    ("fix", "Fixes"),
    ("perf", "Performance"),
    ("docs", "Documentation"),
    ("build", "Build and CI"),
    ("ci", "Build and CI"),
    ("refactor", "Other changes"),
    ("style", "Other changes"),
    ("test", "Other changes"),
    ("chore", "Other changes"),
    ("revert", "Other changes"),
]
SUBJECT = re.compile(r"^(?P<type>[a-z]+)(?:\((?P<scope>[a-z0-9._/-]+)\))?(?P<bang>!)?: (?P<desc>.+)$")
BREAKING_FOOTER = re.compile(r"^BREAKING[ -]CHANGE: (.+)$", re.MULTILINE)
PR_NUMBER = re.compile(r"\s\(#(\d+)\)$")
MENTION = re.compile(r"(?<![\w`])@([A-Za-z0-9-]+)")
# A GitHub login. A value from the API must match it before the script writes it in the notes.
LOGIN = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})(?:\[bot\])?$")


def git(*args: str) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def previous_tag(ref: str):
    try:
        return git("describe", "--tags", "--abbrev=0", "--match", "v*", f"{ref}^").strip() or None
    except subprocess.CalledProcessError:
        return None  # No earlier tag, or ref is the first commit.


def maintainers(path: str = ".github/CODEOWNERS") -> set:
    """The users in CODEOWNERS, in lower case. Teams ("@org/team") are not included."""
    try:
        text = open(path, encoding="utf-8").read()
    except OSError:
        return set()
    users = set()
    for line in text.splitlines():
        for word in line.split("#", 1)[0].split()[1:]:
            if word.startswith("@") and "/" not in word:
                users.add(word[1:].lower())
    return users


def gh_login(path: str, field: str):
    """The login in field (for example ".user.login") of the GitHub API response, or None."""
    try:
        out = subprocess.run(
            ["gh", "api", path, "--jq", field], check=True, capture_output=True, text=True, timeout=30,
        ).stdout.strip()
    except (OSError, subprocess.SubprocessError):
        return None
    return out if LOGIN.match(out) else None


def author(repo: str, sha: str, pr, cache: dict):
    key = f"pr{pr}" if pr else sha
    if key not in cache:
        cache[key] = (
            gh_login(f"repos/{repo}/pulls/{pr}", ".user.login") if pr
            else gh_login(f"repos/{repo}/commits/{sha}", ".author.login // empty")
        )
    login = cache[key]
    # "dependabot[bot]" shows as "@dependabot" in GitHub release notes.
    return login.removesuffix("[bot]") if login else None


def clean(text: str) -> str:
    # Do not notify GitHub users from the release notes: put "@name" in code format.
    return MENTION.sub(r"`@\1`", text)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--from", dest="since")
    ap.add_argument("--to", default="HEAD")
    ap.add_argument("--version")
    ap.add_argument("--repo")
    ap.add_argument("--no-authors", action="store_true")
    a = ap.parse_args()
    cache: dict = {}
    contributors: list[str] = []
    skip = maintainers()

    since = a.since or previous_tag(a.to)
    rev = f"{since}..{a.to}" if since else a.to
    raw = git("log", "--no-merges", "--format=%H%x1f%s%x1f%b%x1e", rev)

    groups: dict[str, list[str]] = {}
    breaking: list[str] = []
    for record in filter(None, (r.strip("\n") for r in raw.split("\x1e"))):
        sha, subject, body = (record.split("\x1f") + ["", ""])[:3]
        link = f"[`{sha[:7]}`](https://github.com/{a.repo}/commit/{sha})" if a.repo else f"`{sha[:7]}`"
        pr = PR_NUMBER.search(subject)
        if pr:
            subject = subject[: pr.start()]
            if a.repo:
                link += f" ([#{pr.group(1)}](https://github.com/{a.repo}/pull/{pr.group(1)}))"
        login = author(a.repo, sha, pr.group(1) if pr else None, cache) if a.repo and not a.no_authors else None
        if login and login.lower() not in skip:
            link += f" by @{login}"
            if login not in contributors:
                contributors.append(login)
        m = SUBJECT.match(subject)
        if m:
            scope = f"**{m['scope']}:** " if m["scope"] else ""
            line = f"- {scope}{clean(m['desc'])} {link}"
            section = dict(SECTIONS).get(m["type"], "Other changes")
            footer = BREAKING_FOOTER.search(body)
            if m["bang"] or footer:
                breaking.append(f"- {scope}{clean(footer.group(1) if footer else m['desc'])} {link}")
        else:
            line = f"- {clean(subject)} {link}"
            section = "Other changes"
        groups.setdefault(section, []).append(line)

    title = f"CleanTube TV {a.version}" if a.version else "Changes"
    out = [f"## {title}", ""]
    if breaking:
        out += ["### Breaking changes", "", *breaking, ""]
    for section in dict.fromkeys(title for _, title in SECTIONS):
        if groups.get(section):
            out += [f"### {section}", "", *groups[section], ""]
    if not groups:
        out += ["No changes.", ""]
    if contributors:
        out += ["### Contributors", "", "Thank you: " + ", ".join(f"@{c}" for c in contributors), ""]
    if a.repo and since:
        out += [f"**Full list of changes:** https://github.com/{a.repo}/compare/{since}...{a.to}", ""]
    sys.stdout.write("\n".join(out))
    return 0


if __name__ == "__main__":
    sys.exit(main())
