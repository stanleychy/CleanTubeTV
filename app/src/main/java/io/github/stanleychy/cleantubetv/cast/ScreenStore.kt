package io.github.stanleychy.cleantubetv.cast

import android.content.Context
import io.github.stanleychy.cleantubetv.yt.Scraped
import java.util.UUID

/**
 * The identity of this app as a Lounge screen. A phone that is linked knows the [screenId].
 * The [deviceId] is a random UUID that the app makes. It has no relation to the device or the account.
 */
data class LoungeScreen(val screenId: String, val deviceId: String) {
    companion object {
        fun create(screenId: String) = LoungeScreen(screenId, UUID.randomUUID().toString())
    }
}

/**
 * Keeps the linked screen in app-private SharedPreferences, so that the phone stays linked across app starts.
 * The lounge token is not kept: the app gets a new one from the screen ID at each connection.
 * The screen ID lets a client send commands to this screen, so it must stay private. Backup is off for this app.
 */
class ScreenStore(context: Context) {
    private val prefs = context.getSharedPreferences("cast", Context.MODE_PRIVATE)

    /** The linked screen, or null when no phone is linked or when a stored value is not valid. */
    fun load(): LoungeScreen? {
        val screenId = Scraped.screenId(prefs.getString(KEY_SCREEN_ID, null)) ?: return null
        val deviceId = prefs.getString(KEY_DEVICE_ID, null)?.takeIf { UUID_FORMAT.matches(it) } ?: return null
        return LoungeScreen(screenId, deviceId)
    }

    fun save(screen: LoungeScreen) {
        prefs.edit().putString(KEY_SCREEN_ID, screen.screenId).putString(KEY_DEVICE_ID, screen.deviceId).apply()
    }

    /** Forgets the screen. A linked phone then finds no screen with the old ID. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_SCREEN_ID = "screen_id"
        const val KEY_DEVICE_ID = "device_id"
        val UUID_FORMAT = Regex("^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$")
    }
}
