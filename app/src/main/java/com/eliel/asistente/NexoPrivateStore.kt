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

object NexoPrivateStore {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "nexo_private_data_v1"
    private const val PREFS = "nexo_private_data"

    fun putString(context: Context, key: String, value: String): Boolean =
        runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(cipherKey(key), Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(ivKey(key), Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .commit()
        }.getOrDefault(false)

    fun getString(context: Context, key: String): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encryptedB64 = prefs.getString(cipherKey(key), null) ?: return null
        val ivB64 = prefs.getString(ivKey(key), null) ?: return null

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
        }.getOrNull()
    }

    fun contains(context: Context, key: String): Boolean =
        getString(context, key) != null

    fun remove(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(cipherKey(key))
            .remove(ivKey(key))
            .apply()
    }

    private fun cipherKey(key: String) = key + "_cipher"
    private fun ivKey(key: String) = key + "_iv"

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
