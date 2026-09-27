package com.eliel.asistente

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

object UberOcrReader {

    fun readRate(
        bitmap: Bitmap,
        onResult: (Double?, String) -> Unit
    ) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { result ->
                val lines = result.textBlocks.flatMap { block ->
                    block.lines.map { it.text }
                }
                val rate = UberRateParser.extract(lines)
                onResult(rate, lines.joinToString(" | "))
            }
            .addOnFailureListener {
                onResult(null, "")
            }
            .addOnCompleteListener {
                recognizer.close()
            }
    }
}
