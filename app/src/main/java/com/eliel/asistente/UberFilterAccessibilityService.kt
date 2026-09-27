package com.eliel.asistente

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

class UberFilterAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var overlay: TextView? = null
    private var bubbleManager: BubbleOverlayManager? = null

    private var decisionLocked = false
    private var currentRate: Double? = null
    private var offerStartedAt = 0L

    companion object {
        private const val MAX_DECISION_VISIBLE_MS = 15_000L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        bubbleManager = BubbleOverlayManager(this).also {
            it.show()
            it.setState(BubbleOverlayManager.State.IDLE)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val prefs = getSharedPreferences(UberFilterActivity.PREFS, Context.MODE_PRIVATE)
        val sundayOnly = prefs.getBoolean(UberFilterActivity.KEY_SUNDAY_ONLY, true)
        if (sundayOnly && Calendar.getInstance().get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) return

        val eventPackage = event?.packageName?.toString().orEmpty()
        val eventLooksUber = isUberPackage(eventPackage)
        val uberWindowPresent = findUberRoots().isNotEmpty()

        if (!eventLooksUber && !uberWindowPresent) return

        handler.removeCallbacksAndMessages("scan")
        handler.postAtTime({ scanCurrentOffer() }, "scan", SystemClock.uptimeMillis() + 80)
    }

    private fun isUberPackage(pkg: String): Boolean =
        pkg.equals("com.ubercab.driver", true) || pkg.contains("uber", true)

    private fun findUberRoots(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()

        rootInActiveWindow?.let { root ->
            if (isUberPackage(root.packageName?.toString().orEmpty())) roots += root
        }

        for (window in windows) {
            val root = window.root ?: continue
            if (isUberPackage(root.packageName?.toString().orEmpty())) {
                if (roots.none { it == root }) roots += root
            }
        }

        return roots
    }

    private fun scanCurrentOffer() {
        val roots = findUberRoots()
        if (roots.isEmpty()) {
            clearDecision()
            return
        }

        val detected = roots.asSequence()
            .mapNotNull { extractActiveOfferRate(it) }
            .firstOrNull()

        if (detected == null) {
            clearDecision()
            return
        }

        bubbleManager?.setState(BubbleOverlayManager.State.READING)

        val threshold = getSharedPreferences(UberFilterActivity.PREFS, Context.MODE_PRIVATE)
            .getFloat(UberFilterActivity.KEY_THRESHOLD, 0.50f)
            .toDouble()

        val now = System.currentTimeMillis()
        val sameOffer = decisionLocked && currentRate != null &&
            kotlin.math.abs(currentRate!! - detected) < 0.0001

        if (!sameOffer) {
            decisionLocked = true
            currentRate = detected
            offerStartedAt = now

            val good = detected >= threshold
            bubbleManager?.setState(
                if (good) BubbleOverlayManager.State.ACCEPT else BubbleOverlayManager.State.REJECT,
                detected
            )
            showDecision(detected, good)
        } else {
            if (now - offerStartedAt >= MAX_DECISION_VISIBLE_MS) {
                hideOverlay()
                bubbleManager?.setState(BubbleOverlayManager.State.IDLE)
            }
        }

        scheduleOfferWatch()
    }

    private fun extractActiveOfferRate(root: AccessibilityNodeInfo): Double? {
        val lines = mutableListOf<String>()
        collect(root, lines)

        if (lines.isEmpty()) return null

        val rootText = normalize(lines.joinToString(" "))

        // Confirmar que estamos viendo una oferta activa y no historial/notificaciones.
        val hasOfferContext =
            rootText.contains("viaje:") ||
            rootText.contains("viaje ") ||
            rootText.contains("aceptar") ||
            rootText.contains("me interesa") ||
            Regex("""\ba\s+\d{1,2}\s+min\b""").containsMatchIn(rootText)

        if (!hasOfferContext) return null

        // La ÚNICA cifra usada para decidir es la línea de Uber "USDx.xx/km (estimado)".
        val strictRateRegex = Regex(
            """(?:usd|b\s*/?\.?|\$)?\s*(\d{1,2}(?:[.,]\d{1,3})?)\s*[/／]\s*(?:km|kilometros?|kilómetros?)\s*\(?\s*estimado\s*\)?""",
            RegexOption.IGNORE_CASE
        )

        // Primero línea por línea: evita confundir montos de otras zonas.
        for (line in lines) {
            val normalizedLine = normalize(line)
            val match = strictRateRegex.find(normalizedLine) ?: continue
            val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: continue
            if (value in 0.05..20.0) return value
        }

        // Algunas versiones de Uber dividen "USD0.75/km" y "(estimado)" en nodos separados.
        // En ese caso se permite combinar SOLO el árbol visible actual de Uber.
        val combinedMatch = strictRateRegex.find(rootText)
        if (combinedMatch != null) {
            val value = combinedMatch.groupValues[1].replace(',', '.').toDoubleOrNull()
            if (value != null && value in 0.05..20.0) return value
        }

        // Último fallback para árboles que omiten la palabra "estimado" pero mantienen /km.
        // Solo se usa si el popup tiene contexto de oferta activa.
        val rateOnlyRegex = Regex(
            """(?:usd|b\s*/?\.?|\$)?\s*(\d{1,2}(?:[.,]\d{1,3})?)\s*[/／]\s*(?:km|kilometros?|kilómetros?)""",
            RegexOption.IGNORE_CASE
        )
        for (line in lines) {
            val normalizedLine = normalize(line)
            val match = rateOnlyRegex.find(normalizedLine) ?: continue
            val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: continue
            if (value in 0.05..20.0) return value
        }

        return null
    }

    private fun scheduleOfferWatch() {
        handler.removeCallbacksAndMessages("watch")
        handler.postAtTime({ scanCurrentOffer() }, "watch", SystemClock.uptimeMillis() + 350)
    }

    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null || !node.isVisibleToUser) return

        node.text?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { out += it }

        node.contentDescription?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { out += it }

        node.hintText?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { out += it }

        for (i in 0 until node.childCount) collect(node.getChild(i), out)
    }

    private fun showDecision(rate: Double, good: Boolean) {
        hideOverlay()

        val message = buildString {
            append(if (good) "✅ ACEPTAR" else "❌ RECHAZAR")
            append("\nUSD ")
            append(String.format(Locale.US, "%.2f", rate))
            append("/km")
            append("\n(estimado de Uber)")
        }

        val view = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 21f
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(14), dp(18), dp(14))
            text = message
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(if (good) 0xE62E7D32.toInt() else 0xE6C62828.toInt())
            }
            elevation = dp(10).toFloat()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = dp(46)
        }

        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            wm.addView(view, params)
            overlay = view
        } catch (_: Exception) {
            overlay = null
        }
    }

    private fun clearDecision() {
        decisionLocked = false
        currentRate = null
        offerStartedAt = 0L
        handler.removeCallbacksAndMessages("watch")
        hideOverlay()
        bubbleManager?.setState(BubbleOverlayManager.State.IDLE)
    }

    private fun hideOverlay() {
        val current = overlay ?: return
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(current)
        } catch (_: Exception) {
        }
        overlay = null
    }

    private fun normalize(input: String): String =
        Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    override fun onInterrupt() {
        clearDecision()
    }

    override fun onDestroy() {
        clearDecision()
        bubbleManager?.hide()
        bubbleManager = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
