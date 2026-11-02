package io.github.stanleychy.cleantubetv.remote

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings.Secure
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import io.github.stanleychy.cleantubetv.app

/**
 * Opens CleanTube when the user pushes the remote button saved in Settings (the YouTube button).
 *
 * The service only filters key events. It does not read window content and ignores accessibility events.
 * It consumes only the saved key code, and only when that code is set. All other keys pass through.
 */
class RemoteKeyService : AccessibilityService() {

    override fun onKeyEvent(event: KeyEvent): Boolean {
        learner?.let { onLearned ->
            if (event.keyCode in NOT_LEARNABLE) return false
            if (event.action == KeyEvent.ACTION_UP) {
                learner = null
                onLearned(event.keyCode)
            }
            return true
        }
        val code = app.settings.remoteKeyCode
        if (code == KeyEvent.KEYCODE_UNKNOWN || event.keyCode != code) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) openApp()
        return true
    }

    private fun openApp() {
        val launch = packageManager.getLeanbackLaunchIntentForPackage(packageName) ?: return
        // Same flags as the launcher: bring the existing task to the front, or start a new one.
        startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        learner = null
        super.onDestroy()
    }

    companion object {
        /** Keys that we never save, because the user needs them to move in the app. */
        private val NOT_LEARNABLE = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
        )

        /**
         * When set, the next learnable key goes to this callback (on the main thread) and does not reach apps.
         * The service and the activities run in the same process, so a static field is sufficient.
         */
        @Volatile
        var learner: ((Int) -> Unit)? = null

        /** True when the user turned on the service in the system accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, RemoteKeyService::class.java)
            val enabled = Secure.getString(context.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
