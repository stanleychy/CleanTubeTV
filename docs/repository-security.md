# Repository security settings

This document records the GitHub settings of this repository. The settings in GitHub are the source of truth. Keep this document up to date when you change a setting.

To see the current values, use the GitHub CLI:

```
gh api repos/stanleychy/CleanTubeTV --jq '{allow_squash_merge,allow_merge_commit,allow_rebase_merge,delete_branch_on_merge,web_commit_signoff_required,security_and_analysis}'
gh api repos/stanleychy/CleanTubeTV/actions/permissions
gh api repos/stanleychy/CleanTubeTV/actions/permissions/workflow
gh api repos/stanleychy/CleanTubeTV/rulesets
gh api repos/stanleychy/CleanTubeTV/environments/release
```

## Settings

| Setting | Value | Why |
|---|---|---|
| Visibility | Public | Rulesets, secret scanning, code scanning and attestations are free for public repositories. |
| Actions: allowed actions | GitHub-owned actions only (`actions/*`, `github/*`) | A third-party action cannot run, even if a pull request adds one. |
| Actions: SHA pinning | Required | Each action must use a full commit SHA. A moved tag cannot change the code that runs. |
| Actions: default token | Read-only. Workflows cannot approve pull requests. | A workflow gets write access only when it asks for it (`permissions:`). |
| Actions: fork pull requests | Approval required for all external contributors | A fork cannot run a workflow without the approval of the maintainer. |
| Environment `release` | Only the `main` branch and `v*` tags can use it. The maintainer is a required reviewer. | The signing secrets are in this environment. A workflow on any other branch cannot read the key. Each run that can read the key needs an approval. |
| Signing secrets | In the `release` environment only. No repository secrets. | See above. |
| Merge methods | Squash only. The commit subject is the pull request title (with the pull request number). The commit body is empty. Auto-merge off. | One Conventional Commit on `main` for each pull request. The release notes use these subjects. |
| Delete branch after merge | On | |
| Commit sign-off for web edits | Required | |
| Dependabot alerts | On | Alerts for known vulnerabilities in dependencies and actions. |
| Dependabot security updates | On | Dependabot opens pull requests for security fixes. |
| Dependabot version updates | GitHub Actions pins only (`.github/dependabot.yml`) | Gradle updates need new checksums and a review (CONTRIBUTING.md). |
| Secret scanning and push protection | On | GitHub blocks a push that contains a known secret format. |
| Secret scanning: non-provider patterns | Off | Optional. It also finds generic secrets, for example a password in a properties file. |
| Private vulnerability reporting | On | SECURITY.md tells reporters to use it. |
| Code scanning | CodeQL default setup | Finds problems in the Kotlin code and the workflows on each pull request. |
| Build provenance attestations | Automatic (`release.yml`) | Users can verify that GitHub Actions built the APK from this repository. |
| Maintainer account | Two-factor authentication on | The account can change all settings above. |

## Rulesets

Both rulesets are active. They are in **Settings > Rules > Rulesets**. The repository has no copy of them: the ruleset in GitHub is the only record. Export a ruleset from its page (**...** > **Export**) when you want to compare or copy it.

### Protect main

Applies to the default branch.

- Pull request only. One approval from a code owner (`.github/CODEOWNERS`), after the last push. A new push dismisses the approvals. All review threads must be resolved.
- Squash merge only.
- Required status checks: `build` (`ci.yml`) and `conventional-commits` (`conventional-commits.yml`). The branch must be up to date with `main`.
- Linear history. No force push. No deletion.
- Bypass: repository admins, for pull requests only. See "One maintainer".

### Protect release tags

Applies to `refs/tags/v*`.

- No creation, update, deletion or force push.
- Bypass: repository admins, always. Only an admin can push a `v*` tag, and a `v*` tag starts the signed release.

## One maintainer

A person cannot approve their own pull request. With one maintainer, the admin bypass of "Protect main" is necessary to merge. The required reviewer of the `release` environment can approve their own run (`prevent_self_review` off) for the same reason.

When a second maintainer joins:

1. Remove the bypass actors from "Protect main".
2. Turn on **Prevent self-review** in the `release` environment.
3. Add the second maintainer to `.github/CODEOWNERS`.
