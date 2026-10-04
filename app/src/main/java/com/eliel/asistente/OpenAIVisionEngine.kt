package com.eliel.asistente

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class OpenAIVisionEngine(private val context: Context) : NexoVisionEngine {

    companion object {
        private const val MAX_IMAGE_EDGE = 1280
    }

    override val engineName: String = "OpenAI Vision"
    override val requiresNetwork: Boolean = true

    override fun analyze(bitmap: Bitmap): Result<NexoVisionResult> {
        val prefs = context.getSharedPreferences(MiaAgentPlanner.PREFS, Context.MODE_PRIVATE)
        val apiKey = NexoSecretStore.readApiKey(context).trim()
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("NEXO_VISION_NOT_CONFIGURED"))
        }

        val model = MiaAgentPlanner.resolveConfiguredModel(context)

        return runCatching {
            val prepared = prepareBitmap(bitmap)
            val bytes = ByteArrayOutputStream().use { stream ->
                prepared.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                stream.toByteArray()
            }
            if (prepared !== bitmap) {
                prepared.recycle()
            }
            val dataUrl = "data:image/jpeg;base64," +
                Base64.encodeToString(bytes, Base64.NO_WRAP)

            val content = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "input_text")
                    put(
                        "text",
                        "Describe de forma breve y útil lo que ves. Prioriza objetos, texto visible, riesgos, señales y contexto práctico. Responde en español."
                    )
                })
                put(JSONObject().apply {
                    put("type", "input_image")
                    put("image_url", dataUrl)
                })
            }

            val input = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", content)
                })
            }

            val body = JSONObject().apply {
                put("model", model)
                put("input", input)
            }

            val connection = (URL("https://api.openai.com/v1/responses")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 45000
                doOutput = true
                setRequestProperty("Authorization", "Bearer " + apiKey)
                setRequestProperty("Content-Type", "application/json")
            }

            try {
                connection.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                val code = connection.responseCode
                val stream = if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

                if (code !in 200..299) {
                    throw IllegalStateException("Vision HTTP " + code)
                }
                if (responseText.isBlank()) {
                    throw IllegalStateException("Vision devolvió una respuesta vacía.")
                }

                val response = JSONObject(responseText)
                val summary = extractOutputText(response)
                NexoVisionResult(summary = summary)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun prepareBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxEdge = maxOf(width, height)
        if (maxEdge <= MAX_IMAGE_EDGE) return bitmap

        val scale = MAX_IMAGE_EDGE.toFloat() / maxEdge.toFloat()
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun extractOutputText(response: JSONObject): String {
        val output = response.optJSONArray("output")
            ?: throw IllegalStateException("Vision no devolvió respuesta.")

        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                val text = part.optString("text")
                if (text.isNotBlank()) return text
            }
        }

        throw IllegalStateException("Vision no devolvió texto.")
    }
}
