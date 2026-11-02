# Repository security settings

This document records the GitHub settings of this repository. Keep it up to date when you change a setting.

GitHub Free applies some settings only to public repositories. The tables show the status before and after the repository is public.

## Settings that are on now

| Setting | Value | Why |
|---|---|---|
| Actions: allowed actions | GitHub-owned actions only (`actions/*`, `github/*`) | A third-party action cannot run, even if a pull request adds one. |
| Actions: SHA pinning | Required | Each action must use a full commit SHA. A moved tag cannot change the code that runs. |
| Actions: default token | Read-only. Workflows cannot approve pull requests. | A workflow gets write access only when it asks for it (`permissions:`). |
| Environment `release` | Only `v*` tags and `main` can use it | The signing secrets are in this environment. A workflow on any other branch cannot read the key. |
| Signing secrets | In the `release` environment only | See above. |
| Merge methods | Squash only. The commit subject is the pull request title (with the pull request number). The commit body is empty. Auto-merge off. | One Conventional Commit on `main` for each pull request. The release notes use these subjects. |
| Delete branch after merge | On | |
| Commit sign-off for web edits | Required | |
| Dependabot alerts | On | Alerts for known vulnerabilities in dependencies and actions. |
| Dependabot security updates | On | Dependabot opens pull requests for security fixes. |
| Dependabot version updates | GitHub Actions pins only (`.github/dependabot.yml`) | Gradle updates need new checksums and a review (CONTRIBUTING.md). |

## Settings that start when the repository is public

| Setting | How to turn it on | Why |
|---|---|---|
| Ruleset "Protect main" | Import `.github/rulesets/protect-main.json` now (see below). It is enforced when the repository is public. | Pull request only, squash merge only, 1 code-owner approval after the last push, `build` and `conventional-commits` checks, linear history, no force push, no deletion. Admins can merge a pull request without the approval, but cannot push directly. |
| Ruleset "Protect release tags" | Import `.github/rulesets/protect-release-tags.json` now. It is enforced when the repository is public. | Only admins can create, move or delete `v*` tags. A `v*` tag starts the signed release. |
| Approval for workflow runs from outside contributors | **Settings > Actions > General > Approval for running fork pull request workflows from contributors**: select **Require approval for all external contributors**. | A fork cannot run a workflow without the approval of the maintainer. |
| Private vulnerability reporting | **Settings > Advanced Security > Private vulnerability reporting**: select **Enable**. | SECURITY.md tells reporters to use it. |
| Secret scanning and push protection | **Settings > Advanced Security > Secret Protection**: select **Enable**. Make sure that **Push protection** is on. | GitHub blocks a push that contains a known secret format. |
| Required reviewer for the `release` environment | **Settings > Environments > release > Required reviewers**: add the maintainer. | The maintainer must approve each run that can read the signing key. |
| Build provenance attestations | Automatic (`release.yml`) | Users can verify that GitHub Actions built the APK from this repository. |

## Import the rulesets before the repository is public

The GitHub API does not accept rulesets for a private repository on GitHub Free. The web UI accepts them, but does not enforce them until the repository is public.

1. Go to **Settings > Rules > Rulesets**.
2. Select **New ruleset > Import a ruleset**.
3. Select `.github/rulesets/protect-main.json`.
4. Examine the values. Select **Create**.
5. Do steps 2 to 4 again with `.github/rulesets/protect-release-tags.json`.

## Go public

1. Make sure that the history has no secrets. The history was examined on 2026-11-01: no tokens, keys, private IP addresses or personal e-mail addresses.
2. Go to **Settings > General > Danger Zone > Change visibility**. Select **Public**.
3. Immediately turn on the 4 settings in "Settings that start when the repository is public". Use the paths in the table.
4. Make sure that both rulesets show **Active** in **Settings > Rules > Rulesets**.
