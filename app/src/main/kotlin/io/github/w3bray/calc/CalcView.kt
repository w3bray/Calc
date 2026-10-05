package io.github.w3bray.calc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * The whole calculator, drawn by hand on a Canvas like the pygame main loop:
 * background -> display panel -> buttons -> overlay (on top of everything).
 *
 * The overlay ("the image") fades in, holds, fades out, and is triggered by a random timer
 * (see Config.PRANK_*). Calculator logic lives in [CalcInput] / [Evaluator].
 */
class CalcView(context: Context) : View(context) {

    interface Listener {
        /** Fired the instant the overlay starts fading in (the activity plays the sound). */
        fun onOverlayTriggered()
    }

    var listener: Listener? = null

    // --- calculator state ---
    val input = CalcInput()
    private val errorText: String = context.getString(R.string.calc_error)

    // --- overlay state ---
    private enum class OverlayPhase { OFF, FADE_IN, HOLD, FADE_OUT }

    private var overlayPhase = OverlayPhase.OFF
    private var overlayAlpha = 0f
    private var phaseElapsed = 0f
    private var lastFrameNanos = 0L

    val overlayVisible: Boolean
        get() = overlayPhase != OverlayPhase.OFF

    // --- surprise schedule ---
    // The countdown only runs while the app is in front: on pause the remaining time is saved
    // (also across process death) and on resume it continues, so short calculator sessions add
    // up instead of restarting the wait every time.
    private var pranksArmed = false
    private var prankDeadlineMs = 0L // SystemClock.elapsedRealtime() when the next surprise is due; 0 = none
    private val prankRunnable = Runnable { onPrankTimer() }
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- assets ---
    private var background: Bitmap? = null
    private var overlayImg: Bitmap? = null
    private var assetsLoadedForW = 0
    private var assetsLoadedForH = 0
    private var fallbackShader: Shader? = null

    // --- safe area: system bars / display cutout ---
    private var insetLeft = 0
    private var insetTop = 0
    private var insetRight = 0
    private var insetBottom = 0

    // --- layout ---
    private val grid = listOf(
        listOf("7", "8", "9", "C"),
        listOf("4", "5", "6", "⌫"),
        listOf("1", "2", "3", "+"),
        listOf(".", "0", "-", "*"),
        listOf("(", ")", "=", "/"),
    )

    private class Button(val rect: RectF, val label: String)

    private val buttons = ArrayList<Button>()
    private var pressedButton: Button? = null

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private val margin = dp(Config.MARGIN_DP)
    private val displayHeight = dp(Config.DISPLAY_HEIGHT_DP)
    private val gridGap = dp(Config.GRID_GAP_BELOW_DISPLAY_DP)
    private val radius = dp(Config.BUTTON_RADIUS_DP)
    private val outlineWidth = dp(Config.OUTLINE_WIDTH_DP)
    private val fontBigSize = dp(Config.FONT_BIG_DP)
    private val fontBigMinSize = dp(22f)

    private val panelRect = RectF()
    private val tmpRect = RectF()
    private val dstRect = RectF()

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fallbackPaint = Paint()
    private val panelPaint = Paint().apply { color = Color.argb(120, 0, 0, 0) }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(48, 255, 255, 255) }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Config.OUTLINE_COLOR
        strokeWidth = outlineWidth
    }
    private val fontBig = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Config.TEXT_COLOR
        typeface = Typeface.DEFAULT_BOLD
        textSize = fontBigSize
    }
    private val fontMed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Config.TEXT_COLOR
        typeface = Typeface.DEFAULT_BOLD
        textSize = dp(Config.FONT_MED_DP)
        textAlign = Paint.Align.CENTER
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    // ------------------------------------------------------------------ insets

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestApplyInsets()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(prankRunnable)
        super.onDetachedFromWindow()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            setSafeInsets(i.left, i.top, i.right, i.bottom)
        } else {
            @Suppress("DEPRECATION")
            setSafeInsets(
                insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom,
            )
        }
        return insets
    }

    private fun setSafeInsets(l: Int, t: Int, r: Int, b: Int) {
        if (l == insetLeft && t == insetTop && r == insetRight && b == insetBottom) return
        insetLeft = l; insetTop = t; insetRight = r; insetBottom = b
        layoutButtons()
        invalidate()
    }

    // ------------------------------------------------------------------ layout

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutButtons()
        loadAssets(w, h)
        // Same gradient the desktop version draws when the background is missing:
        // shade = 20 + 60 * y / HEIGHT, color = (shade, shade, shade + 20)
        fallbackShader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            Color.rgb(20, 20, 40), Color.rgb(80, 80, 100), Shader.TileMode.CLAMP,
        )
    }

    private fun loadAssets(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        if (w == assetsLoadedForW && h == assetsLoadedForH) return
        assetsLoadedForW = w
        assetsLoadedForH = h
        background?.recycle()
        overlayImg?.recycle()
        background = Media.loadImageSafe(context.assets, Config.BG_IMAGE, w, h)
        overlayImg = Media.loadImageSafe(context.assets, Config.OVERLAY_IMAGE, w, h)
    }

    /** Same formulas as the pygame version, applied to the safe area of the screen. */
    private fun layoutButtons() {
        buttons.clear()
        pressedButton = null
        val left = insetLeft.toFloat()
        val top = insetTop.toFloat()
        val safeW = width - insetLeft - insetRight
        val bottom = (height - insetBottom).toFloat()
        if (safeW <= 0 || bottom <= top) return

        val cols = 4
        val rows = 5
        val gridTop = top + displayHeight + gridGap
        val btnW = (safeW - margin * (cols + 1)) / cols
        val btnH = (bottom - gridTop - margin * (rows + 1)) / rows

        panelRect.set(left + margin, top + margin, left + safeW - margin, top + margin + displayHeight)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x = left + margin + c * (btnW + margin)
                val y = gridTop + r * (btnH + margin)
                buttons.add(Button(RectF(x, y, x + btnW, y + btnH), grid[r][c]))
            }
        }
    }

    // ------------------------------------------------------------------ input

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Like pygame's MOUSEBUTTONDOWN: the button fires on press, not release.
                val button = buttonAt(event.x, event.y)
                pressedButton = button
                if (button != null) onButton(button.label)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                pressedButton = null
                performClick()
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedButton = null
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun buttonAt(x: Float, y: Float): Button? = buttons.firstOrNull { it.rect.contains(x, y) }

    private fun onButton(label: String) {
        when (input.press(label)) {
            CalcInput.Event.EVALUATED -> maybePrankOnEquals()
            CalcInput.Event.CLEARED -> dismissOverlay() // "C" also sends a visible overlay away early
            else -> {}
        }
        invalidate()
    }

    private fun maybePrankOnEquals() {
        val chance = Config.PRANK_ON_EQUALS_CHANCE
        if (chance > 0f && Random.nextFloat() < chance) triggerOverlay()
    }

    /** Hardware keyboard support, same mapping as handle_keydown() on the desktop. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_NUMPAD_EQUALS -> {
                onButton("="); return true
            }
            KeyEvent.KEYCODE_DEL -> {
                onButton("⌫"); return true
            }
            KeyEvent.KEYCODE_ESCAPE -> {
                onButton("C"); return true
            }
        }
        val ch = event.unicodeChar
        if (ch != 0) {
            val c = ch.toChar()
            if (c == '=') {
                onButton("="); return true
            }
            if (c in Config.ALLOWED_INPUT) {
                onButton(c.toString()); return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ------------------------------------------------------------------ overlay

    /** Starts the fade-in (and the sound) unless the overlay is already showing. */
    fun triggerOverlay() {
        if (overlayPhase != OverlayPhase.OFF) return
        clearPendingPrank() // a new countdown starts once the image is gone
        overlayPhase = OverlayPhase.FADE_IN
        overlayAlpha = 0f
        phaseElapsed = 0f
        lastFrameNanos = 0L
        Log.i(Media.TAG, "[overlay] fade in")
        listener?.onOverlayTriggered() // play the sound the instant the fade begins
        postInvalidateOnAnimation()
    }

    /** Skips straight to the fade-out if the overlay is visible. */
    fun dismissOverlay() {
        if (overlayPhase == OverlayPhase.FADE_IN || overlayPhase == OverlayPhase.HOLD) {
            overlayPhase = OverlayPhase.FADE_OUT
            phaseElapsed = 0f
            postInvalidateOnAnimation()
        }
    }

    /** Removes the overlay immediately (used when the app goes to the background). */
    fun cancelOverlayNow() {
        val wasShowing = overlayPhase != OverlayPhase.OFF
        overlayPhase = OverlayPhase.OFF
        overlayAlpha = 0f
        phaseElapsed = 0f
        invalidate()
        if (wasShowing && pranksArmed) scheduleNextPrank() // never leave the schedule empty while armed
    }

    /** Arms the countdown (call from Activity.onResume), continuing a saved one if there is any. */
    fun startPranks() {
        pranksArmed = true
        if (!Config.PRANK_ENABLED) return
        val saved = prefs.getLong(KEY_PRANK_REMAINING_MS, -1L)
        if (saved >= 0L) schedulePrankIn(saved.coerceAtLeast(MIN_RESUME_DELAY_MS)) else scheduleNextPrank()
    }

    /** Pauses the countdown (call from Activity.onPause) and remembers how much of it is left. */
    fun stopPranks() {
        pranksArmed = false
        removeCallbacks(prankRunnable)
        if (prankDeadlineMs > 0L) {
            val remaining = (prankDeadlineMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            prefs.edit().putLong(KEY_PRANK_REMAINING_MS, remaining).apply()
            prankDeadlineMs = 0L
        }
    }

    /** Draws a fresh random delay between the configured bounds. */
    private fun scheduleNextPrank() {
        if (!pranksArmed || !Config.PRANK_ENABLED) return
        val lo = min(Config.PRANK_MIN_INTERVAL_SECONDS, Config.PRANK_MAX_INTERVAL_SECONDS).coerceAtLeast(1f)
        val hi = max(Config.PRANK_MIN_INTERVAL_SECONDS, Config.PRANK_MAX_INTERVAL_SECONDS)
        val seconds = if (hi > lo) Random.nextDouble(lo.toDouble(), hi.toDouble()).toFloat() else lo
        schedulePrankIn((seconds * 1000f).toLong())
    }

    private fun schedulePrankIn(delayMs: Long) {
        removeCallbacks(prankRunnable)
        if (!pranksArmed || !Config.PRANK_ENABLED) return
        prankDeadlineMs = SystemClock.elapsedRealtime() + delayMs
        prefs.edit().putLong(KEY_PRANK_REMAINING_MS, delayMs).apply() // survives a process kill mid-countdown
        Log.i(Media.TAG, "[overlay] next surprise in ${"%.1f".format(Locale.ROOT, delayMs / 1000f)} s")
        postDelayed(prankRunnable, delayMs)
    }

    private fun clearPendingPrank() {
        removeCallbacks(prankRunnable)
        prankDeadlineMs = 0L
        prefs.edit().remove(KEY_PRANK_REMAINING_MS).apply()
    }

    private fun onPrankTimer() {
        if (!pranksArmed) return
        clearPendingPrank()
        if (overlayPhase == OverlayPhase.OFF) triggerOverlay() else scheduleNextPrank()
    }

    // ------------------------------------------------------------------ state

    fun saveState(out: Bundle) {
        out.putString(KEY_EXPR, input.expr)
        out.putBoolean(KEY_JUST_EVALUATED, input.justEvaluated)
        out.putBoolean(KEY_ERROR, input.error)
    }

    fun restoreState(saved: Bundle) {
        input.restore(
            saved.getString(KEY_EXPR, ""),
            saved.getBoolean(KEY_JUST_EVALUATED, false),
            saved.getBoolean(KEY_ERROR, false),
        )
        invalidate()
    }

    // ------------------------------------------------------------------ drawing

    override fun onDraw(canvas: Canvas) {
        // background
        val bg = background
        if (bg != null) {
            drawFitted(canvas, bg, Config.BACKGROUND_FIT, 255)
        } else {
            fallbackPaint.shader = fallbackShader
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fallbackPaint)
        }

        // UI
        drawDisplay(canvas)
        for (b in buttons) drawButton(canvas, b)

        // overlay: fade in -> hold -> fade out, drawn ON TOP of everything
        if (overlayPhase != OverlayPhase.OFF) {
            advanceOverlay()
            if (overlayPhase != OverlayPhase.OFF) {
                overlayImg?.let { drawFitted(canvas, it, Config.OVERLAY_FIT, overlayAlpha.toInt()) }
                postInvalidateOnAnimation()
            }
        }
    }

    /** Advances the overlay animation by the time since the previous frame. */
    private fun advanceOverlay() {
        val now = System.nanoTime()
        val raw = if (lastFrameNanos == 0L) 0f else (now - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = now
        val dt = min(raw, MAX_FRAME_DT) // a long pause must not make the animation jump
        val target = Config.OVERLAY_TARGET_ALPHA.toFloat()

        when (overlayPhase) {
            OverlayPhase.FADE_IN -> {
                overlayAlpha = if (Config.OVERLAY_FADE_IN_TIME <= 0f) target
                else min(target, overlayAlpha + (target / Config.OVERLAY_FADE_IN_TIME) * dt)
                if (overlayAlpha >= target) {
                    overlayPhase = OverlayPhase.HOLD
                    phaseElapsed = 0f
                }
            }
            OverlayPhase.HOLD -> {
                phaseElapsed += dt
                if (phaseElapsed >= Config.OVERLAY_HOLD_TIME) {
                    overlayPhase = OverlayPhase.FADE_OUT
                    phaseElapsed = 0f
                }
            }
            OverlayPhase.FADE_OUT -> {
                overlayAlpha = if (Config.OVERLAY_FADE_OUT_TIME <= 0f) 0f
                else max(0f, overlayAlpha - (target / Config.OVERLAY_FADE_OUT_TIME) * dt)
                if (overlayAlpha <= 0f) {
                    overlayPhase = OverlayPhase.OFF
                    overlayAlpha = 0f
                    Log.i(Media.TAG, "[overlay] gone")
                    scheduleNextPrank()
                }
            }
            OverlayPhase.OFF -> {}
        }
    }

    private fun drawFitted(canvas: Canvas, bmp: Bitmap, fit: Config.Fit, alpha: Int) {
        val vw = width.toFloat()
        val vh = height.toFloat()
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        if (fit == Config.Fit.STRETCH) {
            dstRect.set(0f, 0f, vw, vh)
        } else {
            val scale = if (fit == Config.Fit.COVER) max(vw / bw, vh / bh) else min(vw / bw, vh / bh)
            val dw = bw * scale
            val dh = bh * scale
            val left = (vw - dw) / 2f
            val top = (vh - dh) / 2f
            dstRect.set(left, top, left + dw, top + dh)
        }
        bitmapPaint.alpha = alpha.coerceIn(0, 255)
        canvas.drawBitmap(bmp, null, dstRect, bitmapPaint)
    }

    private fun drawDisplay(canvas: Canvas) {
        // translucent panel so the background shows through
        canvas.drawRect(panelRect, panelPaint)

        val text = input.displayText(errorText)
        val maxWidth = panelRect.width() - 2 * margin
        fontBig.textSize = fontBigSize
        var textWidth = fontBig.measureText(text)
        if (textWidth > maxWidth && maxWidth > 0) {
            // shrink a bit for long expressions; past the minimum size the left part is
            // clipped so the most recent characters stay visible (right aligned).
            fontBig.textSize = max(fontBigMinSize, fontBigSize * maxWidth / textWidth)
            textWidth = fontBig.measureText(text)
        }
        val fm = fontBig.fontMetrics
        val baseline = panelRect.centerY() - (fm.ascent + fm.descent) / 2f
        val right = panelRect.right - margin

        canvas.save()
        canvas.clipRect(panelRect)
        canvas.drawText(text, right - textWidth, baseline, fontBig)
        canvas.restore()
    }

    private fun drawButton(canvas: Canvas, b: Button) {
        if (b === pressedButton) {
            canvas.drawRoundRect(b.rect, radius, radius, pressedPaint)
        }
        if (outlineWidth > 0f) {
            val half = outlineWidth / 2f
            tmpRect.set(b.rect.left + half, b.rect.top + half, b.rect.right - half, b.rect.bottom - half)
            canvas.drawRoundRect(tmpRect, radius, radius, outlinePaint)
        }
        val fm = fontMed.fontMetrics
        val baseline = b.rect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(b.label, b.rect.centerX(), baseline, fontMed)
    }

    private companion object {
        const val KEY_EXPR = "calc.expr"
        const val KEY_JUST_EVALUATED = "calc.justEvaluated"
        const val KEY_ERROR = "calc.error"
        const val MAX_FRAME_DT = 0.25f
        const val PREFS_NAME = "calc"
        const val KEY_PRANK_REMAINING_MS = "prank.remainingMs"
        const val MIN_RESUME_DELAY_MS = 3_000L // never the very instant the app comes back
    }
}
