package com.eliel.asistente

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SetupCenterActivity : AppCompatActivity() {

    private lateinit var progressText: TextView
    private lateinit var checklistText: TextView

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refresh()
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            NexoVoiceState.setEnabled(this, true)
            startVoiceServiceSafely()
        }
        refresh()
    }

    private fun startVoiceServiceSafely() {
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, AssistantWakeService::class.java).apply {
                    action = AssistantWakeService.ACTION_START
                }
            )
        }.onFailure {
            NexoRuntimeState.markIssue(
                this,
                "Voice Core",
                "Android no permitió iniciar la escucha en segundo plano"
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_center)
        NexoOnboardingState.markPresented(this)

        progressText = findViewById(R.id.setupProgressText)
        checklistText = findViewById(R.id.setupChecklistText)

        findViewById<Button>(R.id.setupMicButton).setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                NexoVoiceState.setEnabled(this, true)
                startVoiceServiceSafely()
                refresh()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        findViewById<Button>(R.id.setupAccessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.setupIntelligenceButton).setOnClickListener {
            startActivity(Intent(this, AgentSettingsActivity::class.java))
        }

        findViewById<Button>(R.id.setupVisionButton).setOnClickListener {
            startActivity(Intent(this, VisionActivity::class.java))
        }

        findViewById<Button>(R.id.setupNotificationsButton).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:" + packageName)
                    )
                )
            }
        }

        findViewById<Button>(R.id.setupBatteryButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val health = NexoSystemHealth.snapshot(this)
        val primaryChecks = health.checks.filter {
            it.id in setOf("voice", "microphone", "accessibility", "intelligence", "vision", "notifications", "battery")
        }
        val completed = primaryChecks.count { it.ready }
        val percent = (completed * 100) / primaryChecks.size

        progressText.text = percent.toString() + "% listo"

        checklistText.text = buildString {
            primaryChecks.forEachIndexed { index, check ->
                append(if (check.ready) "✓ " else "• ")
                append(check.label)
                append("\n")
                append(check.detail)
                if (index < primaryChecks.lastIndex) append("\n\n")
            }

            val contacts = health.checks.first { it.id == "contacts" }
            append("\n\n")
            append(if (contacts.ready) "✓ " else "• ")
            append("Contactos · opcional\n")
            append(contacts.detail)

            append("\n\n")
            append(health.recommendation)
        }
    }

}
