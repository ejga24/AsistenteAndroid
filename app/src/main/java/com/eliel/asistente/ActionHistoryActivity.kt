package com.eliel.asistente

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ActionHistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_action_history)
        val text = findViewById<TextView>(R.id.historyText)
        fun refresh() { text.text = NexoActionLog.formatted(this) }
        findViewById<Button>(R.id.clearHistoryButton).setOnClickListener {
            NexoActionLog.clear(this)
            refresh()
        }
        refresh()
    }
}
