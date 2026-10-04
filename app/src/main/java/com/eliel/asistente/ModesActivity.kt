package com.eliel.asistente

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ModesActivity : AppCompatActivity() {

    private lateinit var currentModeText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_modes)

        currentModeText = findViewById(R.id.currentModeText)

        findViewById<Button>(R.id.modeNormalButton).setOnClickListener {
            applyNormalMode()
        }

        findViewById<Button>(R.id.modeCarButton).setOnClickListener {
            applyCarMode()
        }
    }

    override fun onResume() {
        super.onResume()
        NexoModeManager.applyWindowProfile(this)
        refresh()
    }

    private fun refresh() {
        currentModeText.text = "Modo actual · " + NexoModeManager.current(this).displayName
    }

    private fun applyNormalMode() {
        NexoModeManager.set(this, NexoMode.NORMAL)
        NexoModeManager.applyWindowProfile(this, NexoMode.NORMAL)
        refresh()
    }

    private fun applyCarMode() {
        NexoModeManager.set(this, NexoMode.CAR)
        NexoModeManager.applyWindowProfile(this, NexoMode.CAR)
        refresh()
    }
}
