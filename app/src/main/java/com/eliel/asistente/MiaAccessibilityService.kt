package com.eliel.asistente

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.LinearLayout
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class NexoAccessibilityActionResult(
    val success: Boolean,
    val detail: String
)

class MiaAccessibilityService : AccessibilityService() {

    companion object {
        private const val LEGACY_PREFS = "mia_automation"
        private const val KEY_QUEUE = "action_queue"
        private const val PRIVATE_QUEUE_KEY = "accessibility_action_queue"
        private const val RESULT_PREFS = "nexo_accessibility_results"
        private const val MAX_QUEUE = 12
        private const val GENERIC_TTL_MS = 45_000L
        private const val CHATGPT_TTL_MS = 90_000L
        private val queueLock = Any()
        @Volatile private var activeInstance: MiaAccessibilityService? = null

        fun showNexoVoiceOverlay(state: String) {
            activeInstance?.renderVoiceOverlay(state)
        }

        fun hideNexoVoiceOverlay(delayMs: Long = 700L) {
            activeInstance?.hideVoiceOverlay(delayMs)
        }

        private const val TYPE_CHATGPT = "chatgpt"
        private const val TYPE_GENERIC = "generic"

        fun queueChatGptRequest(context: Context, text: String, newChat: Boolean): String {
            val id = UUID.randomUUID().toString()
            val item = JSONObject().apply {
                    put("id", id)
                    put("type", TYPE_CHATGPT)
                    put("package", "com.openai.chatgpt")
                    put("text", text)
                    put("new_chat", newChat)
                    put("created", System.currentTimeMillis())
                }
            if (!enqueue(context, item)) {
                recordResult(
                    context,
                    item,
                    false,
                    "No pude proteger la cola de acciones de pantalla."
                )
            }
            return id
        }

        fun queueGenericAction(
            context: Context,
            action: String,
            value: String = "",
            targetPackage: String = "*"
        ): String {
            val id = UUID.randomUUID().toString()
            val item = JSONObject().apply {
                    put("id", id)
                    put("type", TYPE_GENERIC)
                    put("package", targetPackage.ifBlank { "*" })
                    put("action", action)
                    put("value", value)
                    put("created", System.currentTimeMillis())
                }
            if (!enqueue(context, item)) {
                recordResult(
                    context,
                    item,
                    false,
                    "No pude proteger la cola de acciones de pantalla."
                )
            }
            return id
        }

        fun hasPending(context: Context): Boolean =
            peek(context) != null

        fun consumeResult(context: Context, id: String): NexoAccessibilityActionResult? {
            val prefs = context.getSharedPreferences(RESULT_PREFS, Context.MODE_PRIVATE)
            val raw = prefs.getString(id, null) ?: return null
            prefs.edit().remove(id).apply()

            return runCatching {
                val json = JSONObject(raw)
                NexoAccessibilityActionResult(
                    success = json.optBoolean("success", false),
                    detail = json.optString("detail")
                )
            }.getOrNull()
        }

        private fun enqueue(context: Context, item: JSONObject): Boolean =
            synchronized(queueLock) {
                val current = readQueue(context)
                val next = JSONArray()
                val start = (current.length() - (MAX_QUEUE - 1)).coerceAtLeast(0)

                for (i in start until current.length()) {
                    next.put(current.getJSONObject(i))
                }
                next.put(item)
                writeQueue(context, next)
            }

        private fun readQueue(context: Context): JSONArray {
            NexoPrivateStore.getString(context, PRIVATE_QUEUE_KEY)?.let { raw ->
                return runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
            }

            val legacyPrefs = context.getSharedPreferences(
                LEGACY_PREFS,
                Context.MODE_PRIVATE
            )
            val legacyRaw = legacyPrefs.getString(KEY_QUEUE, null).orEmpty()
            if (legacyRaw.isBlank()) return JSONArray()

            val parsed = runCatching { JSONArray(legacyRaw) }.getOrElse { JSONArray() }
            if (NexoPrivateStore.putString(context, PRIVATE_QUEUE_KEY, parsed.toString())) {
                legacyPrefs.edit().remove(KEY_QUEUE).apply()
            }
            return parsed
        }

        private fun peek(context: Context): JSONObject? =
            synchronized(queueLock) {
                var queue = readQueue(context)
                var changed = false

                while (queue.length() > 0) {
                    val item = queue.optJSONObject(0)
                    if (item == null) {
                        queue = withoutHead(queue)
                        changed = true
                        continue
                    }

                    val created = item.optLong("created", 0L)
                    val ttl = if (item.optString("type") == TYPE_CHATGPT) {
                        CHATGPT_TTL_MS
                    } else {
                        GENERIC_TTL_MS
                    }

                    val expired = created <= 0L ||
                        System.currentTimeMillis() - created > ttl

                    if (!expired) {
                        if (changed && !writeQueue(context, queue)) {
                            clearQueueStorage(context)
                            NexoRuntimeState.markIssue(
                                context,
                                "Control de aplicaciones",
                                "No pude actualizar de forma segura la cola de acciones"
                            )
                            return@synchronized null
                        }
                        return@synchronized item
                    }

                    recordResult(
                        context,
                        item,
                        false,
                        "La acción expiró antes de poder ejecutarse."
                    )
                    queue = withoutHead(queue)
                    changed = true
                }

                if (changed && !writeQueue(context, queue)) {
                    clearQueueStorage(context)
                    NexoRuntimeState.markIssue(
                        context,
                        "Control de aplicaciones",
                        "No pude limpiar de forma segura la cola de acciones"
                    )
                }
                null
            }

        private fun withoutHead(queue: JSONArray): JSONArray {
            val next = JSONArray()
            for (i in 1 until queue.length()) {
                next.put(queue.getJSONObject(i))
            }
            return next
        }

        private fun writeQueue(context: Context, queue: JSONArray): Boolean {
            val saved = NexoPrivateStore.putString(
                context,
                PRIVATE_QUEUE_KEY,
                queue.toString()
            )
            if (saved) {
                context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_QUEUE)
                    .apply()
            }
            return saved
        }

        private fun clearQueueStorage(context: Context) {
            NexoPrivateStore.remove(context, PRIVATE_QUEUE_KEY)
            context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_QUEUE)
                .apply()
        }

        private fun removeHead(context: Context) {
            synchronized(queueLock) {
                val next = withoutHead(readQueue(context))
                if (!writeQueue(context, next)) {
                    // Fall closed: losing queued follow-up actions is safer than
                    // repeating an already executed UI action.
                    clearQueueStorage(context)
                    NexoRuntimeState.markIssue(
                        context,
                        "Control de aplicaciones",
                        "No pude persistir el avance de la cola; se descartaron acciones pendientes"
                    )
                }
            }
        }

        private fun recordResult(
            context: Context,
            item: JSONObject,
            success: Boolean,
            detail: String
        ) {
            val id = item.optString("id")
            if (id.isBlank()) return

            val json = JSONObject().apply {
                put("success", success)
                put("detail", detail)
                put("time", System.currentTimeMillis())
            }

            context.getSharedPreferences(RESULT_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(id, json.toString())
                .apply()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var busy = false
    private var videoFullscreen = false
    private var latestVoiceState = "ready"
    private val videoPackages = setOf(
        "com.google.android.youtube", "com.netflix.mediaclient",
        "com.disney.disneyplus", "com.hbo.hbonow",
        "com.hbo.max", "org.videolan.vlc", "com.amazon.avod.thirdpartyclient"
    )
    private var voiceOverlay: View? = null
    private var voiceFace: NexoFaceView? = null
    private var voiceStatus: TextView? = null
    private val overlayHandler = Handler(Looper.getMainLooper())
    private var voicePulse: ValueAnimator? = null
    private var voiceTravel: ValueAnimator? = null
    private var voicePlayful: ValueAnimator? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        serviceInfo = serviceInfo.apply {
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    private fun renderVoiceOverlay(state: String) {
        latestVoiceState = state
        if (videoFullscreen) {
            if (voiceOverlay != null) hideVoiceOverlay(0)
            return
        }
        overlayHandler.removeCallbacksAndMessages(null)
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        // Ready is a resting expression, not a reason to remove NEXO.

        val panel = voiceOverlay ?: LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            elevation = 0f
            val faceSize = (resources.getDimension(R.dimen.nexo_orb_size)).toInt()
            minimumWidth = faceSize
            minimumHeight = faceSize

            voiceFace = NexoFaceView(this@MiaAccessibilityService).apply {
                layoutParams = LinearLayout.LayoutParams(faceSize, faceSize)
            }
            voiceStatus = TextView(this@MiaAccessibilityService).apply {
                gravity = Gravity.CENTER
                textSize = 10f
                setTextColor(Color.WHITE)
            }
            addView(voiceFace)
            addView(voiceStatus)
        }

        val face = voiceFace ?: return
        val status = voiceStatus ?: return
        status.text = when (state) {
            "listening" -> "NEXO\nTE ESCUCHO"
            "processing" -> "NEXO\nPROCESANDO"
            "executing" -> "NEXO\nEJECUTANDO"
            "speaking" -> "NEXO\nHABLANDO"
            "surprised" -> "NEXO"
            "wink" -> "NEXO"
            "success" -> "NEXO\nLISTO"
            else -> "NEXO"
        }
        face.showMode(state)

        fun paint(strokeAlpha: Int, strokeWidth: Int = 3) {
            panel.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14f
                setColor(Color.TRANSPARENT)
                setStroke(0, Color.TRANSPARENT)
            }
        }

        voicePulse?.cancel()
        voicePulse = null
        panel.animate().cancel()
        panel.scaleX = 1f
        panel.scaleY = 1f
        panel.alpha = 1f
        face.translationX = 0f
        paint(if (state == "listening") 255 else 205)

        if (voiceOverlay == null) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 28
                y = 72
            }
            runCatching { windowManager.addView(panel, params) }
                .onSuccess { voiceOverlay = panel }
        }

        if (voiceOverlay != null && voiceTravel == null) {
            val metrics = resources.displayMetrics
            val maxX = (metrics.widthPixels - panel.width.coerceAtLeast(160) - 32).coerceAtLeast(0)
            val maxY = (metrics.heightPixels - panel.height.coerceAtLeast(180) - 120).coerceAtLeast(0)
            voiceTravel = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 19000L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener { animation ->
                    val fraction = animation.animatedValue as Float
                    val params = panel.layoutParams as? WindowManager.LayoutParams ?: return@addUpdateListener
                    params.gravity = Gravity.TOP or Gravity.LEFT
                    val t = fraction * (2.0 * Math.PI)
                    val fx = (0.5 + 0.44 * kotlin.math.sin(t * 2.0 + 0.3)).toFloat()
                    val fy = (0.5 + 0.43 * kotlin.math.sin(t * 3.0 + 1.2)).toFloat()
                    params.x = (16f + (maxX - 16).coerceAtLeast(0) * fx).toInt()
                    params.y = (56f + (maxY - 56).coerceAtLeast(0) * fy).toInt()
                    runCatching { windowManager.updateViewLayout(panel, params) }
                }
                start()
            }
        }

        // Subtle occasional hops and turns, independent of screen travel.
        if (voiceOverlay != null && voicePlayful == null) {
            voicePlayful = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 10500L
                repeatCount = ValueAnimator.INFINITE
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener { animation ->
                    val t = animation.animatedValue as Float
                    val hop = if (t in 0.64f..0.78f)
                        kotlin.math.sin(((t - 0.64f) / 0.14f * Math.PI).toFloat()) * 22f
                    else 0f
                    panel.translationY = -hop
                    val turn = if (t in 0.32f..0.48f)
                        kotlin.math.sin(((t - 0.32f) / 0.16f * Math.PI).toFloat()) * 14f
                    else 0f
                    face.rotation = turn
                }
                start()
            }
        }

        if (state == "listening") {
            voicePulse = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 620L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animation ->
                    val phase = animation.animatedValue as Float
                    panel.scaleX = 1f + (0.07f * phase)
                    panel.scaleY = 1f + (0.07f * phase)
                    panel.alpha = 0.80f + (0.20f * phase)
                    paint((145 + (110 * phase)).toInt(), if (phase > 0.55f) 5 else 3)
                }
                start()
            }
        }
    }

    private fun hideVoiceOverlay(delayMs: Long) {
        voicePlayful?.cancel()
        voicePlayful = null
        voiceOverlay?.translationY = 0f
        voiceFace?.rotation = 0f
        voiceTravel?.cancel()
        voiceTravel = null
        voicePulse?.cancel()
        voicePulse = null
        overlayHandler.removeCallbacksAndMessages(null)
        overlayHandler.postDelayed({
            val view = voiceOverlay ?: return@postDelayed
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            runCatching { windowManager.removeView(view) }
            voiceOverlay = null
            voiceFace = null
            voiceStatus = null
        }, delayMs)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (activeInstance === this) activeInstance = null
        hideVoiceOverlay(0)
        overlayHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            val pkg = rootInActiveWindow?.packageName?.toString().orEmpty()
            val isVideoApp = pkg in videoPackages
            // In immersive playback, system bars disappear from the accessible
            // window list. This is a best-effort signal, not a video-content API.
            val hasSystemBar = windows.any { window ->
                window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM &&
                    (window.root?.packageName?.toString() == "com.android.systemui")
            }
            val immersiveVideo = isVideoApp && !hasSystemBar
            if (immersiveVideo != videoFullscreen) {
                videoFullscreen = immersiveVideo
                if (immersiveVideo) hideVoiceOverlay(0)
                else renderVoiceOverlay(latestVoiceState)
            }
        }
        if (busy) return

        val pending = peek(this) ?: return
        val targetPackage = pending.optString("package")
        val activePackage = event?.packageName?.toString() ?: return

        if (targetPackage != "*" && activePackage != targetPackage) return

        busy = true
        handler.postDelayed({ executePendingRequest() }, 450)
    }

    private fun executePendingRequest() {
        val pending = peek(this)
        if (pending == null) {
            busy = false
            return
        }

        when (pending.optString("type")) {
            TYPE_CHATGPT -> executeChatGpt(pending)
            TYPE_GENERIC -> executeGenericAction(pending)
            else -> {
                recordResult(this, pending, false, "Tipo de acción no soportado.")
                removeHead(this)
                busy = false
            }
        }
    }

    private fun executeChatGpt(pending: JSONObject) {
        val text = pending.optString("text").trim()
        val newChat = pending.optBoolean("new_chat", true)

        if (text.isBlank()) {
            recordResult(this, pending, false, "La consulta estaba vacía.")
            removeHead(this)
            busy = false
            return
        }

        if (newChat) {
            clickFirstMatching(
                "Nuevo chat", "New chat", "Nueva conversación", "New conversation",
                "Iniciar nuevo chat", "Start new chat"
            )
        }

        handler.postDelayed({
            val root = rootInActiveWindow
            if (root == null) {
                busy = false
                return@postDelayed
            }

            val editor = findEditableNode(root)
            if (editor == null) {
                busy = false
                handler.postDelayed({ executePendingRequest() }, 700)
                return@postDelayed
            }

            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            editor.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

            handler.postDelayed({
                var sent = clickFirstMatching(
                    "Enviar", "Send", "Enviar mensaje", "Send message"
                ) || clickNodeByDescription(
                    rootInActiveWindow,
                    listOf("Enviar", "Send", "Enviar mensaje", "Send message")
                )

                if (!sent) {
                    sent = findLikelySendButton(rootInActiveWindow)
                        ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                }

                recordResult(
                    this,
                    pending,
                    sent,
                    if (sent) "Consulta enviada." else "No encontré el control para enviar."
                )
                removeHead(this)
                busy = false
                triggerNext()
            }, 450)
        }, if (newChat) 850 else 350)
    }

    private fun executeGenericAction(pending: JSONObject) {
        val action = pending.optString("action")
        val value = pending.optString("value")

        val success = when (action) {
            "tap_text" -> clickFirstMatching(value)
            "type_text" -> {
                val editor = findEditableNode(rootInActiveWindow)
                if (editor != null) {
                    val args = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            value
                        )
                    }
                    editor.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                } else {
                    false
                }
            }
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "spotify_play_query" -> {
                // Spotify can land on Search, artist results, or the artist page depending
                // on app version/account. Walk forward until a real Play control is pressed.
                val alreadyPlayable = clickFirstMatching(
                    "Reproducir", "Play", "Reproducción aleatoria",
                    "Reproduccion aleatoria", "Shuffle", "Shuffle play"
                ) || clickNodeByDescription(
                    rootInActiveWindow,
                    listOf("Reproducir", "Play", "Reproducción aleatoria",
                        "Reproduccion aleatoria", "Shuffle", "Shuffle play")
                )
                if (alreadyPlayable) {
                    true
                } else {
                    val selected = clickFirstMatching(value)
                    if (selected) {
                        handler.postDelayed({
                            val played = clickFirstMatching(
                                "Reproducir", "Play", "Reproducción aleatoria",
                                "Reproduccion aleatoria", "Shuffle", "Shuffle play"
                            ) || clickNodeByDescription(
                                rootInActiveWindow,
                                listOf("Reproducir", "Play", "Reproducción aleatoria",
                                    "Reproduccion aleatoria", "Shuffle", "Shuffle play")
                            )
                            if (!played) {
                                // Some Spotify layouts expose the first song title but not
                                // a labelled Play button; activate the first clickable result.
                                clickFirstVisibleResult(value)
                            }
                        }, 1200)
                    }
                    selected
                }
            }
            else -> false
        }

        recordResult(
            this,
            pending,
            success,
            if (success) "Acción ejecutada." else "No se pudo ejecutar la acción visible."
        )
        removeHead(this)
        busy = false
        triggerNext()
    }

    private fun triggerNext() {
        val next = peek(this) ?: return
        val targetPackage = next.optString("package")
        val activePackage = rootInActiveWindow?.packageName?.toString()

        if (targetPackage == "*" || targetPackage == activePackage) {
            busy = true
            handler.postDelayed({ executePendingRequest() }, 350)
        } else {
            busy = false
            // Espera un AccessibilityEvent de la aplicación objetivo.
        }
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable && node.isVisibleToUser) return node

        for (i in 0 until node.childCount) {
            val found = findEditableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun clickFirstVisibleResult(label: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val matches = root.findAccessibilityNodeInfosByText(label)
        for (node in matches) {
            if (!node.isVisibleToUser) continue
            val clickable = findClickableParent(node)
            if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
        }
        return false
    }

    private fun clickFirstMatching(vararg labels: String): Boolean {
        val root = rootInActiveWindow ?: return false

        for (label in labels) {
            val matches = root.findAccessibilityNodeInfosByText(label)
            for (node in matches) {
                val clickable = findClickableParent(node)
                if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                    return true
                }
            }
        }
        return false
    }

    private fun clickNodeByDescription(
        node: AccessibilityNodeInfo?,
        labels: List<String>
    ): Boolean {
        if (node == null) return false

        val description = node.contentDescription?.toString()?.trim()
        if (node.isVisibleToUser && description != null &&
            labels.any { it.equals(description, ignoreCase = true) }
        ) {
            val clickable = findClickableParent(node)
            if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                return true
            }
        }

        for (i in 0 until node.childCount) {
            if (clickNodeByDescription(node.getChild(i), labels)) return true
        }
        return false
    }

    private fun findLikelySendButton(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
        val text = node.text?.toString()?.lowercase().orEmpty()
        val className = node.className?.toString().orEmpty()

        val looksLikeButton = node.isClickable &&
            (className.contains("Button", ignoreCase = true) || desc.isNotBlank())

        if (looksLikeButton &&
            (desc.contains("send") || desc.contains("enviar") ||
                text.contains("send") || text.contains("enviar"))
        ) {
            return node
        }

        for (i in 0 until node.childCount) {
            val found = findLikelySendButton(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun findClickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        var hops = 0
        while (current != null && hops < 5) {
            if (current.isClickable) return current
            current = current.parent
            hops++
        }
        return node
    }

}