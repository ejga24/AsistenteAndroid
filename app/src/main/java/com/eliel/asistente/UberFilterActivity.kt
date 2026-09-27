package com.eliel.asistente

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class UberFilterActivity : AppCompatActivity() {

    companion object {
        const val PREFS = "uber_filter_settings"
        const val KEY_THRESHOLD = "threshold"
        const val KEY_SUNDAY_ONLY = "sunday_only"
    }

    private lateinit var thresholdInput: EditText
    private lateinit var sundayOnlyCheck: CheckBox
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 36, 36, 36)
        }
        root.addView(box)

        fun label(text: String, size: Float, bold: Boolean = false): TextView =
            TextView(this).apply {
                this.text = text
                textSize = size
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 8, 0, 8)
            }

        box.addView(label("Filtro Uber", 30f, true))

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "desconocida"
        } catch (_: Exception) {
            "desconocida"
        }
        box.addView(label("Versión instalada: " + versionName, 15f, true))

        box.addView(label("Analiza las solicitudes visibles de Uber Driver y calcula cuánto pagan por kilómetro.", 17f))
        box.addView(label("Mínimo por kilómetro (B/.)", 17f, true))

        thresholdInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            textSize = 24f
        }
        box.addView(thresholdInput)

        sundayOnlyCheck = CheckBox(this).apply {
            text = "Aplicar filtro solamente los domingos"
            textSize = 16f
        }
        box.addView(sundayOnlyCheck)

        val save = Button(this).apply { text = "Guardar regla" }
        box.addView(save)

        val access = Button(this).apply { text = "Activar lectura de Uber" }
        box.addView(access)

        statusText = label("", 16f)
        box.addView(statusText)

        val debugPreview = label("", 12f)
        val debugPrefs = getSharedPreferences("uber_filter_debug", MODE_PRIVATE)
        val captured = debugPrefs.getString("last_accessibility_text", "").orEmpty()
        if (captured.isNotBlank()) {
            debugPreview.text = "Última lectura de Uber: " + captured.take(700)
            box.addView(debugPreview)
        }

        val ocrCaptured = debugPrefs.getString("last_ocr_text", "").orEmpty()
        if (ocrCaptured.isNotBlank()) {
            box.addView(label("Última lectura visual: " + ocrCaptured.take(700), 12f))
        }

        box.addView(label("La app muestra ACEPTAR o RECHAZAR como recomendación visual. Tú haces el toque final en Uber.", 14f))

        setContentView(root)

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        thresholdInput.setText(String.format(Locale.US, "%.2f", prefs.getFloat(KEY_THRESHOLD, 0.50f)))
        sundayOnlyCheck.isChecked = prefs.getBoolean(KEY_SUNDAY_ONLY, true)

        save.setOnClickListener {
            val threshold = thresholdInput.text.toString().replace(',', '.').toFloatOrNull()
            if (threshold == null || threshold <= 0f) {
                Toast.makeText(this, "Escribe un valor válido, por ejemplo 0.50", Toast.LENGTH_LONG).show()
            } else {
                prefs.edit().putFloat(KEY_THRESHOLD, threshold)
                    .putBoolean(KEY_SUNDAY_ONLY, sundayOnlyCheck.isChecked).apply()
                Toast.makeText(this, "Regla guardada", Toast.LENGTH_SHORT).show()
                updateStatus()
            }
        }

        access.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val threshold = prefs.getFloat(KEY_THRESHOLD, 0.50f)
        val mode = if (prefs.getBoolean(KEY_SUNDAY_ONLY, true)) "solo domingos" else "todos los días"
        val debug = getSharedPreferences("uber_filter_debug", MODE_PRIVATE)
        val lastRateAt = debug.getLong("last_rate_at", 0L)
        val lastRate = if (lastRateAt > 0L) debug.getFloat("last_rate", -1f) else -1f

        statusText.text = if (isServiceEnabled()) {
            buildString {
                append("ACTIVO\nMínimo: B/. ")
                append(String.format(Locale.US, "%.2f", threshold))
                append("/km\nModo: ")
                append(mode)
                append("\nVersión: ")
                append(packageManager.getPackageInfo(packageName, 0).versionName ?: "?")
                if (lastRate >= 0f) {
                    append("\nÚltima tarifa detectada: USD ")
                    append(String.format(Locale.US, "%.2f", lastRate))
                    append("/km")
                }
            }
        } else {
            "INACTIVO\nActiva “Filtro Uber” en Accesibilidad."
        }
    }

    private fun isServiceEnabled(): Boolean {
        val expected = ComponentName(this, UberFilterAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (service in splitter) if (service.equals(expected, true)) return true
        return false
    }
}
