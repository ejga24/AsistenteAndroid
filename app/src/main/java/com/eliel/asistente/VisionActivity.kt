package com.eliel.asistente

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File

class VisionActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var stateText: TextView
    private lateinit var resultText: TextView
    private lateinit var resultCard: android.view.View
    private lateinit var captureButton: Button
    private var imageCapture: ImageCapture? = null

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            stateText.text = "La cámara está desactivada. NEXO solo la usa cuando tú abres Vision."
            captureButton.isEnabled = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vision)

        previewView = findViewById(R.id.visionPreview)
        stateText = findViewById(R.id.visionStateText)
        resultText = findViewById(R.id.visionResultText)
        resultCard = findViewById(R.id.visionResultCard)
        captureButton = findViewById(R.id.visionCaptureButton)

        captureButton.setOnClickListener { captureFrame() }
        findViewById<Button>(R.id.visionCloseButton).setOnClickListener { finish() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            stateText.text = "NEXO necesita permiso de cámara para Vision."
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        stateText.text = "Preparando cámara…"
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
                stateText.text = "Vision lista · análisis bajo demanda"
                NexoActionLog.add(this, "Vision", "Cámara preparada")
            }.onFailure {
                stateText.text = "No pude iniciar la cámara."
                NexoActionLog.add(this, "Vision", it.message ?: "Error de cámara", false)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun cleanupCapturedFile(file: File) {
        runCatching {
            if (file.exists()) file.delete()
        }
    }

    private fun analyzeCapturedFrame(file: File) {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null) {
            stateText.text = "No pude preparar la imagen para análisis."
            captureButton.isEnabled = true
            return
        }

        Thread {
            val result = OpenAIVisionEngine(this).analyze(bitmap)
            runOnUiThread {
                result.onSuccess {
                    NexoRuntimeState.clearIssue(this, "Vision")
                    resultCard.visibility = android.view.View.VISIBLE
                    resultText.text = it.summary
                    stateText.text = "Análisis completado. La captura temporal fue eliminada."
                    captureButton.isEnabled = true
                    cleanupCapturedFile(file)
                    NexoActionLog.add(this, "Vision", "Análisis completado y captura temporal eliminada")
                }.onFailure {
                    val guidance = NexoRecoveryPolicy.fromThrowable("Vision", it)
                    NexoRuntimeState.markIssue(this, "Vision", guidance.logMessage)
                    resultCard.visibility = android.view.View.GONE
                    stateText.text = guidance.userMessage
                    captureButton.isEnabled = true
                    cleanupCapturedFile(file)
                    NexoActionLog.add(this, "Vision", guidance.logMessage, false)
                }
            }
        }.start()
    }

    private fun captureFrame() {
        val capture = imageCapture ?: run {
            stateText.text = "La cámara todavía no está lista."
            return
        }

        captureButton.isEnabled = false
        stateText.text = "Capturando…"

        val file = File(cacheDir, "nexo_vision_latest.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()

        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    stateText.text = "Imagen capturada. Analizando…"
                    NexoActionLog.add(this@VisionActivity, "Vision", "Fotograma capturado localmente")
                    analyzeCapturedFrame(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    stateText.text = "No pude capturar la imagen."
                    captureButton.isEnabled = true
                    NexoActionLog.add(
                        this@VisionActivity,
                        "Vision",
                        exception.message ?: "Error de captura",
                        false
                    )
                }
            }
        )
    }
}
