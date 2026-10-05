package com.eliel.asistente

import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ActionHistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_action_history)
        val text = findViewById<TextView>(R.id.historyText)
        fun refresh() { text.text = NexoActionLog.formatted(this) }
        findViewById<Button>(R.id.clearHistoryButton).setOnClickListener {
            NexoConfirmationDialog.show(
                context = this,
                actionLabel = "Borrar historial local de NEXO",
                reason = "Esta acción elimina el registro visible de actividad guardado en la tablet.",
                onApproved = {
                    NexoActionLog.clear(this)
                    refresh()
                },
                onRejected = {}
            )
        }
        refresh()
    }
}
