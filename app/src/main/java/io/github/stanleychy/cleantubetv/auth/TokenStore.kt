package io.github.stanleychy.cleantubetv.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the OAuth refresh token encrypted with an AES-256-GCM key that lives in the
 * Android Keystore. The key cannot leave the device.
 */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    var refreshToken: String?
        get() {
            val stored = prefs.getString(KEY_REFRESH, null) ?: return null
            return try {
                decrypt(stored)
            } catch (e: Exception) {
                // The key is gone or is not the key that encrypted the value: the value can never be read again.
                // Other errors (for example a Keystore service problem) can be temporary, so the value stays.
                val permanent = e is AEADBadTagException || e is KeyPermanentlyInvalidatedException
                Log.w(TAG, "cannot decrypt the stored refresh token" + if (permanent) ", sign-in removed" else "", e)
                if (permanent) prefs.edit().remove(KEY_REFRESH).apply()
                null
            }
        }
        set(value) {
            if (value == null) prefs.edit().remove(KEY_REFRESH).apply()
            else prefs.edit().putString(KEY_REFRESH, encrypt(value)).apply()
        }

    /** Stable random device ID sent with the device-code request. Not secret. */
    val deviceId: String
        get() = prefs.getString(KEY_DEVICE, null) ?: java.util.UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE, it).apply()
        }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val data = Base64.decode(stored, Base64.NO_WRAP)
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, IV_LEN))
        return c.doFinal(data, IV_LEN, data.size - IV_LEN).toString(Charsets.UTF_8)
    }

    private companion object {
        /** The same tag as Auth, so that "adb logcat -s Auth" shows it. */
        const val TAG = "Auth"
        const val ALIAS = "cleantube_refresh_token"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
        const val KEY_REFRESH = "refresh_token"
        const val KEY_DEVICE = "device_id"
    }
}
