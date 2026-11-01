package io.github.stanleychy.cleantubetv.ui

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import io.github.stanleychy.cleantubetv.R
import io.github.stanleychy.cleantubetv.app
import io.github.stanleychy.cleantubetv.auth.Auth

/** Shows the activation code. The user enters it at youtube.com/activate on a phone or computer. */
class SignInActivity : Activity() {
    private lateinit var url: TextView
    private lateinit var code: TextView
    private lateinit var hint: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var active = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)
        url = findViewById(R.id.sign_in_url)
        code = findViewById(R.id.sign_in_code)
        hint = findViewById(R.id.sign_in_hint)
        requestCode()
    }

    override fun onDestroy() {
        active = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun requestCode() {
        hint.text = getString(R.string.loading)
        app.io.execute {
            val r = runCatching { app.auth.requestDeviceCode() }
            runOnUiThread {
                r.onSuccess { dc ->
                    url.text = dc.verificationUrl.removePrefix("https://")
                    code.text = dc.userCode
                    hint.text = getString(R.string.sign_in_waiting)
                    schedulePoll(dc, dc.intervalSec * 1000L, System.currentTimeMillis() + dc.expiresInSec * 1000L)
                }.onFailure { hint.text = getString(R.string.load_error, it.message) }
            }
        }
    }

    private fun schedulePoll(dc: Auth.DeviceCode, delayMs: Long, deadline: Long) {
        handler.postDelayed({
            if (!active) return@postDelayed
            if (System.currentTimeMillis() > deadline) { requestCode(); return@postDelayed }
            app.io.execute {
                val r = runCatching { app.auth.poll(dc) }.getOrElse { Auth.PollResult.Pending }
                runOnUiThread {
                    when (r) {
                        Auth.PollResult.Success -> finish()
                        Auth.PollResult.Pending -> schedulePoll(dc, delayMs, deadline)
                        Auth.PollResult.SlowDown -> schedulePoll(dc, delayMs + 5000, deadline)
                        is Auth.PollResult.Failed -> hint.text = getString(R.string.load_error, r.reason)
                    }
                }
            }
        }, delayMs)
    }
}
