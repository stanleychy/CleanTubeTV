# Security policy

## Report a vulnerability

Do not open a public issue for a security problem.

1. Go to the **Security** tab of this repository.
2. Select **Report a vulnerability**.
3. Write the steps that cause the problem, the effect, and the app version.

The maintainer replies in 7 days or less. The maintainer tells you when the fix is released.

## Scope

These problems are in scope:

- A leak of the OAuth refresh token or the access token.
- A request that sends account data to a host that is not a Google or YouTube host.
- Code that the app downloads and runs at run time.
- A problem in the release pipeline, the signing key handling or the dependency verification.
- A link or an intent from another app that makes CleanTube TV do an unwanted action.

These problems are not in scope:

- Playback stops because YouTube changed its API. Open a normal issue for this.
- Problems in YouTube, Google sign-in or SponsorBlock servers.

## Release integrity

- The Release workflow signs each APK. The SHA-256 of the signing certificate is:

  `31:4E:13:3E:1B:EA:68:5A:8C:B9:EA:D2:0E:8C:B0:F5:67:3B:64:86:D5:0F:7C:EB:6D:62:0A:97:2A:D5:D7:8D`

- Each release has a `SHA256SUMS` file and a GitHub build provenance attestation. Refer to "Install a release" in `README.md`.
