package com.eliel.asistente

import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AgentSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_agent_settings)

        val apiKeyInput = findViewById<EditText>(R.id.apiKeyInput)
        val modelInput = findViewById<EditText>(R.id.modelInput)
        val saveButton = findViewById<Button>(R.id.saveAiButton)
        val removeButton = findViewById<Button>(R.id.removeAiButton)
        val testButton = findViewById<Button>(R.id.testAiButton)
        val testStatus = findViewById<TextView>(R.id.aiTestStatus)

        val prefs = getSharedPreferences(MiaAgentPlanner.PREFS, MODE_PRIVATE)
        apiKeyInput.setText("")
        apiKeyInput.hint = if (NexoSecretStore.hasApiKey(this)) {
            "Credencial configurada · escribe una nueva para reemplazarla"
        } else {
            "Gemini API key"
        }
        modelInput.setText(MiaAgentPlanner.resolveConfiguredModel(this))

        testButton.setOnClickListener {
            if (!NexoSecretStore.hasApiKey(this)) {
                testStatus.text = "Sin credencial · guarda primero tu Gemini API key."
                return@setOnClickListener
            }

            testButton.isEnabled = false
            testStatus.text = "Probando conexión con Gemini…"
            val planner = MiaAgentPlanner(this)
            Thread {
                val result = planner.plan("Responde únicamente con una acción answer y el texto CONEXION_OK.")
                runOnUiThread {
                    testButton.isEnabled = true
                    result.onSuccess { plan ->
                        val connected = plan.actions.any {
                            it.tool == "answer" && it.text.contains("CONEXION_OK", ignoreCase = true)
                        }
                        if (connected) {
                            NexoRuntimeState.clearIssue(this, "Agent Brain")
                            testStatus.text = "✓ Gemini conectado · " + MiaAgentPlanner.resolveConfiguredModel(this)
                        } else {
                            testStatus.text = "Gemini respondió, pero el plan no tuvo el formato esperado."
                        }
                    }.onFailure { error ->
                        val detail = error.message.orEmpty()
                            .replace(Regex("AIza[\\w-]+"), "[credencial oculta]")
                            .take(320)
                        NexoRuntimeState.markIssue(this, "Agent Brain", detail.ifBlank { "Fallo de conexión con Gemini" })
                        testStatus.text = "✕ Gemini no conectado · " + when {
                            detail.contains("401") || detail.contains("403") -> "clave sin autorización o proyecto sin acceso."
                            detail.contains("429") -> "cuota/límite de Gemini alcanzado."
                            detail.contains("404") -> "modelo no disponible para esta clave/proyecto."
                            detail.contains("400") -> "Gemini rechazó la solicitud: " + detail
                            detail.isNotBlank() -> detail
                            else -> "revisa conexión, clave y proyecto."
                        }
                    }
                }
            }.start()
        }

        removeButton.setOnClickListener {
            NexoSecretStore.clearApiKey(this)
            Toast.makeText(this, "Credencial desconectada.", Toast.LENGTH_SHORT).show()
            finish()
        }

        saveButton.setOnClickListener {
            val apiKey = apiKeyInput.text.toString().trim()
            val model = modelInput.text.toString().trim()
                .ifBlank { MiaAgentPlanner.DEFAULT_MODEL }

            if (apiKey.isNotBlank() && !NexoSecretStore.saveApiKey(this, apiKey)) {
                NexoRuntimeState.markIssue(
                    this,
                    "Privacidad",
                    "No pude cifrar la credencial de inteligencia"
                )
                Toast.makeText(
                    this,
                    "No pude guardar la credencial de forma segura.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putString(MiaAgentPlanner.KEY_MODEL, model)
                .remove(MiaAgentPlanner.KEY_API_KEY)
                .apply()

            Toast.makeText(
                this,
                if (NexoSecretStore.hasApiKey(this)) {
                    "Inteligencia de NEXO configurada de forma segura."
                } else {
                    "Agrega una credencial para activar la inteligencia."
                },
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }
}
