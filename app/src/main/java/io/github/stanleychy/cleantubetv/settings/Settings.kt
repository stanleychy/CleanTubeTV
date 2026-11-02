package io.github.stanleychy.cleantubetv.settings

import android.content.Context
import java.util.Locale

/** User settings, stored in plain SharedPreferences. Nothing here is secret. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Send watch reports to YouTube, so videos show in Watch History and resume points. */
    var historyEnabled: Boolean
        get() = prefs.getBoolean(KEY_HISTORY, true)
        set(v) = prefs.edit().putBoolean(KEY_HISTORY, v).apply()

    /** Speeds that the player speed button cycles through, in ascending order. */
    var speeds: List<Float>
        get() = prefs.getString(KEY_SPEEDS, null)
            ?.split(',')?.mapNotNull { it.toFloatOrNull() }?.filter { it in SPEED_CHOICES }
            ?.sorted()?.ifEmpty { null }
            ?: DEFAULT_SPEEDS
        set(v) = prefs.edit().putString(KEY_SPEEDS, v.sorted().joinToString(",")).apply()

    /** Preferred audio language (BCP 47). Empty means the original audio of the video. */
    var audioLanguage: String
        get() = prefs.getString(KEY_AUDIO, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_AUDIO, v).apply()

    /** Preferred subtitle language (BCP 47). Empty means subtitles off. */
    var subtitleLanguage: String
        get() = prefs.getString(KEY_SUBS, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_SUBS, v).apply()

    companion object {
        private const val KEY_HISTORY = "history_enabled"
        private const val KEY_SPEEDS = "speeds"
        private const val KEY_AUDIO = "audio_language"
        private const val KEY_SUBS = "subtitle_language"

        val SPEED_CHOICES = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
        val DEFAULT_SPEEDS = listOf(1f, 1.5f, 2f)

        private val COMMON_LANGUAGES = listOf(
            "en", "es", "fr", "de", "it", "pt", "nl", "pl", "ru", "uk", "tr", "ar", "hi",
            "id", "vi", "th", "ja", "ko", "zh-Hans", "zh-Hant",
        )

        /** Language choices, with the device language first. */
        fun languageChoices(): List<String> {
            val device = Locale.getDefault().language
            return (listOf(device) + COMMON_LANGUAGES).distinct()
        }

        fun languageName(tag: String): String =
            Locale.forLanguageTag(tag).getDisplayName(Locale.getDefault()).ifEmpty { tag }

        fun speedLabel(s: Float): String =
            (if (s % 1f == 0f) s.toInt().toString() else s.toString().trimEnd('0')) + "x"
    }
}
