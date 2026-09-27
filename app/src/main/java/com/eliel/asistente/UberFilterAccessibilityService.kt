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

    private var decisionLocked = false
    private var offerStartedAt = 0L
    private var overlayHiddenByTimeout = false
    private var bubbleManager: BubbleOverlayManager? = null

    companion object {
        private const val MAX_OFFER_VISIBLE_MS = 15_000L
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

        if (!decisionLocked) {
            bubbleManager?.setState(BubbleOverlayManager.State.READING)
        }

        handler.removeCallbacksAndMessages("scan")
        handler.postAtTime({ scanCurrentOffer() }, "scan", SystemClock.uptimeMillis() + 100)
    }

    private fun isUberPackage(pkg: String): Boolean =
        pkg.equals("com.ubercab.driver", true) || pkg.contains("uber", true)

    private fun findUberRoots(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()

        rootInActiveWindow?.let { root ->
            if (isUberPackage(root.packageName?.toString().orEmpty())) {
                roots += root
            }
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
            resetOffer()
            return
        }

        val offerCard = roots
            .mapNotNull { findBestOfferCard(it) }
            .minByOrNull { countNodes(it) }

        if (offerCard == null) {
            resetOffer()
            return
        }

        val now = System.currentTimeMillis()

        if (!decisionLocked) {
            val rate = extractEstimatedRateFromCard(offerCard) ?: return
            val threshold = getSharedPreferences(UberFilterActivity.PREFS, Context.MODE_PRIVATE)
                .getFloat(UberFilterActivity.KEY_THRESHOLD, 0.50f)
                .toDouble()

            decisionLocked = true
            offerStartedAt = now
            overlayHiddenByTimeout = false
            bubbleManager?.setState(
                if (rate >= threshold) BubbleOverlayManager.State.ACCEPT else BubbleOverlayManager.State.REJECT,
                rate
            )
            showDecision(rate, rate >= threshold)
            scheduleOfferWatch()
            return
        }

        if (now - offerStartedAt >= MAX_OFFER_VISIBLE_MS) {
            if (!overlayHiddenByTimeout) {
                overlayHiddenByTimeout = true
                hideOverlay()
            }
        }

        scheduleOfferWatch()
    }

    private fun scheduleOfferWatch() {
        handler.removeCallbacksAndMessages("watch")
        handler.postAtTime({ watchOfferPresence() }, "watch", SystemClock.uptimeMillis() + 350)
    }

    private fun watchOfferPresence() {
        if (!decisionLocked) return

        val roots = findUberRoots()
        val offerStillVisible = roots.any { findBestOfferCard(it) != null }

        if (!offerStillVisible) {
            resetOffer()
            return
        }

        val now = System.currentTimeMillis()
        if (now - offerStartedAt >= MAX_OFFER_VISIBLE_MS && !overlayHiddenByTimeout) {
            overlayHiddenByTimeout = true
            hideOverlay()
        }

        scheduleOfferWatch()
    }

    private fun findBestOfferCard(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestSize = Int.MAX_VALUE

        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null || !node.isVisibleToUser) return

            val subtreeText = collectSubtreeText(node)
            val normalized = normalize(subtreeText)

            val hasAction =
                normalized.contains("aceptar") ||
                normalized.contains("me interesa")

            val hasEstimatedRate =
                Regex("""(?:usd|b\s*/?\.?|\$)?\s*\d{1,2}(?:[.,]\d{1,3})?\s*[/／]\s*(?:km|kilometros?|kilómetros?)\s*\(?\s*estimado\s*\)?""",
                    RegexOption.IGNORE_CASE).containsMatchIn(normalized)

            // Validar que sea la tarjeta ACTIVA de oferta, no una notificación,
            // historial o texto de una carrera anterior.
            val hasRideType =
                normalized.contains("uberx") ||
                normalized.contains("uber x") ||
                normalized.contains("comfort") ||
                normalized.contains("moto")

            val hasTripDetails =
                normalized.contains("viaje:") ||
                Regex("""\ba\s+\d{1,2}\s+min\b""").containsMatchIn(normalized)

            val looksLikeOfferCard =
                hasAction && hasEstimatedRate && hasRideType && hasTripDetails

            if (looksLikeOfferCard) {
                val size = countNodes(node)
                if (size < bestSize) {
                    best = node
                    bestSize = size
                }
            }

            for (i in 0 until node.childCount) {
                visit(node.getChild(i))
            }
        }

        visit(root)
        return best
    }

    private fun extractEstimatedRateFromCard(card: AccessibilityNodeInfo): Double? {
        val lines = mutableListOf<String>()
        collect(card, lines)

        // Regla estricta: SOLO aceptar valores de la línea marcada por Uber como "(estimado)".
        // Ignoramos cualquier otro monto de la pantalla, notificación, historial o carrera anterior.
        val rateRegex = Regex(
            """(?:usd|b\s*/?\.?|\$)?\s*(\d{1,2}(?:[.,]\d{1,3})?)\s*[/／]\s*(?:km|kilometros?|kilómetros?)\s*\(?\s*estimado\s*\)?""",
            RegexOption.IGNORE_CASE
        )

        for (line in lines) {
            val normalizedLine = normalize(line)
            val match = rateRegex.find(normalizedLine) ?: continue
            val value = match.groupValues.getOrNull(1)
                ?.replace(',', '.')
                ?.toDoubleOrNull()
                ?: continue

            if (value in 0.05..20.0) return value
        }

        // Si Android fragmentó la línea en varios nodos, probar únicamente dentro del popup.
        val combined = normalize(lines.joinToString(" "))
        val match = rateRegex.find(combined) ?: return null
        return match.groupValues.getOrNull(1)
            ?.replace(',', '.')
            ?.toDoubleOrNull()
            ?.takeIf { it in 0.05..20.0 }
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

        for (i in 0 until node.childCount) {
            collect(node.getChild(i), out)
        }
    }

    private fun collectSubtreeText(node: AccessibilityNodeInfo?): String {
        val values = mutableListOf<String>()
        collect(node, values)
        return values.distinct().joinToString(" ")
    }

    private fun countNodes(node: AccessibilityNodeInfo?): Int {
        if (node == null) return 0
        var count = 1
        for (i in 0 until node.childCount) {
            count += countNodes(node.getChild(i))
        }
        return count
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

    private fun resetOffer() {
        decisionLocked = false
        offerStartedAt = 0L
        overlayHiddenByTimeout = false
        bubbleManager?.setState(BubbleOverlayManager.State.IDLE)
        handler.removeCallbacksAndMessages("watch")
        hideOverlay()
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
        resetOffer()
        bubbleManager?.setState(BubbleOverlayManager.State.IDLE)
    }

    override fun onDestroy() {
        resetOffer()
        bubbleManager?.hide()
        bubbleManager = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
