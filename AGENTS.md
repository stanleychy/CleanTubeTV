# AGENTS.md: guide for AI coding agents

This file is for AI coding agents (for example Claude Code, Codex, Cursor, GitHub Copilot or Gemini CLI) and for the people who use them.

## For people: how to use this file

1. Clone the repository.
2. Open the repository folder in your AI agent. Most agents read `AGENTS.md` automatically. Claude Code reads `CLAUDE.md`, which loads this file.
3. Ask your questions. Examples:
   - "Explain how sign-in works, step by step."
   - "Why does playback use the VISIONOS client and not the TV client?"
   - "What does the app send to YouTube when I watch a video?"
   - "Show me where SponsorBlock segments are skipped."
   - "Build the debug APK and install it on my TV at 192.168.1.50."
   - "YouTube playback stopped working. Find the cause in the log."

CAUTION: An agent can run commands on your computer and on your TV (with `adb`). Read each command before you approve it.

CAUTION: Do not give your agent your Google password, your OAuth tokens or the contents of the app data folder. The agent does not need them.

## For agents: project summary

CleanTube TV is a small YouTube client for Android TV and Google TV. It shows no ads and skips sponsor segments (SponsorBlock). It is written in Kotlin with plain Android Views. It has no dependency-injection library, no Compose, no Leanback, no OkHttp, no Retrofit and no Gson.

Main goals, in order:

1. A small supply chain. Each new dependency is a risk.
2. Account safety. The app must look like the official TV app to YouTube for each request that is linked to the account.
3. A good experience with a TV remote (D-pad, OK, Back).

## Rules for agents

Obey these rules. Each rule has a reason in this file or in `CONTRIBUTING.md`.

- Do not add a dependency (Gradle library, Gradle plugin, GitHub Action, vendored file) unless the user approves it explicitly.
- Do not install system tools or SDK packages unless the user approves it explicitly.
- Do not add analytics, crash reporting, auto-update, remote configuration or native libraries.
- Do not add code that downloads and runs code (JavaScript, DEX) at run time.
- Never print, log or commit an OAuth token, a cookie, a keystore, a keystore password or `local.properties`.
- Never send account data or the OAuth token to a host other than `www.youtube.com` (InnerTube, OAuth) and `s.youtube.com` (watch-time pings). The allowed hosts are in `yt/Scraped.kt`.
- Send the OAuth token only from the TV client (`Clients.TV`). Never send it from a signed-out client.
- Check each value that comes from a YouTube page or reply with `yt/Scraped.kt` before you put it in a header, a JSON body or a URL. Add a new check there for a new value.
- Keep the visitor ID of the signed-in TV session separate from the visitor ID of signed-out playback (see "Account safety").
- Write commit messages with Conventional Commits.
- Write instructions in ASD-STE100 Simplified Technical English (short sentences, active voice, one instruction in each sentence).
- `main` is protected. Make a branch and open a pull request. Do not push to `main`.
- The pull request title becomes the commit on `main` (squash merge only). Write it as a Conventional Commit for users, because it goes in the release notes.

## Repository map

| Path | Content |
|---|---|
| `app/src/main/java/.../App.kt` | App-wide singletons: `Auth`, `InnerTube`, `Settings`, the I/O thread pool. |
| `auth/Auth.kt` | OAuth device flow (the code that the user enters at `youtube.com/activate`), token refresh. |
| `auth/TokenStore.kt` | Refresh token, encrypted with an AES-GCM key in the Android Keystore. |
| `net/Http.kt` | Small HTTP client on `HttpURLConnection`. |
| `yt/Clients.kt` | InnerTube client identities (TVHTML5 and VISIONOS). Update these values when YouTube changes its API. |
| `yt/TvAppInfo.kt` | Values read from `https://www.youtube.com/tv`: visitor ID, OAuth client, `signatureTimestamp`, client version. Cached for 24 hours. |
| `yt/Scraped.kt` | Format and host checks for all values from YouTube. |
| `yt/InnerTube.kt` | `browse`, `player` and `account/accounts_list` requests. Selects the visitor ID, version and token for each client. |
| `yt/Feed.kt`, `yt/FeedItemsMapper.kt` | Parse TV browse replies (Home, Subscriptions) into feed items. |
| `yt/PlayerInfo.kt` | Parse the player reply into stream formats. |
| `yt/DashManifest.kt` | Build a DASH MPD from the stream formats for ExoPlayer. |
| `yt/WatchReporter.kt` | Send watch-time pings, so that videos show in Watch History with a resume point. |
| `sponsor/SponsorBlock.kt` | Get skip segments with the SponsorBlock hash-prefix API (the full video ID is not sent). |
| `ui/MainActivity.kt` | Tabs: Home, Subscriptions, Account, Settings. |
| `ui/PlayerActivity.kt` | ExoPlayer (Media3), quality and speed controls, SponsorBlock skip, watch reports. |
| `ui/SignInActivity.kt` | Shows the activation code. |
| `ui/LinkActivity.kt` | Opens `youtube.com`, `youtu.be` and `vnd.youtube:` links, with the start time. |
| `remote/RemoteKeyService.kt` | Accessibility service that only filters keys. It opens the app with the remote YouTube button. |
| `settings/Settings.kt` | User settings in SharedPreferences. |
| `vendor/ejs/` | Vendored yt-dlp EJS solver. Not in the APK and not used now. Kept for a future fallback. |
| `docs/research/smarttube-architecture.md` | Research notes: SmartTube design, YouTube restrictions, design decisions. Read section 8 and 9 first. |
| `gradle/verification-metadata.xml`, `*.lockfile` | SHA-256 of each artifact and the locked versions. |
| `.github/workflows/` | `ci.yml` (build and lint), `conventional-commits.yml` (title and commit check) and `release.yml` (signed release APK with release notes). |
| `.github/scripts/changelog.py` | Makes the release notes from the Conventional Commits since the last `v*` tag. |
| `docs/repository-security.md` | A record of all GitHub repository settings, including the rulesets. The settings in GitHub are the source of truth. |

All `.kt` paths in the table, except `App.kt`, are under `app/src/main/java/io/github/stanleychy/cleantubetv/`.

## How the app works

### Sign-in

1. `TvAppInfoProvider` reads the OAuth client ID and secret of the official YouTube TV web app from `youtube.com/tv`.
2. `Auth.requestDeviceCode()` gets a user code. `SignInActivity` shows it.
3. The user enters the code at `youtube.com/activate`.
4. `Auth.poll()` gets a refresh token. `TokenStore` encrypts it.

The token scope is YouTube only. It gives no access to Gmail, Drive or other Google data.

### Feeds

`InnerTube.browse()` sends a signed-in TVHTML5 request. `Feed.kt` parses the reply.

### Playback

1. `InnerTube.player()` sends a signed-out VISIONOS request. VISIONOS gives direct stream URLs up to 4K HDR. It needs no signature or `n` challenge and no PoToken. Thus the app does not run YouTube player JavaScript.
2. `DashManifest` builds an MPD. ExoPlayer plays it.
3. `SponsorBlock` gets the segments. `PlayerActivity` skips them.

If VISIONOS stops working, the planned fallback is ANDROID_VR. The TV client needs the signature and `n` solver (the vendored EJS files).

### Watch history

`WatchReporter` sends one extra signed-in TVHTML5 player request, only to get the `playbackTracking` URLs for the account. `Scraped.trackingUrl()` accepts only https URLs on `s.youtube.com` or `www.youtube.com`. It sends the pings with the TV User-Agent and the token. At each seek it ends the watched segment. When a link from another app started the playback, the reports start at the first key press. The user can set history to off in Settings.

## Account safety

YouTube does not permit third-party clients. YouTube can limit playback in the app or revoke the token. The design decreases the signals that YouTube can see:

- Each request that is linked to the account uses one identity: TVHTML5, the TV User-Agent, the TV visitor ID and the current TV client version (read from `youtube.com/tv`).
- Signed-out playback (VISIONOS) uses its own visitor ID and never gets the token. YouTube cannot link it to the account by visitor ID.
- Watch-time pings report only the parts that the user watched.

Do not change these properties without a clear reason from the user. Examples of changes that increase the risk:

- Sending the TV visitor ID or the token with a VISIONOS request.
- A fixed, old client version.
- Bulk actions with the account token (for example, scripts that subscribe to many channels).

## Build, install and debug

Requirements: JDK 17 or later, Android SDK with `platforms;android-36`, `build-tools;36.0.0` and `platform-tools`.

```
./gradlew assembleDebug lintDebug
adb connect TV_IP:PORT
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s Auth InnerTube PlayerActivity MainActivity SponsorBlock WatchReporter ExoPlayerImpl
```

A debug build and a release build have different signatures. To change from one to the other on a device, uninstall the app first. This deletes the sign-in.

There are no unit tests now. Test each change on an Android TV device or emulator. Use the log tags above to see the result.

## Common tasks

| Symptom or task | Where to look |
|---|---|
| "Sign in to confirm you're not a bot" or `LOGIN_REQUIRED` in playback | `InnerTube.player()` (visitor ID retry), `yt/Clients.kt` (VISIONOS values). |
| "The page needs to be reloaded" in the TV player request | `TvAppInfoProvider.signatureTimestamp()`. |
| Feeds are empty after a YouTube change | `yt/Feed.kt`, `yt/FeedItemsMapper.kt`. Save a reply and compare the JSON structure. |
| Videos do not show in Watch History | `yt/WatchReporter.kt`. Look for `TV tracking: status=` and `ping OK` in the log. |
| A scraped value is rejected | The exception text names the value. Compare the live value with the pattern in `yt/Scraped.kt`. |
| Add a setting | `settings/Settings.kt`, the Settings tab in `ui/MainActivity.kt`, `res/layout/activity_main.xml`, `res/values/strings.xml`. |
| Make a release | Change `appVersion` in `app/build.gradle.kts`, merge, then push the tag `vX.Y.Z`. The Release workflow makes a draft release with notes from the commits. |
