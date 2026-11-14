package io.github.stanleychy.cleantubetv.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.TextView
import io.github.stanleychy.cleantubetv.R
import io.github.stanleychy.cleantubetv.app

/**
 * Shows the code for "Link with TV code". The user enters it in the official YouTube app on a phone.
 * The screen also shows if a phone is linked, and lets the user remove the link.
 */
class CastActivity : Activity() {
    private lateinit var code: TextView
    private lateinit var hint: TextView
    private lateinit var unlink: TextView
    private val handler = Handler(Looper.getMainLooper())

    /** Counts the code requests. A reply to an old request (before a stop or an unlink) is not shown. */
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cast)
        code = findViewById(R.id.cast_code)
        hint = findViewById(R.id.cast_hint)
        unlink = findViewById(R.id.cast_unlink)
        unlink.setOnClickListener { confirmUnlink() }
    }

    /** The button has the focus when the screen opens, so one push of OK must not remove the link. */
    private fun confirmUnlink() {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.cast_unlink)
            .setMessage(R.string.cast_unlink_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                app.cast.unlink()
                // The old code belongs to the screen that is now forgotten.
                requestCode()
            }
            .show()
    }

    override fun onStart() {
        super.onStart()
        app.cast.onLinked = { showLinkState() }
        requestCode()
    }

    override fun onStop() {
        app.cast.onLinked = null
        generation++
        handler.removeCallbacksAndMessages(null)
        super.onStop()
    }

    /** Gets a code and shows it. A code is valid for a short time, so the screen gets a new one after 5 minutes. */
    private fun requestCode() {
        val request = ++generation
        handler.removeCallbacksAndMessages(null)
        code.text = ""
        hint.text = getString(R.string.loading)
        unlink.visibility = View.GONE
        app.io.execute {
            val result = runCatching { app.cast.requestPairingCode() }
            runOnUiThread {
                if (request != generation) return@runOnUiThread
                result.onSuccess {
                    code.text = it.chunked(3).joinToString(" ")
                    showLinkState()
                    handler.postDelayed({ requestCode() }, CODE_LIFETIME_MS)
                }.onFailure { e ->
                    // Only the class name: the message of a network error can contain a URL.
                    Log.w(TAG, "pairing code request failed: ${e.javaClass.simpleName}")
                    hint.text = getString(R.string.load_error, e.message)
                    handler.postDelayed({ requestCode() }, RETRY_MS)
                }
            }
        }
    }

    private fun showLinkState() {
        val linked = app.cast.isLinked
        hint.text = getString(if (linked) R.string.cast_linked else R.string.cast_waiting)
        unlink.visibility = if (linked) View.VISIBLE else View.GONE
    }

    private companion object {
        const val TAG = "Lounge"
        const val CODE_LIFETIME_MS = 5 * 60 * 1000L
        const val RETRY_MS = 30_000L
    }
}
