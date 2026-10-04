package com.eliel.asistente

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

class MiaAccessibilityService : AccessibilityService() {

    companion object {
        private const val PREFS = "mia_automation"
        private const val KEY_QUEUE = "action_queue"
        private const val MAX_QUEUE = 12
        private val queueLock = Any()

        private const val TYPE_CHATGPT = "chatgpt"
        private const val TYPE_GENERIC = "generic"

        fun queueChatGptRequest(context: Context, text: String, newChat: Boolean) {
            enqueue(
                context,
                JSONObject().apply {
                    put("type", TYPE_CHATGPT)
                    put("package", "com.openai.chatgpt")
                    put("text", text)
                    put("new_chat", newChat)
                    put("created", System.currentTimeMillis())
                }
            )
        }

        fun queueGenericAction(context: Context, action: String, value: String = "") {
            enqueue(
                context,
                JSONObject().apply {
                    put("type", TYPE_GENERIC)
                    put("package", "*")
                    put("action", action)
                    put("value", value)
                    put("created", System.currentTimeMillis())
                }
            )
        }

        fun hasPending(context: Context): Boolean =
            synchronized(queueLock) {
                readQueue(context).length() > 0
            }

        private fun enqueue(context: Context, item: JSONObject) {
            synchronized(queueLock) {
                val current = readQueue(context)
                val next = JSONArray()

                val start = (current.length() - (MAX_QUEUE - 1)).coerceAtLeast(0)
                for (i in start until current.length()) {
                    next.put(current.getJSONObject(i))
                }
                next.put(item)

                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_QUEUE, next.toString())
                    .apply()
            }
        }

        private fun readQueue(context: Context): JSONArray {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_QUEUE, "[]")
                .orEmpty()

            return runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        }

        private fun peek(context: Context): JSONObject? =
            synchronized(queueLock) {
                val queue = readQueue(context)
                if (queue.length() == 0) null else queue.optJSONObject(0)
            }

        private fun removeHead(context: Context) {
            synchronized(queueLock) {
                val queue = readQueue(context)
                val next = JSONArray()
                for (i in 1 until queue.length()) {
                    next.put(queue.getJSONObject(i))
                }
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_QUEUE, next.toString())
                    .apply()
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var busy = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
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
                removeHead(this)
                busy = false
            }
        }
    }

    private fun executeChatGpt(pending: JSONObject) {
        val text = pending.optString("text").trim()
        val newChat = pending.optBoolean("new_chat", true)

        if (text.isBlank()) {
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
                val sent = clickFirstMatching(
                    "Enviar", "Send", "Enviar mensaje", "Send message"
                ) || clickNodeByDescription(
                    rootInActiveWindow,
                    listOf("Enviar", "Send", "Enviar mensaje", "Send message")
                )

                if (!sent) {
                    findLikelySendButton(rootInActiveWindow)
                        ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }

                removeHead(this)
                busy = false
                triggerNext()
            }, 450)
        }, if (newChat) 850 else 350)
    }

    private fun executeGenericAction(pending: JSONObject) {
        val action = pending.optString("action")
        val value = pending.optString("value")

        when (action) {
            "tap_text" -> clickFirstMatching(value)
            "type_text" -> {
                val editor = findEditableNode(rootInActiveWindow)
                if (editor != null) {
                    val args = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                    }
                    editor.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                }
            }
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
        }

        removeHead(this)
        busy = false
        triggerNext()
    }

    private fun triggerNext() {
        if (peek(this) != null) {
            handler.postDelayed({ executePendingRequest() }, 350)
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

    override fun onInterrupt() = Unit
}
