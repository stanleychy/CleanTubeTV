package io.github.stanleychy.cleantubetv.yt

/**
 * InnerTube client identities. YouTube changes what each client is allowed to do,
 * so every value that may need an update lives in this file.
 *
 * - TV (TVHTML5): accepts the YouTube-on-TV OAuth token. Used for Home, Subscriptions
 *   and anything tied to the account.
 * - VISIONOS: returns direct stream URLs (no signature or "n" challenge, no PoToken)
 *   up to 4K HDR, without sign-in. Used for playback.
 */
data class Client(
    val name: String,
    val version: String,
    val nameId: String,
    val userAgent: String,
    val extra: Map<String, Any> = emptyMap(),
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

    val VISIONOS = Client(
        name = "VISIONOS",
        version = "1.02",
        nameId = "101",
        userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/26.0 Safari/605.1.15",
        extra = mapOf("deviceMake" to "Apple", "deviceModel" to "RealityDevice17,1"),
    )
}
