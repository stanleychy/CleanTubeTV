package io.github.stanleychy.cleantubetv.auth

import android.content.Context
import android.util.Log
import io.github.stanleychy.cleantubetv.net.Http
import io.github.stanleychy.cleantubetv.yt.Clients
import io.github.stanleychy.cleantubetv.yt.TvAppInfoProvider
import org.json.JSONObject
import java.io.IOException

/**
 * YouTube-on-TV OAuth device flow, the same flow the official TV app uses:
 * 1. Ask for a user code.
 * 2. The user enters it at youtube.com/activate.
 * 3. Poll until the token endpoint returns a refresh token.
 */
class Auth(private val context: Context) {
    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUrl: String,
        val intervalSec: Int,
        val expiresInSec: Int,
    )

    sealed class PollResult {
        data object Pending : PollResult()
        data object SlowDown : PollResult()
        data object Success : PollResult()
        data class Failed(val reason: String) : PollResult()
    }

    private val store = TokenStore(context)
    @Volatile private var accessToken: String? = null
    @Volatile private var accessExpiry = 0L

    val isSignedIn: Boolean get() = store.refreshToken != null

    fun requestDeviceCode(): DeviceCode {
        val app = TvAppInfoProvider.get(context)
        val body = JSONObject()
            .put("client_id", app.clientId)
            .put("device_id", store.deviceId)
            .put("model_name", "ytlr::")
            .put("scope", SCOPE)
        val j = post(DEVICE_CODE_URL, body)
        if (!j.has("device_code")) throw IOException("device code request failed: ${j.optString("error")}")
        return DeviceCode(
            deviceCode = j.getString("device_code"),
            userCode = j.getString("user_code"),
            verificationUrl = j.optString("verification_url", "https://www.youtube.com/activate"),
            intervalSec = j.optInt("interval", 5),
            expiresInSec = j.optInt("expires_in", 1800),
        )
    }

    fun poll(code: DeviceCode): PollResult {
        val app = TvAppInfoProvider.get(context)
        val body = JSONObject()
            .put("code", code.deviceCode)
            .put("client_id", app.clientId)
            .put("client_secret", app.clientSecret)
            .put("grant_type", "http://oauth.net/grant_type/device/1.0")
        val j = post(TOKEN_URL, body)
        return when {
            j.has("refresh_token") -> {
                store.refreshToken = j.getString("refresh_token")
                setAccess(j)
                PollResult.Success
            }
            j.optString("error") == "authorization_pending" -> PollResult.Pending
            j.optString("error") == "slow_down" -> PollResult.SlowDown
            else -> PollResult.Failed(j.optString("error", "unknown error"))
        }
    }

    /** Returns a valid access token, or null when the user is not signed in. Call off the main thread. */
    @Synchronized
    fun accessToken(): String? {
        val refresh = store.refreshToken ?: return null
        if (accessToken != null && System.currentTimeMillis() < accessExpiry) return accessToken
        val app = TvAppInfoProvider.get(context)
        val body = JSONObject()
            .put("refresh_token", refresh)
            .put("client_id", app.clientId)
            .put("client_secret", app.clientSecret)
            .put("grant_type", "refresh_token")
        val j = post(TOKEN_URL, body)
        if (j.has("access_token")) {
            setAccess(j)
            return accessToken
        }
        val err = j.optString("error")
        Log.w(TAG, "token refresh failed: $err")
        if (err == "invalid_grant") signOut()
        return null
    }

    fun signOut() {
        store.refreshToken = null
        accessToken = null
        accessExpiry = 0
    }

    private fun setAccess(j: JSONObject) {
        accessToken = j.getString("access_token")
        // Refresh one minute before the real expiry.
        accessExpiry = System.currentTimeMillis() + (j.optLong("expires_in", 3600) - 60) * 1000
    }

    private fun post(url: String, body: JSONObject): JSONObject {
        val r = Http.request(
            url,
            method = "POST",
            headers = mapOf("Content-Type" to "application/json", "User-Agent" to Clients.TV_UA),
            body = body.toString(),
        )
        return runCatching { JSONObject(r.body) }.getOrElse { throw IOException("HTTP ${r.code} from ${url.substringAfterLast('/')}") }
    }

    private companion object {
        const val TAG = "Auth"
        const val DEVICE_CODE_URL = "https://www.youtube.com/o/oauth2/device/code"
        const val TOKEN_URL = "https://www.youtube.com/o/oauth2/token"
        const val SCOPE = "http://gdata.youtube.com https://www.googleapis.com/auth/youtube-paid-content"
    }
}
