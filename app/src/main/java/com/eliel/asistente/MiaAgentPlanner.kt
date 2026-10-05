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
    val newChat: Boolean = false
)

data class NexoAgentPlan(
    val actions: List<MiaAgentDecision>,
    val speech: String = ""
)

class MiaAgentPlanner(private val context: Context) {

    companion object {
        const val PREFS = "mia_ai"
        const val KEY_API_KEY = "openai_api_key"
        const val KEY_MODEL = "openai_model"
        const val DEFAULT_MODEL = "gpt-6-luna"
        private const val LEGACY_MODEL = "gpt-5.6-luna"
        private const val MAX_ACTIONS = 6

        fun resolveConfiguredModel(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val saved = prefs.getString(KEY_MODEL, DEFAULT_MODEL)
                ?.trim()
                .orEmpty()

            val resolved = when {
                saved.isBlank() -> DEFAULT_MODEL
                saved.equals(LEGACY_MODEL, ignoreCase = true) -> DEFAULT_MODEL
                else -> saved
            }

            if (resolved != saved) {
                prefs.edit().putString(KEY_MODEL, resolved).apply()
            }
            return resolved
        }
    }


    fun isConfigured(): Boolean =
        NexoSecretStore.hasApiKey(context)

    fun plan(userRequest: String): Result<NexoAgentPlan> {
        val apiKey = NexoSecretStore.readApiKey(context).trim()
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("NEXO_AI_NOT_CONFIGURED"))
        }

        val model = resolveConfiguredModel(context)

        return runCatching {
            val actionSchema = JSONObject().apply {
                put("type", "object")
                put("additionalProperties", false)
                put("properties", JSONObject().apply {
                    put("tool", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray(listOf(
                            "open_app", "waze", "spotify", "youtube", "chatgpt", "vision",
                            "tap_text", "type_text", "back", "home",
                            "set_volume", "set_brightness", "car_mode",
                            "answer", "clarify"
                        )))
                    })
                    put("app", JSONObject().put("type", "string"))
                    put("text", JSONObject().put("type", "string"))
                    put("target", JSONObject().put("type", "string"))
                    put("new_chat", JSONObject().put("type", "boolean"))
                })
                put("required", JSONArray(listOf("tool", "app", "text", "target", "new_chat")))
            }

            val body = JSONObject().apply {
                put("model", model)
                put("store", false)
                put("max_output_tokens", 900)
                put("reasoning", JSONObject().put("effort", "low"))
                put(
                    "instructions",
                    """
                    Eres el cerebro de planificación de NEXO, un agente Android en una HONOR Pad.
                    Convierte la solicitud del usuario en un plan de 1 a $MAX_ACTIONS acciones, en el orden correcto.
                    Divide solicitudes compuestas en varias acciones. No inventes destinos, nombres, textos ni aplicaciones.
                    Herramientas:
                    - open_app: abrir una aplicación; app = nombre visible.
                    - waze: navegar; target = destino.
                    - spotify: buscar/reproducir; text = búsqueda.
                    - youtube: buscar/reproducir; text = búsqueda.
                    - chatgpt: abrir ChatGPT y escribir/enviar; text = consulta; new_chat según corresponda.
                    - vision: abrir el módulo de cámara/visión bajo demanda.
                    - tap_text: tocar un control visible; target = texto; app = aplicación visible objetivo cuando se conozca.
                    - type_text: escribir en el campo editable visible; text = contenido; app = aplicación visible objetivo cuando se conozca.
                    - back: volver.
                    - home: ir a inicio.
                    - set_volume: volumen multimedia; target = entero 0..100.
                    - set_brightness: brillo de pantalla; target = entero 0..100.
                    - car_mode: target = "on" u "off".
                    - answer: solo responder; text = respuesta.
                    - clarify: falta un dato imprescindible; text = pregunta corta.
                    Reglas:
                    1) Si el usuario pide varias cosas, genera varias acciones.
                    2) No uses Accessibility si existe una herramienta directa.
                    3) No incluyas acciones que el usuario no pidió, salvo ajustes estrictamente necesarios para completar una orden.
                    4) Si una acción es ambigua y no puede ejecutarse con seguridad, usa clarify y no continúes después.
                    5) Para tap_text y type_text, completa app cuando puedas identificar la aplicación objetivo; no uses otra app distinta.

                    6) Si usas vision, vision debe ser la última acción del plan. No inventes ni anticipes lo que la cámara verá.
                    7) No encadenes acciones que dependan del resultado de vision hasta que exista una herramienta explícita para ese resultado.
                    8) speech es una confirmación breve del plan completo en español natural de Panamá.
                    """.trimIndent()
                )
                put("input", userRequest)
                put(
                    "text",
                    JSONObject().put(
                        "format",
                        JSONObject().apply {
                            put("type", "json_schema")
                            put("name", "nexo_android_plan")
                            put("strict", true)
                            put("schema", JSONObject().apply {
                                put("type", "object")
                                put("additionalProperties", false)
                                put("properties", JSONObject().apply {
                                    put("actions", JSONObject().apply {
                                        put("type", "array")
                                        put("minItems", 1)
                                        put("maxItems", MAX_ACTIONS)
                                        put("items", actionSchema)
                                    })
                                    put("speech", JSONObject().put("type", "string"))
                                })
                                put("required", JSONArray(listOf("actions", "speech")))
                            })
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
                    throw IllegalStateException("OpenAI HTTP $code")
                }
                if (responseText.isBlank()) {
                    throw IllegalStateException("OpenAI devolvió una respuesta vacía.")
                }

                val responseJson = JSONObject(responseText)
                val structuredText = extractOutputText(responseJson)
                val planJson = JSONObject(structuredText)
                val actionArray = planJson.getJSONArray("actions")
                val actions = buildList {
                    for (i in 0 until minOf(actionArray.length(), MAX_ACTIONS)) {
                        val action = actionArray.getJSONObject(i)
                        add(
                            MiaAgentDecision(
                                tool = action.getString("tool"),
                                app = action.optString("app"),
                                text = action.optString("text"),
                                target = action.optString("target"),
                                newChat = action.optBoolean("new_chat", false)
                            )
                        )
                    }
                }

                NexoAgentPlan(
                    actions = actions,
                    speech = planJson.optString("speech")
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun extractOutputText(response: JSONObject): String {
        val output = response.optJSONArray("output")
            ?: throw IllegalStateException("La IA no devolvió un plan.")

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
