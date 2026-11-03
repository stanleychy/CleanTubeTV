# Contributing to CleanTube TV

Thank you for your help. Read these rules before you open a pull request.

## Before you start

- For a large change, open an issue first. Describe the change and the reason.
- Do not add a new dependency without approval in an issue. This includes Gradle libraries, Gradle plugins, GitHub Actions and vendored files. The project keeps the supply chain small.
- Do not add analytics, crash reporting, auto-update, remote configuration or native libraries.
- Do not add code that downloads and runs code at run time.

## Make a change

1. Fork the repository and make a branch from `main`.
2. Make the change. Keep each pull request to one subject.
3. Build and lint the app:

   ```
   ./gradlew assembleDebug lintDebug
   ```

4. Test the change on an Android TV or Google TV device, or on an Android TV emulator. Write the device and the test steps in the pull request.
5. Open a pull request to `main`. Fill in the pull request template.

## Commit messages

Use [Conventional Commits](https://www.conventionalcommits.org/). Examples:

- `feat(player): add a sleep timer`
- `fix(history): end watch segments at seeks`
- `docs: describe the Settings tab`

The repository allows only squash merges. The pull request title becomes the one commit on `main`, so the title must use this format. The `conventional-commits` check examines the title. The commits in your branch can use any format, but Conventional Commits are recommended.

Allowed types: `feat`, `fix`, `perf`, `docs`, `build`, `ci`, `refactor`, `style`, `test`, `chore`, `revert`.

For a change that is not compatible with earlier versions, add `!` after the type or the scope in the title (`feat(player)!: remove the old quality menu`). The squash commit has no body, so a `BREAKING CHANGE:` line in a branch commit is not kept.

The Release workflow makes the release notes from these subjects. Each line names the author of the pull request with an @mention, and the notes have a list of all contributors. The maintainers (the users in `.github/CODEOWNERS`) are not named. Write the description for users: tell what changed, not how. To see the notes for the next release, run this command:

```
.github/scripts/changelog.py
```

## Documentation

Write instructions (README, guides, comments that tell the reader what to do) in [ASD-STE100 Simplified Technical English](https://www.asd-ste100.org/):

- Use short sentences. Use a maximum of 20 words in an instruction.
- Write one instruction in each sentence.
- Use the active voice and the imperative ("Run this command", not "This command should be run").

## Dependencies and checksums

Gradle checks each artifact against `gradle/verification-metadata.xml` and each version against the lockfiles. An artifact with a PGP signature must have a signature from a trusted key. The public keys are in `gradle/verification-keyring.keys`, so the build does not need a key server. An artifact without a signature, or with a key that no key server has (`ignored-keys`), must have the recorded SHA-256.

When a dependency change is approved:

1. Change the version in `gradle/libs.versions.toml`.
2. Update the lockfiles, the signatures, the checksums and the keyring:

   ```
   ./gradlew --write-verification-metadata pgp,sha256 --export-keys --write-locks :app:dependencies assembleDebug assembleRelease lintDebug
   ```

3. Examine the new entries in `gradle/verification-metadata.xml`. Make sure that each new artifact comes from the expected group. For a new `trusted-key`, make sure that the key belongs to the publisher of the group (for example, compare it with the key on the project page). For a new `ignored-key`, try the command again later: key servers are not always available.
4. Put the reason for each new artifact and each new key in the pull request.

## Review and merge

- Only the maintainer can merge to `main`. Each pull request needs the approval of the code owner and passed `build` and `conventional-commits` checks.
- GitHub Actions runs a workflow from a first-time contributor only after the maintainer approves it.
