package io.github.stanleychy.cleantubetv.yt

/**
 * Builds a static DASH MPD from the adaptive formats of a player response.
 * Each format is a single file with a sidx index (SegmentBase), the same as the
 * official clients use. Formats are grouped by codec family, because a player
 * can only switch quality inside one codec.
 */
object DashManifest {
    fun build(info: PlayerInfo): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.append("""<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" minBufferTime="PT1.5S" """)
        sb.append("""profiles="urn:mpeg:dash:profile:isoff-on-demand:2011" """)
        sb.append("""mediaPresentationDuration="PT${durationSec(info)}S"><Period>""")

        var setId = 0
        val groups = info.formats.groupBy { Triple(it.mimeType, codecFamily(it.codecs), if (it.isVideo) "" else it.audioLang.orEmpty()) }
        for ((key, formats) in groups) {
            val (mime, _, lang) = key
            val first = formats.first()
            sb.append("""<AdaptationSet id="${setId++}" mimeType="$mime" subsegmentAlignment="true"""")
            if (!first.isVideo && lang.isNotEmpty()) sb.append(""" lang="${esc(lang)}"""")
            sb.append(">")
            if (!first.isVideo) {
                first.audioLabel?.let { sb.append("<Label>${esc(it)}</Label>") }
                val role = if (first.audioIsDefault) "main" else "alternate"
                sb.append("""<Role schemeIdUri="urn:mpeg:dash:role:2011" value="$role"/>""")
            }
            for (f in formats) {
                sb.append("""<Representation id="${f.itag}" codecs="${esc(f.codecs)}" bandwidth="${f.bitrate}"""")
                if (f.isVideo) sb.append(""" width="${f.width}" height="${f.height}" frameRate="${f.fps}"""")
                sb.append(">")
                if (!f.isVideo) {
                    sb.append("""<AudioChannelConfiguration schemeIdUri="urn:mpeg:dash:23003:3:audio_channel_configuration:2011" value="2"/>""")
                }
                sb.append("<BaseURL>${esc(f.url)}</BaseURL>")
                sb.append("""<SegmentBase indexRange="${f.indexRange}"><Initialization range="${f.initRange}"/></SegmentBase>""")
                sb.append("</Representation>")
            }
            sb.append("</AdaptationSet>")
        }

        for (c in info.captions) {
            sb.append("""<AdaptationSet id="${setId++}" mimeType="text/vtt" lang="${esc(c.lang)}">""")
            val label = if (c.isAuto) "${c.label} (auto)" else c.label
            sb.append("<Label>${esc(label)}</Label>")
            sb.append("""<Representation id="cc${setId}" bandwidth="0">""")
            sb.append("<BaseURL>${esc(withParam(c.url, "fmt", "vtt"))}</BaseURL>")
            sb.append("</Representation></AdaptationSet>")
        }

        sb.append("</Period></MPD>")
        return sb.toString()
    }

    private fun durationSec(info: PlayerInfo): String =
        if (info.lengthSec > 0) info.lengthSec.toString() else "0"

    /** vp9 and vp9.2 (HDR) are different families, so SDR and HDR are not mixed. */
    private fun codecFamily(codecs: String): String = when {
        codecs.startsWith("vp9.2") || codecs.startsWith("vp09.02") -> "vp9.2"
        codecs.startsWith("vp9") || codecs.startsWith("vp09") -> "vp9"
        codecs.startsWith("av01") -> if (codecs.split('.').getOrNull(3) == "10") "av1-10" else "av1"
        else -> codecs.substringBefore('.')
    }

    private fun withParam(url: String, k: String, v: String): String {
        val cleaned = url.replace(Regex("([?&])$k=[^&]*&?"), "$1").trimEnd('&', '?')
        return cleaned + (if ('?' in cleaned) "&" else "?") + "$k=$v"
    }

    private fun esc(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
