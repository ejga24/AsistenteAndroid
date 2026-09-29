package com.eliel.asistente

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class UberFilterAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var overlay: TextView? = null
    private var bubbleManager: BubbleOverlayManager? = null

    private var screenshotReading = false
    private var lastScreenshotAt = 0L
    private var lastUberEventAt = 0L
    private var lastValidRateSeenAt = 0L

    private var currentRate: Double? = null
    private var decisionLocked = false

    private val recentUberText = ArrayDeque<String>()

    companion object {
        private const val HEARTBEAT_MS = 650L
        private const val SCREENSHOT_MIN_GAP_MS = 650L
        private const val OFFER_LOST_MS = 1800L
        private const val BANNER_VISIBLE_MS = 4500L
        private const val RECENT_EVENT_MS = 1500L
    }

    private val heartbeat = object : Runnable {
        override fun run() {
            try {
                heartbeatTick()
            } finally {
                handler.postDelayed(this, HEARTBEAT_MS)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        bubbleManager = BubbleOverlayManager(this).also {
            it.show()
            it.setState(BubbleOverlayManager.State.IDLE)
        }

        handler.removeCallbacks(heartbeat)
        handler.post(heartbeat)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString().orEmpty()
        if (!isUberPackage(pkg)) return

        lastUberEventAt = System.currentTimeMillis()
        event?.let { rememberUberEvent(it) }

        // No depender del evento para seguir leyendo. Solo acelera la próxima vuelta.
        handler.removeCallbacks(heartbeat)
        handler.post(heartbeat)
    }

    private fun heartbeatTick() {
        val prefs = getSharedPreferences(UberFilterActivity.PREFS, Context.MODE_PRIVATE)
        val sundayOnly = prefs.getBoolean(UberFilterActivity.KEY_SUNDAY_ONLY, true)
        if (sundayOnly && Calendar.getInstance().get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            clearDecision()
            return
        }

        val now = System.currentTimeMillis()
        val roots = findUberRoots()
        val uberLooksActive = roots.isNotEmpty() || now - lastUberEventAt <= 3000L

        if (!uberLooksActive) {
            clearDecision()
            return
        }

        if (!decisionLocked) {
            bubbleManager?.setState(BubbleOverlayManager.State.READING)
        }

        // 1) Intento rápido por Accesibilidad.
        val candidates = mutableListOf<String>()
        for (root in roots) collect(root, candidates, requireVisible = false)

        if (now - lastUberEventAt <= RECENT_EVENT_MS) {
            candidates += recentUberText
        } else {
            recentUberText.clear()
        }

        val debugText = candidates.distinct().joinToString(" | ").take(5000)
        getSharedPreferences("uber_filter_debug", Context.MODE_PRIVATE)
            .edit()
            .putString("last_accessibility_text", debugText)
            .putLong("last_accessibility_at", now)
            .apply()

        val accessibilityRate = UberRateParser.extract(candidates)
        if (accessibilityRate != null) {
            observeRate(accessibilityRate)
        } else {
            // 2) En esta tablet el popup no siempre aparece en Accesibilidad.
            // Leer visualmente la pantalla en cada ciclo mientras Uber esté activo.
            tryVisualOfferRead()
        }

        // Si durante 1.8 s no volvió a aparecer una tarifa válida, la oferta terminó
        // o cambió. Se limpia el estado para que la siguiente oferta entre fresca.
        if (lastValidRateSeenAt > 0L && now - lastValidRateSeenAt > OFFER_LOST_MS) {
            clearDecision()
        }
    }

    private fun isUberPackage(pkg: String): Boolean =
        pkg.equals("com.ubercab.driver", true) || pkg.contains("uber", true)

    private fun rememberUberEvent(event: AccessibilityEvent) {
        val pieces = mutableListOf<String>()

        event.text?.forEach { cs ->
            cs?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { pieces += it }
        }

        event.contentDescription?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { pieces += it }

        event.beforeText?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { pieces += it }

        event.source?.let { source ->
            val sourceText = mutableListOf<String>()
            collect(source, sourceText, requireVisible = false)
            pieces += sourceText
        }

        for (piece in pieces) {
            val clean = piece.trim()
            if (clean.isBlank()) continue
            recentUberText.remove(clean)
            recentUberText.addLast(clean)
            while (recentUberText.size > 100) recentUberText.removeFirst()
        }
    }

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

    private fun tryVisualOfferRead() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        val now = System.currentTimeMillis()
        if (screenshotReading || now - lastScreenshotAt < SCREENSHOT_MIN_GAP_MS) return

        screenshotReading = true
        lastScreenshotAt = now

        takeScreenshot(
            android.view.Display.DEFAULT_DISPLAY,
            ContextCompat.getMainExecutor(this),
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    try {
                        val hardware = result.hardwareBuffer
                        val wrapped = Bitmap.wrapHardwareBuffer(hardware, result.colorSpace)
                        val bitmap = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                        hardware.close()

                        if (bitmap == null) {
                            screenshotReading = false
                            return
                        }

                        UberOcrReader.readRate(bitmap) { rate, rawText ->
                            val nowOcr = System.currentTimeMillis()

                            getSharedPreferences("uber_filter_debug", Context.MODE_PRIVATE)
                                .edit()
                                .putString("last_ocr_text", rawText.take(5000))
                                .putLong("last_ocr_at", nowOcr)
                                .apply()

                            if (rate != null) {
                                observeRate(rate)
                            }

                            bitmap.recycle()
                            screenshotReading = false
                        }
                    } catch (_: Exception) {
                        screenshotReading = false
                    }
                }

                override fun onFailure(errorCode: Int) {
                    screenshotReading = false
                }
            }
        )
    }

    private fun observeRate(rate: Double) {
        val now = System.currentTimeMillis()
        lastValidRateSeenAt = now

        val threshold = getSharedPreferences(UberFilterActivity.PREFS, Context.MODE_PRIVATE)
            .getFloat(UberFilterActivity.KEY_THRESHOLD, 0.50f)
            .toDouble()

        val isNewDecision =
            !decisionLocked ||
            currentRate == null ||
            abs(currentRate!! - rate) >= 0.0001

        if (!isNewDecision) return

        decisionLocked = true
        currentRate = rate

        getSharedPreferences("uber_filter_debug", Context.MODE_PRIVATE)
            .edit()
            .putFloat("last_rate", rate.toFloat())
            .putLong("last_rate_at", now)
            .apply()

        val good = rate >= threshold
        bubbleManager?.setState(
            if (good) BubbleOverlayManager.State.ACCEPT else BubbleOverlayManager.State.REJECT,
            rate
        )
        showDecision(rate, good)
    }

    private fun showDecision(rate: Double, good: Boolean) {
        hideOverlay()
        handler.removeCallbacksAndMessages("hide-banner")

        // IMPORTANTE: no escribir "(estimado)" aquí.
        // Así el OCR jamás puede confundir nuestro propio banner con el popup de Uber.
        val message = buildString {
            append(if (good) "✅ ACEPTAR" else "❌ RECHAZAR")
            append("\n")
            append(String.format(Locale.US, "%.2f", rate))
            append("/km")
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

            handler.postAtTime(
                { hideOverlay() },
                "hide-banner",
                SystemClock.uptimeMillis() + BANNER_VISIBLE_MS
            )
        } catch (_: Exception) {
            overlay = null
        }
    }

    private fun clearDecision() {
        if (!decisionLocked && currentRate == null && overlay == null) {
            bubbleManager?.setState(BubbleOverlayManager.State.IDLE)
            return
        }

        decisionLocked = false
        currentRate = null
        lastValidRateSeenAt = 0L
        handler.removeCallbacksAndMessages("hide-banner")
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

    private fun collect(
        node: AccessibilityNodeInfo?,
        out: MutableList<String>,
        requireVisible: Boolean
    ) {
        if (node == null) return
        if (requireVisible && !node.isVisibleToUser) return

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
            collect(node.getChild(i), out, requireVisible)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    override fun onInterrupt() {
        clearDecision()
    }

    override fun onDestroy() {
        handler.removeCallbacks(heartbeat)
        handler.removeCallbacksAndMessages(null)
        screenshotReading = false
        recentUberText.clear()
        clearDecision()
        bubbleManager?.hide()
        bubbleManager = null
        super.onDestroy()
    }
}
