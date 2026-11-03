# Vendored JavaScript: yt-dlp EJS solver

These files solve the YouTube player signature and `n` challenges.
The app does not use them now. They are not in the APK. A future fallback can run them in the system WebView. The app never downloads JavaScript at run time.

| File | Source | License |
|---|---|---|
| `yt.solver.core.js` | https://github.com/yt-dlp/ejs/releases/tag/0.8.0 | Unlicense |
| `yt.solver.lib.js` | https://github.com/yt-dlp/ejs/releases/tag/0.8.0 | Unlicense. Bundles meriyah 6.1.4 (ISC) and astring 1.9.0 (MIT). The license texts are in the file header. |

## Verify the files

1. Go to this directory.
2. Run this command: `sha256sum -c SHA256SUMS`
3. Make sure that each line shows `OK`.

The values in `SHA256SUMS` are the digests that GitHub shows for the 0.8.0 release assets.

## Update the files

1. Download the new release assets from https://github.com/yt-dlp/ejs/releases.
2. Compare the SHA-256 of each file with the digest on the release page.
3. Read the diff of `yt.solver.core.js`. Make sure that it does not add network access.
4. Update `SHA256SUMS` and this table in the same commit.
