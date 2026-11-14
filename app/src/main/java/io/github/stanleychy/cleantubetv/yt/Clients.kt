package io.github.stanleychy.cleantubetv.yt

/**
 * InnerTube client identities. YouTube changes what each client is allowed to do,
 * so every value that may need an update lives in this file.
 *
 * - TV (TVHTML5): accepts the YouTube-on-TV OAuth token. Used for Home, Subscriptions
 *   and anything tied to the account.
 * - VISIONOS: returns direct stream URLs (no signature or "n" challenge, no PoToken)
 *   up to 4K HDR, without sign-in. Used for playback.
 * - ANDROID_VR: the second client for playback, used when VISIONOS gives no stream. It returns direct
 *   stream URLs without sign-in too. But the stream server refuses some requests that have no PoToken
 *   (HTTP 403 on parts of the low-bitrate audio formats, verified 2026-11-13). Thus it is only a fallback.
 *
 * The values of VISIONOS and ANDROID_VR come from INNERTUBE_CLIENTS in yt-dlp
 * (yt_dlp/extractor/youtube/_base.py). Compare them with that file when a client stops working.
 */
data class Client(
    val name: String,
    val version: String,
    val nameId: String,
    val userAgent: String,
    val extra: Map<String, Any> = emptyMap(),
    /** The name of the stored signed-out visitor ID. Two clients with the same [name] must have different keys. */
    val visitorKey: String = name,
)

object Clients {
    const val TV_UA =
        "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), " +
            "Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)"

    val TV = Client(
        name = "TVHTML5",
        version = "7.20260901.15.00",
        nameId = "7",
        userAgent = TV_UA,
    )

    /**
     * TVHTML5 with no account. It has the name, version and User-Agent of [TV], but it is a signed-out client:
     * it has its own visitor ID and it never gets the token. Search uses it when the history setting is off,
     * so that YouTube cannot put the query in the search history of the account.
     */
    val TV_SIGNED_OUT = TV.copy(visitorKey = "TVHTML5_SIGNED_OUT")

    val VISIONOS = Client(
        name = "VISIONOS",
        version = "1.02",
        nameId = "101",
        userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/26.0 Safari/605.1.15",
        extra = mapOf("deviceMake" to "Apple", "deviceModel" to "RealityDevice17,1"),
    )

    /** Do not increase the version: a version after 1.65 can return only SABR streams (no direct URLs). */
    val ANDROID_VR = Client(
        name = "ANDROID_VR",
        version = "1.65.10",
        nameId = "28",
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 " +
            "(Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
        extra = mapOf(
            "deviceMake" to "Oculus",
            "deviceModel" to "Quest 3",
            "androidSdkVersion" to 32,
            "osName" to "Android",
            "osVersion" to "12L",
        ),
    )

    /**
     * The signed-out clients for playback, in the sequence in which the app tries them.
     * Each client must return direct stream URLs: the app does not run YouTube player JavaScript.
     */
    val PLAYBACK = listOf(VISIONOS, ANDROID_VR)
}
