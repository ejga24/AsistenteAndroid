package com.eliel.asistente

import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SystemDiagnosticsActivity : AppCompatActivity() {

    private lateinit var reportText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_system_diagnostics)

        reportText = findViewById(R.id.diagnosticsText)

        findViewById<Button>(R.id.openAccessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.openAppSettingsButton).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + packageName)
                )
            )
        }
        findViewById<Button>(R.id.refreshDiagnosticsButton).setOnClickListener {
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val health = NexoSystemHealth.snapshot(this)
        val battery = (getSystemService(BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val wake = NexoWakeRuntime.status(this)
        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull().orEmpty()
        val enabledSkills = NexoSkillPolicy.enabledNames(this)
        val rc = NexoReleaseGate.evaluate(this)

        reportText.text = buildString {
            append("ESTADO GENERAL\n")
            append(health.summary)
            append("\n").append(health.recommendation)

            append("\n\nCAPACIDADES\n")
            health.checks.forEachIndexed { index, check ->
                append(if (check.ready) "✓ " else "• ")
                append(check.label).append(" — ").append(check.detail)
                if (index < health.checks.lastIndex) append("\n")
            }

            append("\n\nDISPOSITIVO\n")
            append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
            append("\nAndroid ").append(Build.VERSION.RELEASE)
            append(" · API ").append(Build.VERSION.SDK_INT)
            append("\nBatería ").append(battery).append("%")

            append("\n\nVOICE CORE\n")
            append("Wake word: NEXO")
            append("\nMotor: ").append(wake.displayName)
            append("\nModo: ").append(if (wake.local) "Local" else "Compatibilidad")

            append("\n\nRELEASE CANDIDATE\n")
            append(rc.headline)
            append("\n").append(rc.detail)

            append("\n\nNEXO\n")
            append(versionName.ifBlank { "3.0-rc1" })
            append(" · ").append(enabledSkills.size)
            append("/").append(NexoSkillPolicy.definitions.size)
            append(" skills activas")
        }
    }

}
