package io.github.stanleychy.cleantubetv.yt

import org.json.JSONObject

data class StreamFormat(
    val itag: Int,
    val url: String,
    val mimeType: String, // e.g. video/webm
    val codecs: String, // e.g. vp9
    val bitrate: Int,
    val width: Int,
    val height: Int,
    val fps: Int,
    val initRange: String,
    val indexRange: String,
    val audioLang: String?,
    val audioLabel: String?,
    val audioIsDefault: Boolean,
) {
    val isVideo get() = mimeType.startsWith("video/")
}

data class CaptionTrack(val url: String, val lang: String, val label: String, val isAuto: Boolean)

data class PlayerInfo(
    val videoId: String,
    val title: String,
    val channel: String?,
    val lengthSec: Long,
    val isLive: Boolean,
    val formats: List<StreamFormat>,
    val hlsUrl: String?,
    val captions: List<CaptionTrack>,
    val statsPlaybackUrl: String?,
    val statsWatchtimeUrl: String?,
)

class PlaybackException(message: String) : Exception(message)

object PlayerParser {
    fun parse(videoId: String, r: JSONObject): PlayerInfo {
        val ps = r.optJSONObject("playabilityStatus")
        val status = ps?.optString("status") ?: "UNKNOWN"
        if (status != "OK") {
            val reason = ps?.optString("reason")?.ifEmpty { null }
                ?: ps?.obj("errorScreen", "playerErrorMessageRenderer", "reason").text()
                ?: status
            throw PlaybackException(reason)
        }
        val details = r.optJSONObject("videoDetails")
        val sd = r.optJSONObject("streamingData")
        val formats = sd?.optJSONArray("adaptiveFormats")?.objects().orEmpty().mapNotNull(::parseFormat)
        val captions = r.obj("captions", "playerCaptionsTracklistRenderer")?.optJSONArray("captionTracks")
            ?.objects().orEmpty().mapNotNull { c ->
                val url = c.optString("baseUrl").ifEmpty { return@mapNotNull null }
                CaptionTrack(
                    url = url,
                    lang = c.optString("languageCode", "und"),
                    label = c.optJSONObject("name").text() ?: c.optString("languageCode"),
                    isAuto = c.optString("kind") == "asr",
                )
            }
        val tracking = r.optJSONObject("playbackTracking")
        return PlayerInfo(
            videoId = videoId,
            title = details?.optString("title").orEmpty(),
            channel = details?.optString("author"),
            lengthSec = details?.optString("lengthSeconds")?.toLongOrNull() ?: 0,
            isLive = details?.optBoolean("isLive") == true,
            formats = formats,
            hlsUrl = sd?.optString("hlsManifestUrl")?.ifEmpty { null },
            captions = captions,
            statsPlaybackUrl = tracking?.obj("videostatsPlaybackUrl")?.optString("baseUrl")?.ifEmpty { null },
            statsWatchtimeUrl = tracking?.obj("videostatsWatchtimeUrl")?.optString("baseUrl")?.ifEmpty { null },
        )
    }

    private val MIME = Regex("""^([\w-]+/[\w.+-]+);\s*codecs="([^"]+)"""")

    private fun parseFormat(f: JSONObject): StreamFormat? {
        // Only direct URLs. Ciphered formats need the JS solver (not used by this client yet).
        val url = f.optString("url").ifEmpty { return null }
        val init = f.optJSONObject("initRange") ?: return null
        val index = f.optJSONObject("indexRange") ?: return null
        // Skip "dynamic range compression" audio duplicates.
        if (f.optBoolean("isDrc")) return null
        val m = MIME.find(f.optString("mimeType")) ?: return null
        val audio = f.optJSONObject("audioTrack")
        return StreamFormat(
            itag = f.optInt("itag"),
            url = url,
            mimeType = m.groupValues[1],
            codecs = m.groupValues[2],
            bitrate = f.optInt("bitrate"),
            width = f.optInt("width"),
            height = f.optInt("height"),
            fps = f.optInt("fps"),
            initRange = "${init.optString("start")}-${init.optString("end")}",
            indexRange = "${index.optString("start")}-${index.optString("end")}",
            audioLang = audio?.optString("id")?.substringBefore('.')?.ifEmpty { null },
            audioLabel = audio?.optString("displayName")?.ifEmpty { null },
            audioIsDefault = audio?.optBoolean("audioIsDefault") ?: true,
        )
    }
}
