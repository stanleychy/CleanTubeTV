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
   - "Explain how a phone sends a video to the TV with a TV code."
   - "Build the debug APK and install it on my TV at 192.168.1.50."
   - "YouTube playback stopped working. Find the cause in the log."

CAUTION: An agent can run commands on your computer and on your TV (with `adb`). Read each command before you approve it.

CAUTION: Do not give your agent your Google password, your OAuth tokens or the contents of the app data folder. The agent does not need them.

## For agents: project summary

CleanTube TV is a lean YouTube client for Android TV and Google TV, with fewer distractions: only the Home and Subscriptions feeds, one video at a time, no autoplay, no ads, and automatic skip of sponsor segments (SponsorBlock). It is written in Kotlin with plain Android Views. It has no dependency-injection library, no Compose, no Leanback, no OkHttp, no Retrofit and no Gson.

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
- Never send the OAuth token or a visitor ID on a Lounge request (`cast/LoungeApi.kt`). The Lounge session is signed out.
- Never print or log a lounge token, a screen ID, a pairing code or a Lounge session ID.
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
| `yt/Clients.kt` | InnerTube client identities (TVHTML5, VISIONOS and ANDROID_VR) and the sequence of the playback clients. Update these values when YouTube changes its API. |
| `yt/TvAppInfo.kt` | Values read from `https://www.youtube.com/tv`: visitor ID, OAuth client, `signatureTimestamp`, client version. Cached for 24 hours. |
| `yt/Scraped.kt` | Format and host checks for all values from YouTube. |
| `yt/InnerTube.kt` | `browse`, `search`, `player` and `account/accounts_list` requests. Selects the visitor ID, version and token for each client. `playback()` does the bot-check recovery and the client fallback. |
| `yt/SearchParser.kt` | Parse TV search replies (`lockupViewModel` items) into feed items. Only videos become items. |
| `yt/Feed.kt`, `yt/FeedItemsMapper.kt` | Parse TV browse replies (Home, Subscriptions) into feed items. |
| `yt/FeedAds.kt` | The rules that keep ads out of the feeds: ad containers and ad markers. |
| `yt/PlayerInfo.kt` | Parse the player reply into stream formats. |
| `yt/DashManifest.kt` | Build a DASH MPD from the stream formats for ExoPlayer. |
| `yt/WatchReporter.kt` | Send watch-time pings, so that videos show in Watch History with a resume point. |
| `sponsor/SponsorBlock.kt` | Get skip segments with the SponsorBlock hash-prefix API (the full video ID is not sent). |
| `ui/MainActivity.kt` | Tab bar: Home, Subscriptions, the search field and the Settings icon (gear) at the right. The Settings panel shows the account and the setting rows. |
| `ui/SearchField.kt` | The search field in the tab bar: on-screen keyboard, search action, focus with the D-pad. |
| `ui/BackOverride.kt` | Back callback for Android 16 and later. Back from the search results goes to Home. |
| `ui/PlayerActivity.kt` | ExoPlayer (Media3), quality and speed controls, SponsorBlock skip, watch reports. |
| `ui/QualityOptions.kt` | The rows of the quality menu: one for each resolution that the device can play. |
| `ui/SignInActivity.kt` | Shows the activation code. |
| `ui/LinkActivity.kt` | Opens `youtube.com`, `youtu.be` and `vnd.youtube:` links, with the start time. |
| `ui/CastActivity.kt` | Shows the pairing code for "Link with TV code" and the unlink button. |
| `cast/LoungeApi.kt` | Signed-out requests to `https://www.youtube.com/api/lounge/`: screen ID, lounge token, pairing code, `bc/bind` (session, long poll, events). |
| `cast/LoungeMessage.kt`, `cast/CastCommand.kt` | Parse the chunked `bind` reply into messages. Make the commands that the app obeys (play a video, play, pause, seek, stop, report state). |
| `cast/LoungeSession.kt` | One Lounge connection: the long-poll thread with reconnect and the send thread. |
| `cast/ScreenStore.kt` | The linked screen (screen ID, random device ID) in app-private SharedPreferences. |
| `cast/CastController.kt`, `cast/CastPlayback.kt` | Start and stop the session with the app, open `PlayerActivity` for a cast video, and report the player state to the phone. |
| `remote/RemoteKeyService.kt` | Accessibility service that only filters keys. It opens the app with the remote YouTube button. |
| `settings/Settings.kt` | User settings in SharedPreferences. |
| `vendor/ejs/` | Vendored yt-dlp EJS solver. Not in the APK and not used now. Kept for a future fallback. |
| `docs/research/smarttube-architecture.md` | Research notes: SmartTube design, YouTube restrictions, design decisions. Read section 8 and 9 first. |
| `gradle/verification-metadata.xml`, `gradle/verification-keyring.keys`, `*.lockfile` | Trusted PGP keys and SHA-256 of each artifact, the public keys, and the locked versions. |
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

YouTube puts ads in the feed as normal tiles in an `adSlotRenderer`. `FeedAds.kt` has the keys of the ad containers and the ad markers. The parser does not go into an ad container, and it drops a tile that has an ad marker. The rules use JSON keys only, not translated text. In a debug build, the `Feed` log tag shows the structure of each reply (keys and enum values only).

### Playback

1. `InnerTube.playback()` sends a signed-out VISIONOS request. VISIONOS gives direct stream URLs up to 4K HDR. It needs no signature or `n` challenge and no PoToken. Thus the app does not run YouTube player JavaScript.
2. `DashManifest` builds an MPD. ExoPlayer plays it.
3. `SponsorBlock` gets the segments. `PlayerActivity` skips them.

YouTube can reply `LOGIN_REQUIRED` ("Sign in to confirm you're not a bot") to a signed-out request. Then `InnerTube` discards the stored signed-out visitor ID, gets a new one from YouTube and sends the request again. It sends a maximum of three requests for each client.

If VISIONOS gives no stream, `InnerTube.playback()` tries ANDROID_VR, the second client in `Clients.PLAYBACK`. `PlayerInfo.client` records the client that gave the streams, and `PlayerActivity` downloads the streams with the User-Agent of that client. If no client gives a stream, the player shows the reason from YouTube.

ANDROID_VR is a weak fallback. Its player reply has direct stream URLs, but the stream server refuses some requests that have no PoToken (HTTP 403 on parts of the low-bitrate audio formats, verified 2026-11-13). No other client gives direct URLs without sign-in at this time: IOS and ANDROID give only SABR streams. The TV client needs the signature and `n` solver (the vendored EJS files).

### Search

1. The user selects the search field in the tab bar and pushes OK. `SearchField` opens the on-screen keyboard.
2. The search action of the keyboard starts the search. `InnerTube.search()` sends a signed-in TVHTML5 request, the same identity as the feeds. The official TV app does the same. Thus YouTube keeps the query in the search history of the account.
3. `SearchParser` reads the `lockupViewModel` items. It keeps only videos (no Shorts, channels or playlists). `Scraped` checks the video ID, the thumbnail URL and the continuation token.
4. `MainActivity` shows the results in the grid of the feeds. Back goes to Home.

The app does not store the queries and does not get search suggestions.

### Watch history

`WatchReporter` sends one extra signed-in TVHTML5 player request, only to get the `playbackTracking` URLs for the account. `Scraped.trackingUrl()` accepts only https URLs on `s.youtube.com` or `www.youtube.com`. It sends the pings with the TV User-Agent and the token. At each seek it ends the watched segment. When a link from another app started the playback, the reports start at the first key press. The user can set history to off in Settings.

### Cast from a phone (Link with TV code)

1. `CastActivity` calls `CastController.requestPairingCode()`. `LoungeApi` gets a screen ID (`pairing/generate_screen_id`), a lounge token (`pairing/get_lounge_token_batch`) and a 12-digit pairing code (`pairing/get_pairing_code`).
2. The user enters the code in the official YouTube app on the phone (**Settings > General > Watch on TV > Enter TV code**).
3. `LoungeSession` opens a session (`bc/bind`) and reads commands from a long poll. When a phone connects, `ScreenStore` keeps the screen, so the link stays after the app stops.
4. `CastCommand` accepts `setPlaylist` and `setVideo` (play the selected video), `play`, `pause`, `seekTo`, `stopVideo`, `getNowPlaying`, `loungeStatus` and `remoteConnected`. The app ignores all other commands (queue, next, previous, autoplay, volume).
5. `CastController` opens `PlayerActivity` for the video. `PlayerActivity` gives its player to `CastController.onPlayerReady()`. `CastPlayback` sends `nowPlaying` and `onStateChange` to the phone.

The session runs only while an activity of the app is visible. The app has no service and no boot receiver. DIAL and SSDP discovery (the cast button finds the TV by itself) are not implemented.

## Account safety

YouTube does not permit third-party clients. YouTube can limit playback in the app or revoke the token. The design decreases the signals that YouTube can see:

- Each request that is linked to the account uses one identity: TVHTML5, the TV User-Agent, the TV visitor ID and the current TV client version (read from `youtube.com/tv`).
- Signed-out playback (VISIONOS, ANDROID_VR) never gets the token. Each signed-out client uses its own visitor ID. YouTube cannot link it to the account by visitor ID.
- Watch-time pings report only the parts that the user watched.
- The Lounge session (cast from a phone) is signed out. It gets no token, no visitor ID and no cookie. It uses a random device ID. YouTube can link the screen to the account on the phone, because that phone connects to it.
- A cast video plays as a link-started playback (`EXTRA_FROM_LINK`): watch reports start at the first key press on the TV remote. The app does not use the credential (`ctt`) that the phone sends with a video.
- The app reports only cast videos to the phone. It does not report a video that the user starts on the TV.

Do not change these properties without a clear reason from the user. Examples of changes that increase the risk:

- Sending the TV visitor ID or the token with a signed-out request (VISIONOS, ANDROID_VR).
- A fixed, old client version.
- Bulk actions with the account token (for example, scripts that subscribe to many channels).
- Sending the token or a visitor ID on a Lounge request, or reporting each video that plays on the TV to the phone.

## Build, install and debug

Requirements: JDK 17 or later, Android SDK with `platforms;android-36`, `build-tools;36.0.0` and `platform-tools`.

```
./gradlew assembleDebug lintDebug
adb connect TV_IP:PORT
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s Auth InnerTube Feed PlayerActivity MainActivity SponsorBlock WatchReporter Lounge ExoPlayerImpl
```

A debug build and a release build have different signatures. To change from one to the other on a device, uninstall the app first. This deletes the sign-in.

There are no unit tests now. Test each change on an Android TV device or emulator. Use the log tags above to see the result.

## Common tasks

| Symptom or task | Where to look |
|---|---|
| "Sign in to confirm you're not a bot" or `LOGIN_REQUIRED` in playback | Look for `player:` lines with the `InnerTube` log tag. `InnerTube.signedOutPlayer()` (new visitor ID), `InnerTube.playback()` (client fallback), `yt/Clients.kt` (compare the client values with `INNERTUBE_CLIENTS` in yt-dlp). If each client fails, YouTube limits the network: wait some hours. |
| Playback stops with `ERROR_CODE_IO_BAD_HTTP_STATUS` after a fallback to ANDROID_VR | The stream server wants a PoToken for that format. See "Playback". |
| "The page needs to be reloaded" in the TV player request | `TvAppInfoProvider.signatureTimestamp()`. |
| Search shows "No videos found" for each query | `yt/SearchParser.kt`. Save a signed-out TVHTML5 `search` reply and compare the JSON structure. |
| Feeds are empty after a YouTube change | `yt/Feed.kt`, `yt/FeedItemsMapper.kt`. Save a reply and compare the JSON structure. |
| An ad shows as a video card in a feed | Install a debug build. Run `adb logcat -s Feed`. Find the key that marks the ad in the `reply:` lines. Add the key to `yt/FeedAds.kt`. |
| Videos do not show in Watch History | `yt/WatchReporter.kt`. Look for `TV tracking: status=` and `ping OK` in the log. |
| A scraped value is rejected | The exception text names the value. Compare the live value with the pattern in `yt/Scraped.kt`. |
| The phone does not connect, or a cast video does not start | `cast/LoungeSession.kt`, `cast/LoungeApi.kt`. Look for `session open`, `command` and `connection lost` with the `Lounge` tag. |
| The phone shows a wrong player state | `cast/CastPlayback.kt` (the `nowPlaying` and `onStateChange` values). |
| Add a setting | `settings/Settings.kt`, the Settings panel in `ui/MainActivity.kt`, `res/layout/activity_main.xml`, `res/values/strings.xml`. |
| Make a release | Change `appVersion` in `app/build.gradle.kts`, merge, then push the tag `vX.Y.Z`. The Release workflow makes a draft release with notes from the commits. |
