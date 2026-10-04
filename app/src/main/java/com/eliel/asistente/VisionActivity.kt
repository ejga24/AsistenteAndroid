package com.eliel.asistente

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale
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

    companion object {
        const val EXTRA_AUTO_ANALYZE = "nexo_vision_auto_analyze"
    }

    private lateinit var previewView: PreviewView
    private lateinit var stateText: TextView
    private lateinit var resultText: TextView
    private lateinit var resultCard: android.view.View
    private lateinit var captureButton: Button
    private var imageCapture: ImageCapture? = null
    private var autoAnalyzeRequested = false
    private var autoCaptureTriggered = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null

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
        autoAnalyzeRequested = intent.getBooleanExtra(EXTRA_AUTO_ANALYZE, false)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts
                if (engine == null) return@TextToSpeech
                var languageResult = engine.setLanguage(Locale("es", "PA"))
                if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                    languageResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    languageResult = engine.setLanguage(Locale("es"))
                }
                ttsReady = languageResult != TextToSpeech.LANG_MISSING_DATA &&
                    languageResult != TextToSpeech.LANG_NOT_SUPPORTED
                if (ttsReady) {
                    pendingSpeech?.let {
                        pendingSpeech = null
                        engine.speak(it, TextToSpeech.QUEUE_FLUSH, null, "nexo_vision_result")
                    }
                }
            }
        }

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
                stateText.text = if (autoAnalyzeRequested) {
                    "Vision lista · preparando análisis…"
                } else {
                    "Vision lista · análisis bajo demanda"
                }
                NexoActionLog.add(this, "Vision", "Cámara preparada")
                if (autoAnalyzeRequested && !autoCaptureTriggered) {
                    autoCaptureTriggered = true
                    previewView.postDelayed({ captureFrame() }, 650)
                }
            }.onFailure {
                stateText.text = "No pude iniciar la cámara."
                NexoActionLog.add(this, "Vision", it.message ?: "Error de cámara", false)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun speakAnalysisResult(text: String) {
        if (!autoAnalyzeRequested) return
        val engine = tts
        if (ttsReady && engine != null) {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nexo_vision_result")
        } else {
            pendingSpeech = text
        }
    }

    private fun cleanupCapturedFile(file: File) {
        runCatching {
            if (file.exists()) file.delete()
        }
    }

    private fun analyzeCapturedFrame(file: File) {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null) {
            val message = "No pude preparar la imagen para análisis."
            stateText.text = message
            captureButton.isEnabled = true
            cleanupCapturedFile(file)
            speakAnalysisResult(message)
            NexoActionLog.add(this, "Vision", "No pude decodificar la captura temporal", false)
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
                    speakAnalysisResult(it.summary)
                    cleanupCapturedFile(file)
                    NexoActionLog.add(this, "Vision", "Análisis completado y captura temporal eliminada")
                }.onFailure {
                    val guidance = NexoRecoveryPolicy.fromThrowable("Vision", it)
                    NexoRuntimeState.markIssue(this, "Vision", guidance.logMessage)
                    resultCard.visibility = android.view.View.GONE
                    stateText.text = guidance.userMessage
                    captureButton.isEnabled = true
                    speakAnalysisResult(guidance.userMessage)
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
                    val message = "No pude capturar la imagen."
                    stateText.text = message
                    captureButton.isEnabled = true
                    cleanupCapturedFile(file)
                    speakAnalysisResult(message)
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
    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
