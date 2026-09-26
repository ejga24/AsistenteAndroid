package com.eliel.asistente

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AgentSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agent_settings)

        val apiKeyInput = findViewById<EditText>(R.id.apiKeyInput)
        val modelInput = findViewById<EditText>(R.id.modelInput)
        val saveButton = findViewById<Button>(R.id.saveAiButton)

        val prefs = getSharedPreferences(MiaAgentPlanner.PREFS, MODE_PRIVATE)
        apiKeyInput.setText(prefs.getString(MiaAgentPlanner.KEY_API_KEY, ""))
        modelInput.setText(
            prefs.getString(
                MiaAgentPlanner.KEY_MODEL,
                MiaAgentPlanner.DEFAULT_MODEL
            )
        )

        saveButton.setOnClickListener {
            val apiKey = apiKeyInput.text.toString().trim()
            val model = modelInput.text.toString().trim()
                .ifBlank { MiaAgentPlanner.DEFAULT_MODEL }

            prefs.edit()
                .putString(MiaAgentPlanner.KEY_API_KEY, apiKey)
                .putString(MiaAgentPlanner.KEY_MODEL, model)
                .apply()

            Toast.makeText(
                this,
                if (apiKey.isBlank()) "IA desactivada." else "Inteligencia de Mía configurada.",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }
}
