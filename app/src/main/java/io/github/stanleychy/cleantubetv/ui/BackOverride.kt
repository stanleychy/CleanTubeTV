package io.github.stanleychy.cleantubetv.ui

import android.app.Activity
import android.os.Build
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/**
 * Runs [onBack] for the Back key while [enabled] is true, on Android versions that use back callbacks.
 * Android 16 and later do not call `Activity.onBackPressed()` for an app that targets API 36.
 * They call a registered [OnBackInvokedCallback]. Earlier versions call `onBackPressed()`,
 * so the activity must override that function too.
 */
class BackOverride(private val activity: Activity, private val onBack: () -> Unit) {
    /** Typed as Any, because the callback class does not exist before API 33. */
    private val callback: Any? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) OnBackInvokedCallback { onBack() } else null

    var enabled = false
        set(value) {
            if (field == value) return
            field = value
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            val dispatcher = activity.onBackInvokedDispatcher
            val registered = callback as OnBackInvokedCallback
            if (value) dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, registered)
            else dispatcher.unregisterOnBackInvokedCallback(registered)
        }
}
