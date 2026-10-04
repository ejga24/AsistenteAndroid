package com.eliel.asistente

import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object NexoConfirmationDialog {

    fun show(
        context: Context,
        actionLabel: String,
        reason: String,
        onApproved: () -> Unit,
        onRejected: () -> Unit
    ) {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_nexo_confirmation, null, false)

        view.findViewById<TextView>(R.id.confirmActionText).text = actionLabel
        view.findViewById<TextView>(R.id.confirmReasonText).text = reason

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(view)
            .setCancelable(true)
            .create()

        var resolved = false

        view.findViewById<Button>(R.id.confirmCancelButton).setOnClickListener {
            if (!resolved) {
                resolved = true
                dialog.dismiss()
                onRejected()
            }
        }

        view.findViewById<Button>(R.id.confirmApproveButton).setOnClickListener {
            if (!resolved) {
                resolved = true
                dialog.dismiss()
                onApproved()
            }
        }

        dialog.setOnCancelListener {
            if (!resolved) {
                resolved = true
                onRejected()
            }
        }

        dialog.show()
    }
}
