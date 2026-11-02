# SmartTube architecture and supply-chain review

Date of review: 2026-11-01.
Source: shallow clone of SmartTube `8759c6d`, MediaServiceCore `40fec84`, SharedModules `799cfae`.
We read the source only. We did not build or run it.

## 1. Module layout

| Module | Function |
|---|---|
| `smarttubetv` | Application. Leanback activities and fragments. |
| `common` | MVP presenters, playback controllers (SponsorBlock, auto frame rate, chat), ExoPlayer setup, preferences, backup, DIAL server. |
| `leanbackassistant` | Home-screen channels and global search provider. |
| MediaServiceCore `youtubeapi` | All network calls: InnerTube, OAuth, PoToken, n-sig solver, DASH/HLS builders, SponsorBlock, DeArrow. |
| MediaServiceCore `mediaserviceinterfaces` | Service interfaces and data models. |
| SharedModules `sharedutils`, `appupdatechecker2`, `j2v8`, `commons-io` | Utilities, in-app updater, vendored V8 engine, patched commons-io. |
| Forks in tree | Amazon ExoPlayer port 2.10.6 (with a custom SABR module), Leanback 1.0.0, Fragment 1.1.0, chatkit, Slidr, file picker, double-tap player view. |

Build: Gradle 7.5, AGP 7.4.2, Kotlin 1.8.10, Java 8, minSdk 17, compileSdk 34.

## 2. How SmartTube gets video

1. It scrapes `https://www.youtube.com/tv` to find the TV player JavaScript URL and the OAuth client credentials.
2. It sends InnerTube requests (`youtubei/v1/player`, `browse`, `next`, `search`) with a spoofed client context. Only the TV client (`TVHTML5`) carries the OAuth token.
3. The player-request fallback order is: TV, VISIONOS, TV_DOWNGRADED, WEB, WEB_EMBED, WEB_SAFARI, IOS, MWEB, ANDROID_VR.
4. It solves the signature and `n` challenges with a port of the yt-dlp EJS solver. The solver runs in J2V8 (embedded V8, native library).
5. It builds a DASH MPD locally from `streamingData.adaptiveFormats`. It uses HLS for live streams and a custom SABR source when YouTube forces SABR.
6. PoToken: the TV client uses a session PoToken from `tv_config`. Web clients use a BotGuard token made in a WebView (code from NewPipe).

## 3. Sign-in

- Flow: YouTube TV device flow. `POST https://www.youtube.com/o/oauth2/device/code`, then the user types the code at `youtube.com/activate`, then the app polls `https://www.youtube.com/o/oauth2/token`.
- Client ID and secret: scraped at run time from the YouTube TV web app JavaScript.
- Scope: `http://gdata.youtube.com https://www.googleapis.com/auth/youtube-paid-content`.
- Storage: the refresh token is in plain SharedPreferences, with no encryption.
- Note: since late 2024, YouTube accepts these OAuth tokens only on TV InnerTube clients. A custom Google Cloud OAuth client cannot get playable streams.

## 4. Ad removal

SmartTube has no ad-filter code. Ads do not show because the app reads only `streamingData`, captions and metadata, and draws its own UI. It never parses or shows the ad payloads.

## 5. SponsorBlock

- Endpoint: `GET https://sponsor.ajay.app/api/skipSegments?videoID=<id>&categories=[...]`.
- SmartTube sends the full video ID. It does not use the privacy endpoint (`/api/skipSegments/<sha256 prefix>`).
- Categories: sponsor, intro, outro, selfpromo, interaction, music_offtopic, preview, poi_highlight, filler.

## 6. Supply-chain findings

| Finding | Risk |
|---|---|
| No `verification-metadata.xml`, no lockfiles, no wrapper `distributionSha256Sum` | Changed artifacts are not detected. |
| `jitpack.io` is a global repository; `jcenter()` still referenced | Mutable artifacts and dependency confusion. |
| Prebuilt `libj2v8.so` (4 ABIs) with no documented provenance | Unknown native binary runs untrusted YouTube JavaScript. |
| JS solver can download from GitHub at run time with no hash check | Remote code execution path. |
| Remote `constants.json` from a mutable GitHub "latest" tag | Remote configuration trust point. |
| In-app updater installs APKs from URLs in a remote JSON, with no checksum | Update channel takeover. |
| Firebase Crashlytics in release flavors; YouTube stats pings | Telemetry. |
| About 9 forked or vendored libraries in tree | Large audit surface. |
| Late 2025 breach: developer machine infected, signing key compromised, builds 30.43 to 30.47 shipped an injected `libalphasdk.so` | Proof that binary releases were the attack vector. Builds from source were safe. |

## 7. Platform facts for Sony Bravia 8 (XR80)

- SoC: MediaTek Pentonic 1000. 6 GB RAM.
- Verified on the test TV (2026-11-01): `ro.product.model` is `BRAVIA 4K AE2`, Android 14 (API 34).
- CPU ABI of the userland: `armeabi-v7a,armeabi` (32-bit only). The app has no native libraries, so this has no effect.
- Hardware video decoders: `c2.mtk.vp9.decoder`, `c2.mtk.av1.decoder`, `c2.mtk.hevc.decoder`, `c2.mtk.avc.decoder`, and Dolby Vision AV1 (`c2.mtk.dvav1.10.decoder`).
- Sideload: Developer options, then Wireless debugging. Pair with `adb pair <ip>:<pairing port> <code>`, then `adb connect <ip>:<connect port>`, then `adb install`. Port 5555 is closed.

## 8. YouTube restrictions (late 2026)

- `tv` (TVHTML5) and `android_vr` do not need a PoToken. Logged-in TV playback is not DRM-protected.
- YouTube forces SABR streaming more often. SmartTube issue #6293 (October 2026) shows a SABR reload loop.
- The signature and `n` challenges still need a JavaScript engine. If you do not solve `n`, YouTube throttles the stream.
- The TVHTML5 player request needs `playbackContext.contentPlaybackContext.signatureTimestamp`. Use the 8-digit value from `tv-player-ias-tcl.js` (for example `20753001`). The player URL is in `"player_url"` on `https://www.youtube.com/tv`. With no value, or with the 5-digit value, YouTube replies "UNPLAYABLE: The page needs to be reloaded" (verified 2026-11-01).
- Watch history needs the playbackTracking URLs from a signed-in TVHTML5 player response. The URLs from a signed-out VISIONOS response do not record the watch in the account.
- Expect breakage every few weeks. This is the main maintenance cost.

## 9. Design decisions for CleanTubeTV

1. No native libraries. Use the system WebView as the JavaScript engine.
2. Vendor the EJS solver scripts with a pinned SHA-256. Do not download code at run time.
3. Only `google()` and `mavenCentral()`, with content filters. Use Gradle dependency verification, lockfiles and a wrapper checksum.
4. Use the Android platform for HTTP (`HttpURLConnection`) and JSON (`org.json`). Do not add OkHttp, Retrofit or Gson.
5. Player: AndroidX Media3 (ExoPlayer and DASH modules only).
6. Encrypt the refresh token with an Android Keystore key.
7. Use the SponsorBlock hash-prefix endpoint.
8. No updater, no analytics, no crash reporting, no remote configuration.
9. Primary client: TVHTML5 with OAuth. Fallback: ANDROID_VR.

## Sources

- https://github.com/yuliskov/SmartTube
- https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide
- https://github.com/yt-dlp/yt-dlp/issues/11462
- https://github.com/yuliskov/SmartTube/issues/6293
- https://bleepingcomputer.com/news/security/smarttube-youtube-app-for-android-tv-breached-to-push-malicious-update
- https://github.com/androidx/media/releases
- https://developer.android.com/jetpack/androidx/releases/tv
