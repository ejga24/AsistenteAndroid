package com.eliel.asistente

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object NexoAccessibilityStatus {
    fun isEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()

        val component = ComponentName(
            context,
            MiaAccessibilityService::class.java
        ).flattenToString()

        return enabled.split(':')
            .map { it.trim() }
            .any { it.equals(component, ignoreCase = true) }
    }
}
