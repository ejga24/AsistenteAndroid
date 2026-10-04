package com.eliel.asistente

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object NexoSecretStore {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "nexo_agent_secrets_v1"

    private const val PREFS = "nexo_secure"
    private const val KEY_API_CIPHER = "openai_api_cipher"
    private const val KEY_API_IV = "openai_api_iv"

    fun hasApiKey(context: Context): Boolean =
        readApiKey(context).isNotBlank()

    fun saveApiKey(context: Context, value: String): Boolean {
        val clean = value.trim()
        if (clean.isBlank()) {
            clearApiKey(context)
            return true
        }

        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

            val encrypted = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_API_CIPHER, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(KEY_API_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .commit()
        }.getOrDefault(false)
    }

    fun readApiKey(context: Context): String {
        migrateLegacyIfNeeded(context)

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encryptedB64 = prefs.getString(KEY_API_CIPHER, null) ?: return ""
        val ivB64 = prefs.getString(KEY_API_IV, null) ?: return ""

        return runCatching {
            val encrypted = Base64.decode(encryptedB64, Base64.NO_WRAP)
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, iv)
            )

            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrElse {
            ""
        }
    }

    fun clearApiKey(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_API_CIPHER)
            .remove(KEY_API_IV)
            .apply()

        context.getSharedPreferences(MiaAgentPlanner.PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(MiaAgentPlanner.KEY_API_KEY)
            .apply()
    }

    private fun migrateLegacyIfNeeded(context: Context) {
        val securePrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val legacy = context.getSharedPreferences(
            MiaAgentPlanner.PREFS,
            Context.MODE_PRIVATE
        )

        if (securePrefs.contains(KEY_API_CIPHER)) {
            legacy.edit().remove(MiaAgentPlanner.KEY_API_KEY).apply()
            return
        }

        val oldKey = legacy.getString(MiaAgentPlanner.KEY_API_KEY, "")
            ?.trim()
            .orEmpty()

        if (oldKey.isBlank()) return

        if (saveApiKey(context, oldKey)) {
            legacy.edit().remove(MiaAgentPlanner.KEY_API_KEY).apply()
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )

        return generator.generateKey()
    }
}
