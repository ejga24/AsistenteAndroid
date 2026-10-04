package com.eliel.asistente

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.Locale

object NexoSkillRegistry {

    private val appAliases = mapOf(
        "waze" to "com.waze",
        "spotify" to "com.spotify.music",
        "youtube" to "com.google.android.youtube",
        "chatgpt" to "com.openai.chatgpt",
        "chat gpt" to "com.openai.chatgpt",
        "whatsapp" to "com.whatsapp",
        "gmail" to "com.google.android.gm",
        "chrome" to "com.android.chrome",
        "google maps" to "com.google.android.apps.maps",
        "maps" to "com.google.android.apps.maps",
        "disney plus" to "com.disney.disneyplus",
        "disney+" to "com.disney.disneyplus"
    )

    fun resolvePackageName(context: Context, requestedName: String): String? {
        val clean = normalize(requestedName)
        if (clean.isBlank()) return null

        appAliases[clean]?.let { return it }

        val packageManager = context.packageManager
        val installed = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        val exact = installed.firstOrNull {
            normalize(packageManager.getApplicationLabel(it).toString()) == clean
        }
        if (exact != null) return exact.packageName

        return installed.firstOrNull {
            val label = normalize(packageManager.getApplicationLabel(it).toString())
            label.contains(clean) || clean.contains(label)
        }?.packageName
    }

    fun resolveLaunchIntent(context: Context, requestedName: String): Intent? {
        val packageName = resolvePackageName(context, requestedName) ?: return null
        return context.packageManager.getLaunchIntentForPackage(packageName)
    }

    fun knownSkills(): List<String> = listOf(
        "Apps", "Waze", "Spotify", "YouTube", "ChatGPT", "Vision",
        "Control de pantalla", "Volumen", "Brillo", "Modo carro"
    )

    private fun normalize(value: String): String =
        value.lowercase(Locale.getDefault())
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .trim()
}
