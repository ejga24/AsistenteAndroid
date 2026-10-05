package com.eliel.asistente

import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
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

        val prefs = getSharedPreferences(MiaAgentPlanner.PREFS, MODE_PRIVATE)
        apiKeyInput.setText("")
        apiKeyInput.hint = if (NexoSecretStore.hasApiKey(this)) {
            "Credencial configurada · escribe una nueva para reemplazarla"
        } else {
            "Gemini API key"
        }
        modelInput.setText(MiaAgentPlanner.resolveConfiguredModel(this))

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
