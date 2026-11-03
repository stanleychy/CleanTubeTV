# CleanTube TV

A small YouTube client for Android TV and Google TV. It shows no ads and skips sponsor segments.

The design is based on a review of SmartTube. Refer to `docs/research/smarttube-architecture.md`.

CleanTube TV is not affiliated with, endorsed by or sponsored by Google LLC or YouTube. YouTube is a trademark of Google LLC.

To explore the code with an AI coding agent, refer to [AGENTS.md](AGENTS.md).

## Features

- Sign in with your Google account (YouTube TV code at `youtube.com/activate`). The Account tab shows the name and e-mail address of the account.
- Home feed and Subscriptions feed.
- Playback with no ads, up to 4K HDR. The quality button shows the current quality, for example `2160p60 HDR`.
- Automatic skip of sponsor, self-promotion and interaction segments (SponsorBlock).
- A speed button on the player control bar. Each push selects the next speed in your list.
- Subtitles, audio tracks and live streams.
- Watch history and resume points in your YouTube account. You can set this to off.
- The YouTube button on the remote can open CleanTube TV.
- YouTube links (`youtube.com`, `youtu.be`, `vnd.youtube:`) from other apps can open in CleanTube TV.

## Supply-chain rules

- The app has no native libraries, no analytics, no crash reporter, no auto-updater and no remote configuration.
- The app does not download code at run time.
- Gradle uses only Google Maven and Maven Central.
- Gradle checks the PGP signature or the SHA-256 of each artifact (`gradle/verification-metadata.xml`, `gradle/verification-keyring.keys`) and each dependency version (`*.lockfile`).
- The Gradle wrapper has a pinned SHA-256 (`gradle/wrapper/gradle-wrapper.properties`).
- The runtime libraries are AndroidX Media3, AndroidX RecyclerView and their AndroidX, Guava and Kotlin dependencies.

## Install a release

Use this procedure if you do not want to build the app.

1. Enable debugging on the TV and pair your computer with the TV. Refer to "Enable debugging on the TV" and "Install the app on the TV", steps 1 and 2.
2. Go to the [Releases](https://github.com/stanleychy/CleanTubeTV/releases) page.
3. Download `CleanTubeTV-VERSION.apk` and `SHA256SUMS`.
4. Make sure that the checksum is correct. The command must show `OK`:

   ```
   sha256sum -c SHA256SUMS
   ```

5. Optional: make sure that GitHub Actions built the APK from this repository. This step needs the GitHub CLI (`gh`):

   ```
   gh attestation verify CleanTubeTV-VERSION.apk --repo stanleychy/CleanTubeTV
   ```

6. Optional: make sure that the APK has the correct signing certificate. Use `apksigner` from the Android SDK build tools:

   ```
   apksigner verify --print-certs CleanTubeTV-VERSION.apk
   ```

   The line `Signer #1 certificate SHA-256 digest` must show `314e133e1bea685a8cb9ead20e8cb0f5673b6486d50f7ceb6d620a972ad5d78d`.

7. Install the APK:

   ```
   adb install -r CleanTubeTV-VERSION.apk
   ```

CAUTION: A release APK and an APK that you built yourself have different signatures. To change from one to the other, uninstall the app first (`adb uninstall io.github.stanleychy.cleantubetv`). This deletes the sign-in and the settings.

## Requirements

- JDK 17 or later.
- Android SDK with `platforms;android-36`, `build-tools;36.0.0` and `platform-tools`.
- An Android TV or Google TV device on the same network as your computer.

## Build the app

1. Make sure that the file `local.properties` contains the path to your Android SDK. Example: `sdk.dir=/home/you/Android/Sdk`
2. Run this command:

   ```
   ./gradlew assembleDebug
   ```

3. Find the APK at `app/build/outputs/apk/debug/app-debug.apk`.

## Enable debugging on the TV

1. On the TV, go to **Settings > System > About**.
2. Select **Android TV OS build** 7 times. The TV shows "You are now a developer".
3. Go to **Settings > System > Developer options**.
4. Set **Wireless debugging** to on.
5. Open **Wireless debugging**. Write down the **IP address & Port**. This is the connect address.
6. Select **Pair device with pairing code**. Keep this screen open. It shows a 6-digit code and a pairing address. The pairing port is different from the connect port.

## Install the app on the TV

1. Pair your computer with the TV. Use the pairing address and the code from the TV screen. Do this step one time only:

   ```
   adb pair TV_IP:PAIRING_PORT CODE
   ```

2. Connect to the TV. Use the connect address:

   ```
   adb connect TV_IP:CONNECT_PORT
   ```

3. Install the APK:

   ```
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

4. On the TV, open **CleanTube TV** from the apps row.

## Sign in

1. In the app, go to the **Account** tab and press **OK**.
2. On your phone or computer, go to the URL that the TV shows.
3. Enter the code that the TV shows.
4. Approve the access. The TV goes back to the main screen.

## Settings

Go to the **Settings** tab. Select a row and push **OK** to change it.

| Setting | Function | Default |
|---|---|---|
| Watch history | When on, the app sends watch reports to YouTube. The video then shows in your Watch History. | On |
| Speed button | The speeds for the speed button on the player. Select one or more. | 1x, 1.5x, 2x |
| Audio language | The audio track to use when a video has more than one. "Original" selects the original audio of the video. | Original |
| YouTube button opens CleanTube | The remote key that opens CleanTube TV. Refer to "Use the YouTube button". | Not set |
| Subtitles | The subtitle language. If the video has no subtitles in this language, the app uses YouTube auto-translation. "Off" shows no subtitles. You can always use the CC button on the player. | Off |

## Use the YouTube button

The app has an accessibility service, "CleanTube remote button". The service gets the keys from the remote. It stops only the key that you set, and opens CleanTube TV. It does not read the screen.

1. Turn on the service. Go to **Settings > System > Accessibility** on the TV and select **CleanTube remote button**. If the service is not in the list, use these commands:

   ```
   adb shell settings put secure enabled_accessibility_services io.github.stanleychy.cleantubetv/.remote.RemoteKeyService
   adb shell settings put secure accessibility_enabled 1
   ```

   CAUTION: The first command replaces the list of accessibility services. If you use other accessibility services, add them to the list. Separate the items with a colon (`:`).

2. In CleanTube TV, go to the **Settings** tab and select **YouTube button opens CleanTube**.
3. Push the YouTube button on the remote. The row shows the key code.

If the YouTube app opens at step 3, the TV does not send this button to apps. Then you cannot change the button.

To stop the function, select the row again and select **Clear**. You can also turn off the service.

## Open YouTube links

The app accepts `youtube.com`, `youtu.be` and `vnd.youtube:` links. A video link starts the player. If the link has a start time (`t=90`, `t=1m30s` or `start=90`), playback starts at that time. Other links open the main screen.

When a link starts the playback, the app sends watch reports only after you push a key on the remote. Thus an app cannot add videos to your Watch History without you.

Android opens `https` links in the official YouTube app first, because that app is verified for these domains. To use CleanTube TV for these links, do one of these steps:

- Approve the domains for CleanTube TV (recommended):

  ```
  adb shell pm set-app-links-user-selection --user 0 --package io.github.stanleychy.cleantubetv true youtube.com www.youtube.com m.youtube.com youtu.be
  ```

- Or disable the official YouTube app:

  ```
  adb shell pm disable-user --user 0 com.google.android.youtube.tv
  ```

  CAUTION: When the official YouTube app is disabled, you cannot cast from your phone to the TV. To enable the app again, use `adb shell pm enable com.google.android.youtube.tv`.

To test a link, use this command:

```
adb shell am start -a android.intent.action.VIEW -d "https://youtu.be/VIDEO_ID"
```

## Find problems

To see the app log, run this command while the app runs:

```
adb logcat -s Auth InnerTube PlayerActivity MainActivity SponsorBlock WatchReporter ExoPlayerImpl
```

## Warning

Third-party YouTube clients are not permitted by the YouTube Terms of Service. Google can restrict accounts that use them. Use this app at your own risk. You can use a secondary Google account to decrease the risk.

YouTube changes its API frequently. Playback can stop without warning. When this occurs, update the client values in `app/src/main/java/io/github/stanleychy/cleantubetv/yt/Clients.kt`.

## Release process (maintainer)

1. Change `appVersion` in `app/build.gradle.kts`. Use `MAJOR.MINOR.PATCH`. The build calculates `versionCode` from it.
2. Merge the change to `main` through a pull request.
3. Push a tag that is the same as the version:

   ```
   git tag -a v0.2.0 -m "CleanTube TV 0.2.0"
   git push origin v0.2.0
   ```

4. The Release workflow builds, signs and checks the APK. It makes a draft release with the APK, `SHA256SUMS` and release notes. The notes come from the Conventional Commits since the last tag. Each line names the contributor. Changes by the maintainers are not named. To see them before you push the tag, run `.github/scripts/changelog.py`.
5. Examine the draft release on GitHub. Then publish it.

To build and sign without a release, run the Release workflow by hand (**Actions > Release > Run workflow**). The APK is in the workflow artifacts.

The signing key is in the secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD` of the `release` environment. Only `v*` tags and `main` can use this environment. Refer to [docs/repository-security.md](docs/repository-security.md). Keep an offline backup of the keystore and the password. If you lose them, users must uninstall the app to install a new release.

## Contribute

Refer to [CONTRIBUTING.md](CONTRIBUTING.md). To report a security problem, refer to [SECURITY.md](SECURITY.md).

## Vendored files

The directory `vendor/ejs` contains the yt-dlp EJS solver. The current version of the app does not use it. It is kept for a future fallback that must solve the YouTube signature and `n` challenges. Refer to `vendor/ejs/README.md`.

## License

CleanTube TV is free software. You can redistribute it and change it under the terms of the [GNU General Public License version 3](LICENSE). It has no warranty.

The vendored files in `vendor/ejs` have their own licenses (Unlicense, ISC and MIT). Refer to `vendor/ejs/README.md`.
