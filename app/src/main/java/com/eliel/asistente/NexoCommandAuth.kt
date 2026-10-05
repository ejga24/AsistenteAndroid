package com.eliel.asistente

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * One-time authorization for commands handed from the private wake service
 * to the exported launcher Activity.
 *
 * MainActivity must remain exported so Android can launch it from the home
 * screen. Never trust a command Intent solely because it targets our package:
 * another app can construct the same explicit Intent. This nonce is generated
 * inside NEXO, stored encrypted, expires quickly and is consumed once.
 */
object NexoCommandAuth {
    private const val PRIVATE_KEY = "wake_command_auth"
    private const val MAX_AGE_MS = 2 * 60 * 1000L
    private const val TOKEN_BYTES = 24

    fun issue(context: Context): String? {
        val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        val token = Base64.encodeToString(
            bytes,
            Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE
        )
        val payload = JSONObject().apply {
            put("token", token)
            put("issued", System.currentTimeMillis())
        }.toString()

        return if (NexoPrivateStore.putString(context, PRIVATE_KEY, payload)) {
            token
        } else {
            null
        }
    }

    fun consume(context: Context, presentedToken: String?): Boolean {
        if (presentedToken.isNullOrBlank()) return false

        val raw = NexoPrivateStore.getString(context, PRIVATE_KEY) ?: return false
        val stored = runCatching {
            val item = JSONObject(raw)
            val token = item.optString("token")
            val issued = item.optLong("issued")
            token to issued
        }.getOrNull() ?: run {
            clear(context)
            return false
        }

        val age = System.currentTimeMillis() - stored.second
        val fresh = stored.second > 0L && age in 0..MAX_AGE_MS
        if (!fresh) {
            clear(context)
            return false
        }

        val matches = MessageDigest.isEqual(
            stored.first.toByteArray(Charsets.UTF_8),
            presentedToken.toByteArray(Charsets.UTF_8)
        )
        if (matches) {
            // One-time token: only the authorized handoff consumes it.
            clear(context)
        }
        return matches
    }

    fun clear(context: Context) {
        NexoPrivateStore.remove(context, PRIVATE_KEY)
    }
}
