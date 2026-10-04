package com.eliel.asistente

import android.os.Bundle
import android.view.WindowManager
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
        refresh()
    }

    private fun refresh() {
        currentModeText.text = "Modo actual · " + NexoModeManager.current(this).displayName
    }

    private fun applyNormalMode() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        NexoModeManager.set(this, NexoMode.NORMAL)
        refresh()
    }

    private fun applyCarMode() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            screenBrightness = 0.85f
        }
        NexoModeManager.set(this, NexoMode.CAR)
        refresh()
    }
}
