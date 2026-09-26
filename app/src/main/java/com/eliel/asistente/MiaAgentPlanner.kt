package com.eliel.asistente

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class MiaAgentDecision(
    val tool: String,
    val app: String = "",
    val text: String = "",
    val target: String = "",
    val newChat: Boolean = false,
    val speech: String = ""
)

class MiaAgentPlanner(private val context: Context) {

    companion object {
        const val PREFS = "mia_ai"
        const val KEY_API_KEY = "openai_api_key"
        const val KEY_MODEL = "openai_model"
        const val DEFAULT_MODEL = "gpt-5.6-luna"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isConfigured(): Boolean =
        !prefs.getString(KEY_API_KEY, "").isNullOrBlank()

    fun plan(userRequest: String): Result<MiaAgentDecision> {
        val apiKey = prefs.getString(KEY_API_KEY, "")?.trim().orEmpty()
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("MIA_AI_NOT_CONFIGURED"))
        }

        val model = prefs.getString(KEY_MODEL, DEFAULT_MODEL)?.trim()
            .takeUnless { it.isNullOrBlank() } ?: DEFAULT_MODEL

        return runCatching {
            val body = JSONObject().apply {
                put("model", model)
                put("reasoning", JSONObject().put("effort", "low"))
                put(
                    "instructions",
                    """
                    Eres el planificador de Mía, un asistente de voz que controla una tableta Android.
                    Convierte la solicitud del usuario en UNA acción concreta y segura.
                    No inventes datos que no estén en la solicitud.
                    Usa:
                    - open_app: abrir una aplicación por nombre.
                    - waze: navegar a un destino.
                    - spotify: buscar/reproducir música en Spotify.
                    - youtube: buscar/reproducir contenido en YouTube.
                    - chatgpt: abrir ChatGPT, opcionalmente crear chat nuevo y escribir/enviar una consulta.
                    - tap_text: tocar un elemento visible por su texto.
                    - type_text: escribir texto en el campo editable visible.
                    - back: volver atrás.
                    - home: ir a inicio.
                    - answer: responder verbalmente sin ejecutar acción.
                    - clarify: pedir una aclaración imprescindible.
                    En speech escribe una confirmación corta y natural en español de Panamá.
                    """.trimIndent()
                )
                put("input", userRequest)
                put(
                    "text",
                    JSONObject().put(
                        "format",
                        JSONObject().apply {
                            put("type", "json_schema")
                            put("name", "mia_android_action")
                            put("strict", true)
                            put(
                                "schema",
                                JSONObject().apply {
                                    put("type", "object")
                                    put("additionalProperties", false)
                                    put(
                                        "properties",
                                        JSONObject().apply {
                                            put(
                                                "tool",
                                                JSONObject().apply {
                                                    put("type", "string")
                                                    put(
                                                        "enum",
                                                        JSONArray(
                                                            listOf(
                                                                "open_app", "waze", "spotify", "youtube",
                                                                "chatgpt", "tap_text", "type_text",
                                                                "back", "home", "answer", "clarify"
                                                            )
                                                        )
                                                    )
                                                }
                                            )
                                            put("app", JSONObject().put("type", "string"))
                                            put("text", JSONObject().put("type", "string"))
                                            put("target", JSONObject().put("type", "string"))
                                            put("new_chat", JSONObject().put("type", "boolean"))
                                            put("speech", JSONObject().put("type", "string"))
                                        }
                                    )
                                    put(
                                        "required",
                                        JSONArray(
                                            listOf(
                                                "tool", "app", "text", "target",
                                                "new_chat", "speech"
                                            )
                                        )
                                    )
                                }
                            )
                        }
                    )
                )
            }

            val connection = (URL("https://api.openai.com/v1/responses").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 30000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
            }

            connection.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = connection.responseCode
            val responseText = (if (code in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader()
                .use { it.readText() }

            if (code !in 200..299) {
                throw IllegalStateException("OpenAI HTTP $code: $responseText")
            }

            val responseJson = JSONObject(responseText)
            val structuredText = extractOutputText(responseJson)
            val action = JSONObject(structuredText)

            MiaAgentDecision(
                tool = action.getString("tool"),
                app = action.optString("app"),
                text = action.optString("text"),
                target = action.optString("target"),
                newChat = action.optBoolean("new_chat", false),
                speech = action.optString("speech")
            )
        }
    }

    private fun extractOutputText(response: JSONObject): String {
        val output = response.optJSONArray("output")
            ?: throw IllegalStateException("La IA no devolvió una acción.")

        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                val text = part.optString("text")
                if (text.isNotBlank()) return text
            }
        }
        throw IllegalStateException("La IA no devolvió texto estructurado.")
    }
}
