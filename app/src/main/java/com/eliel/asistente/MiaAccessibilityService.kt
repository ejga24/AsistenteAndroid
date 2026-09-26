package com.eliel.asistente

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MiaAccessibilityService : AccessibilityService() {

    companion object {
        private const val PREFS = "mia_automation"
        private const val KEY_PACKAGE = "target_package"
        private const val KEY_TEXT = "text"
        private const val KEY_NEW_CHAT = "new_chat"
        private const val KEY_PENDING = "pending"
        private const val KEY_GENERIC_ACTION = "generic_action"
        private const val KEY_GENERIC_VALUE = "generic_value"

        fun queueChatGptRequest(context: Context, text: String, newChat: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PACKAGE, "com.openai.chatgpt")
                .putString(KEY_TEXT, text)
                .putBoolean(KEY_NEW_CHAT, newChat)
                .putBoolean(KEY_PENDING, true)
                .apply()
        }

        fun hasPending(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_PENDING, false)

        fun queueGenericAction(context: Context, action: String, value: String = "") {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PENDING, true)
                .putString(KEY_PACKAGE, "*")
                .putString(KEY_GENERIC_ACTION, action)
                .putString(KEY_GENERIC_VALUE, value)
                .apply()
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

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PENDING, false)) return

        val targetPackage = prefs.getString(KEY_PACKAGE, null) ?: return
        val activePackage = event?.packageName?.toString() ?: return
        if (targetPackage != "*" && activePackage != targetPackage) return

        busy = true
        handler.postDelayed({ executePendingRequest() }, 650)
    }

    private fun executePendingRequest() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PENDING, false)) {
            busy = false
            return
        }

        val targetPackage = prefs.getString(KEY_PACKAGE, null)
        if (targetPackage == "*") {
            executeGenericAction()
            return
        }
        if (targetPackage != "com.openai.chatgpt") {
            clearPending()
            busy = false
            return
        }

        val text = prefs.getString(KEY_TEXT, "")?.trim().orEmpty()
        val newChat = prefs.getBoolean(KEY_NEW_CHAT, true)

        if (text.isBlank()) {
            clearPending()
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

                if (sent) {
                    clearPending()
                    busy = false
                } else {
                    val currentRoot = rootInActiveWindow
                    val possibleButton = findLikelySendButton(currentRoot)
                    possibleButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    clearPending()
                    busy = false
                }
            }, 450)
        }, if (newChat) 850 else 350)
    }

    private fun executeGenericAction() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val action = prefs.getString(KEY_GENERIC_ACTION, "").orEmpty()
        val value = prefs.getString(KEY_GENERIC_VALUE, "").orEmpty()

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

        clearPending()
        busy = false
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

    private fun clearPending() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING, false)
            .remove(KEY_PACKAGE)
            .remove(KEY_TEXT)
            .remove(KEY_NEW_CHAT)
            .remove(KEY_GENERIC_ACTION)
            .remove(KEY_GENERIC_VALUE)
            .apply()
    }

    override fun onInterrupt() = Unit
}
