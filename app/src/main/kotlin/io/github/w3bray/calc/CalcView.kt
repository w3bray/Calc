package io.github.w3bray.calc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * The whole calculator, drawn by hand on a Canvas:
 * background (+ dark scrim) -> glass display card -> keypad -> overlay (on top of everything).
 *
 * The overlay ("the image") fades in, holds, fades out, and is triggered by a random timer
 * (see Config.PRANK_*). Calculator logic lives in [CalcInput] / [Evaluator].
 */
class CalcView(context: Context) : View(context) {

    interface Listener {
        /** Fired the instant the overlay starts fading in (the activity starts the sound). */
        fun onOverlayTriggered()

        /** Fired when the overlay is gone, however it ended (the activity stops the sound). */
        fun onOverlayFinished()
    }

    var listener: Listener? = null

    // --- calculator state ---
    val input = CalcInput()
    private val errorText: String = context.getString(R.string.calc_error)
    /** The expression that produced the result on screen, shown small above it. */
    private var historyExpr: String? = null

    // --- overlay state ---
    private enum class OverlayPhase { OFF, FADE_IN, HOLD, FADE_OUT }

    private var overlayPhase = OverlayPhase.OFF
    private var overlayAlpha = 0f
    // Wall-clock timing, so the image lasts exactly as long as configured (in sync with the sound)
    // whatever the frame rate of the device.
    private var overlayStartNanos = 0L
    private var fadeOutStartNanos = 0L
    private var fadeOutFromAlpha = 0f

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

    // --- safe area: system bars / display cutout ---
    private var insetLeft = 0
    private var insetTop = 0
    private var insetRight = 0
    private var insetBottom = 0

    // --- keypad ---
    private enum class Kind { DIGIT, FUNCTION, CLEAR, OPERATOR, EQUALS }

    /** One key: [label] is what is drawn, [input] is what CalcInput receives. */
    private class Key(val label: String, val input: String, val kind: Kind) {
        val rect = RectF()
        var fill: Shader? = null
        var press = 0f // 0 = idle, 1 = fully pressed (animated)
    }

    private val keys: List<Key> = listOf(
        Key("C", "C", Kind.CLEAR), Key("(", "(", Kind.FUNCTION), Key(")", ")", Kind.FUNCTION), Key("÷", "/", Kind.OPERATOR),
        Key("7", "7", Kind.DIGIT), Key("8", "8", Kind.DIGIT), Key("9", "9", Kind.DIGIT), Key("×", "*", Kind.OPERATOR),
        Key("4", "4", Kind.DIGIT), Key("5", "5", Kind.DIGIT), Key("6", "6", Kind.DIGIT), Key("−", "-", Kind.OPERATOR),
        Key("1", "1", Kind.DIGIT), Key("2", "2", Kind.DIGIT), Key("3", "3", Kind.DIGIT), Key("+", "+", Kind.OPERATOR),
        Key("0", "0", Kind.DIGIT), Key(".", ".", Kind.DIGIT), Key("⌫", "⌫", Kind.FUNCTION), Key("=", "=", Kind.EQUALS),
    )
    private var pressedKey: Key? = null
    private var uiLastFrameNanos = 0L

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private val sidePad = dp(Config.SIDE_PADDING_DP)
    private val keyGap = dp(Config.KEY_GAP_DP)
    private val cardRadius = dp(Config.DISPLAY_CARD_RADIUS_DP)
    private val cardPad = dp(22f)
    private val mainTextMax = dp(Config.DISPLAY_MAIN_TEXT_DP)
    private val mainTextMin = dp(Config.DISPLAY_MIN_TEXT_DP)
    private var keyRadius = dp(24f)

    private val cardRect = RectF()
    private val tmpRect = RectF()
    private val dstRect = RectF()

    private val light = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val backdropPaint = Paint()
    private var scrimShader: Shader? = null
    private var fallbackShader: Shader? = null
    private val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Config.COLOR_CARD_FILL }
    private val cardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = Config.COLOR_CARD_STROKE
    }
    private val keyFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = Config.COLOR_KEY_STROKE
    }
    private val keyPressOverlay = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Config.COLOR_EQUALS_BOTTOM
        setShadowLayer(dp(18f), 0f, dp(6f), Config.COLOR_EQUALS_GLOW)
    }
    private val keyText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = medium
        textAlign = Paint.Align.CENTER
        textSize = dp(Config.KEY_TEXT_DP)
    }
    private val mainText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = light }
    private val secondaryText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT
        textSize = dp(Config.DISPLAY_SECONDARY_TEXT_DP)
    }
    private val brandText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = medium
        textSize = dp(13f)
        letterSpacing = 0.32f
        color = Config.COLOR_TEXT_MUTED
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isHapticFeedbackEnabled = true
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
        layoutKeys()
        invalidate()
    }

    // ------------------------------------------------------------------ layout

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutKeys()
        loadAssets(w, h)
        scrimShader = LinearGradient(
            0f, 0f, 0f, h.toFloat(), Config.COLOR_SCRIM_TOP, Config.COLOR_SCRIM_BOTTOM, Shader.TileMode.CLAMP,
        )
        fallbackShader = LinearGradient(
            0f, 0f, 0f, h.toFloat(), Config.COLOR_FALLBACK_TOP, Config.COLOR_FALLBACK_BOTTOM, Shader.TileMode.CLAMP,
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

    /** Keypad anchored to the bottom of the safe area; the display card fills the space above. */
    private fun layoutKeys() {
        pressedKey = null
        val left = insetLeft + sidePad
        val right = width - insetRight - sidePad
        val top = insetTop + sidePad
        val bottom = height - insetBottom - sidePad
        if (right <= left || bottom <= top) return

        val cols = 4
        val rows = 5
        val keyW = (right - left - keyGap * (cols - 1)) / cols
        val maxGridH = (bottom - top) * 0.66f
        val keyH = min(keyW * Config.KEY_MAX_ASPECT, (maxGridH - keyGap * (rows - 1)) / rows)
        keyRadius = min(keyW, keyH) * Config.KEY_CORNER_FRACTION
        val gridTop = bottom - (keyH * rows + keyGap * (rows - 1))

        cardRect.set(left, top, right, gridTop - keyGap * 1.6f)

        keys.forEachIndexed { index, key ->
            val r = index / cols
            val c = index % cols
            val x = left + c * (keyW + keyGap)
            val y = gridTop + r * (keyH + keyGap)
            key.rect.set(x, y, x + keyW, y + keyH)
            val (topColor, bottomColor) = when (key.kind) {
                Kind.DIGIT -> Config.COLOR_KEY_DIGIT_TOP to Config.COLOR_KEY_DIGIT_BOTTOM
                Kind.FUNCTION, Kind.CLEAR -> Config.COLOR_KEY_FUNCTION_TOP to Config.COLOR_KEY_FUNCTION_BOTTOM
                Kind.OPERATOR -> Config.COLOR_OPERATOR_TOP to Config.COLOR_OPERATOR_BOTTOM
                Kind.EQUALS -> Config.COLOR_EQUALS_TOP to Config.COLOR_EQUALS_BOTTOM
            }
            key.fill = LinearGradient(x, y, x + keyW * 0.35f, y + keyH, topColor, bottomColor, Shader.TileMode.CLAMP)
        }
    }

    // ------------------------------------------------------------------ input

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // The key fires on press, not release, so fast typing never loses a digit.
                val key = keyAt(event.x, event.y)
                pressedKey = key
                if (key != null) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onButton(key.input)
                }
                postInvalidateOnAnimation()
            }
            MotionEvent.ACTION_UP -> {
                pressedKey = null
                performClick()
                postInvalidateOnAnimation()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedKey = null
                postInvalidateOnAnimation()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun keyAt(x: Float, y: Float): Key? {
        val slop = keyGap / 2f // the gaps belong to the nearest key, so near-misses still register
        return keys.firstOrNull {
            x >= it.rect.left - slop && x <= it.rect.right + slop && y >= it.rect.top - slop && y <= it.rect.bottom + slop
        }
    }

    private fun onButton(label: String) {
        val before = input.expr
        when (input.press(label)) {
            CalcInput.Event.EVALUATED -> {
                historyExpr = before
                maybePrankOnEquals()
            }
            CalcInput.Event.CLEARED -> dismissOverlay() // "C" also sends a visible overlay away early
            else -> {}
        }
        invalidate()
    }

    private fun maybePrankOnEquals() {
        val chance = Config.PRANK_ON_EQUALS_CHANCE
        if (chance > 0f && Random.nextFloat() < chance) triggerOverlay()
    }

    /** Hardware keyboard support: Enter/=, Backspace, Esc and the calculator characters. */
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
        overlayStartNanos = System.nanoTime()
        Log.i(Media.TAG, "[overlay] fade in")
        listener?.onOverlayTriggered() // play the sound the instant the fade begins
        postInvalidateOnAnimation()
    }

    /** Skips straight to the fade-out if the overlay is visible. */
    fun dismissOverlay() {
        if (overlayPhase == OverlayPhase.FADE_IN || overlayPhase == OverlayPhase.HOLD) {
            startFadeOut(System.nanoTime())
            postInvalidateOnAnimation()
        }
    }

    /** Removes the overlay immediately (used when the app goes to the background). */
    fun cancelOverlayNow() {
        val wasShowing = overlayPhase != OverlayPhase.OFF
        overlayPhase = OverlayPhase.OFF
        overlayAlpha = 0f
        invalidate()
        if (wasShowing) {
            listener?.onOverlayFinished()
            if (pranksArmed) scheduleNextPrank() // never leave the schedule empty while armed
        }
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
        out.putString(KEY_HISTORY, historyExpr)
    }

    fun restoreState(saved: Bundle) {
        input.restore(
            saved.getString(KEY_EXPR, ""),
            saved.getBoolean(KEY_JUST_EVALUATED, false),
            saved.getBoolean(KEY_ERROR, false),
        )
        historyExpr = saved.getString(KEY_HISTORY)
        invalidate()
    }

    // ------------------------------------------------------------------ drawing

    override fun onDraw(canvas: Canvas) {
        drawBackdrop(canvas)
        drawDisplay(canvas)
        if (animateKeys()) postInvalidateOnAnimation()
        for (key in keys) drawKey(canvas, key)

        // overlay: fade in -> hold -> fade out, drawn ON TOP of everything
        if (overlayPhase != OverlayPhase.OFF) {
            advanceOverlay()
            if (overlayPhase != OverlayPhase.OFF) {
                overlayImg?.let { drawFitted(canvas, it, Config.OVERLAY_FIT, overlayAlpha.toInt()) }
                postInvalidateOnAnimation()
            }
        }
    }

    private fun drawBackdrop(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bg = background
        if (bg != null) {
            drawFitted(canvas, bg, Config.BACKGROUND_FIT, 255)
            backdropPaint.shader = scrimShader // darken the photo so the glass keys stay readable
        } else {
            backdropPaint.shader = fallbackShader
        }
        canvas.drawRect(0f, 0f, w, h, backdropPaint)
    }

    /** Pretty operators for display only; the expression itself stays ASCII. */
    private fun pretty(s: String): String = s.replace('*', '×').replace('/', '÷').replace('-', '−')

    private fun drawDisplay(canvas: Canvas) {
        if (cardRect.isEmpty) return
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardFill)
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardStroke)

        val innerLeft = cardRect.left + cardPad
        val innerRight = cardRect.right - cardPad
        val maxWidth = innerRight - innerLeft
        if (maxWidth <= 0f) return

        // tiny brand label in the corner
        canvas.drawText("CALC", innerLeft, cardRect.top + cardPad - brandText.ascent(), brandText)

        val secondaryLine = secondaryText.fontSpacing
        val secondaryBaseline = cardRect.bottom - cardPad - secondaryText.descent()
        val mainBaseline = secondaryBaseline - secondaryLine - dp(4f)

        val main: String
        val mainColor: Int
        var below: String? = null
        var above: String? = null
        when {
            input.error -> {
                main = errorText
                mainColor = Config.COLOR_ERROR
            }
            input.justEvaluated -> {
                main = pretty(input.expr)
                mainColor = Config.COLOR_TEXT
                above = historyExpr?.let { pretty(it) + " =" }
            }
            else -> {
                main = pretty(input.displayText(errorText))
                mainColor = Config.COLOR_TEXT
                below = input.preview()?.let { "= " + pretty(it) }
            }
        }

        canvas.save()
        canvas.clipRect(cardRect.left + dp(6f), cardRect.top, cardRect.right - dp(6f), cardRect.bottom)

        // big line: shrink to fit, then clip on the left so the latest characters stay visible
        mainText.color = mainColor
        mainText.textSize = mainTextMax
        var width = mainText.measureText(main)
        if (width > maxWidth) {
            mainText.textSize = max(mainTextMin, mainTextMax * maxWidth / width)
            width = mainText.measureText(main)
        }
        canvas.drawText(main, innerRight - width, mainBaseline, mainText)

        if (below != null) {
            secondaryText.color = Config.COLOR_PREVIEW
            val bw = secondaryText.measureText(below)
            canvas.drawText(below, innerRight - bw, secondaryBaseline, secondaryText)
        }
        if (above != null) {
            secondaryText.color = Config.COLOR_TEXT_MUTED
            val aw = secondaryText.measureText(above)
            val aboveBaseline = mainBaseline + mainText.ascent() - dp(10f)
            canvas.drawText(above, innerRight - aw, aboveBaseline, secondaryText)
        }
        canvas.restore()
    }

    /** Moves each key's press amount toward its target; true while anything is still animating. */
    private fun animateKeys(): Boolean {
        val now = System.nanoTime()
        val dt = if (uiLastFrameNanos == 0L) 0.016f else min((now - uiLastFrameNanos) / 1_000_000_000f, 0.05f)
        uiLastFrameNanos = now
        var animating = false
        for (key in keys) {
            val target = if (key === pressedKey) 1f else 0f
            if (key.press != target) {
                val speed = if (target > key.press) 1f / 0.07f else 1f / 0.2f
                key.press = if (target > key.press) min(target, key.press + speed * dt) else max(target, key.press - speed * dt)
                animating = true
            }
        }
        if (!animating) uiLastFrameNanos = 0L
        return animating
    }

    /** The operator the expression currently ends with, highlighted like on a phone calculator. */
    private fun activeOperator(): String? {
        if (input.justEvaluated || input.error) return null
        val last = input.expr.lastOrNull() ?: return null
        return if (last in "+-*/") last.toString() else null
    }

    private fun drawKey(canvas: Canvas, key: Key) {
        val r = key.rect
        if (r.isEmpty) return
        val scale = 1f - 0.07f * key.press
        canvas.save()
        canvas.scale(scale, scale, r.centerX(), r.centerY())

        val highlighted = key.kind == Kind.OPERATOR && key.input == activeOperator()
        if (key.kind == Kind.EQUALS) canvas.drawRoundRect(r, keyRadius, keyRadius, glowPaint)

        if (highlighted) {
            keyFill.shader = null
            keyFill.color = 0xF2FFFFFF.toInt()
        } else {
            keyFill.color = 0xFFFFFFFF.toInt()
            keyFill.shader = key.fill
        }
        canvas.drawRoundRect(r, keyRadius, keyRadius, keyFill)
        keyFill.shader = null
        if (key.kind == Kind.DIGIT || key.kind == Kind.FUNCTION || key.kind == Kind.CLEAR) {
            val half = keyStroke.strokeWidth / 2f
            tmpRect.set(r.left + half, r.top + half, r.right - half, r.bottom - half)
            canvas.drawRoundRect(tmpRect, keyRadius, keyRadius, keyStroke)
        }
        if (key.press > 0f) {
            keyPressOverlay.color = ((0x55 * key.press).toInt() shl 24) or 0xFFFFFF
            canvas.drawRoundRect(r, keyRadius, keyRadius, keyPressOverlay)
        }

        keyText.color = when {
            highlighted -> Config.COLOR_OPERATOR_BOTTOM
            key.kind == Kind.CLEAR -> Config.COLOR_KEY_CLEAR_TEXT
            else -> Config.COLOR_TEXT
        }
        val big = key.kind == Kind.OPERATOR || key.kind == Kind.EQUALS
        keyText.typeface = if (big) light else medium
        keyText.textSize = dp(if (big) Config.KEY_OPERATOR_TEXT_DP else Config.KEY_TEXT_DP)
        val baseline = r.centerY() - (keyText.ascent() + keyText.descent()) / 2f
        canvas.drawText(key.label, r.centerX(), baseline, keyText)
        canvas.restore()
    }

    private fun startFadeOut(atNanos: Long) {
        overlayPhase = OverlayPhase.FADE_OUT
        fadeOutStartNanos = atNanos
        fadeOutFromAlpha = overlayAlpha
    }

    /** Sets the overlay alpha from the real time elapsed since it was triggered. */
    private fun advanceOverlay() {
        val now = System.nanoTime()
        val target = Config.OVERLAY_TARGET_ALPHA.toFloat()
        val fadeIn = Config.OVERLAY_FADE_IN_TIME
        val visibleUntil = fadeIn + Config.OVERLAY_HOLD_TIME

        if (overlayPhase == OverlayPhase.FADE_IN || overlayPhase == OverlayPhase.HOLD) {
            val t = (now - overlayStartNanos) / 1_000_000_000f
            when {
                t < fadeIn -> {
                    overlayPhase = OverlayPhase.FADE_IN
                    overlayAlpha = target * (t / fadeIn)
                }
                t < visibleUntil -> {
                    overlayPhase = OverlayPhase.HOLD
                    overlayAlpha = target
                }
                else -> {
                    overlayAlpha = target
                    startFadeOut(overlayStartNanos + (visibleUntil * 1_000_000_000L).toLong())
                }
            }
        }
        if (overlayPhase == OverlayPhase.FADE_OUT) {
            val t = (now - fadeOutStartNanos) / 1_000_000_000f
            val fadeOut = Config.OVERLAY_FADE_OUT_TIME
            if (fadeOut <= 0f || t >= fadeOut) {
                overlayPhase = OverlayPhase.OFF
                overlayAlpha = 0f
                Log.i(Media.TAG, "[overlay] gone")
                listener?.onOverlayFinished()
                scheduleNextPrank()
            } else {
                overlayAlpha = fadeOutFromAlpha * (1f - t / fadeOut)
            }
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

    private companion object {
        const val KEY_EXPR = "calc.expr"
        const val KEY_JUST_EVALUATED = "calc.justEvaluated"
        const val KEY_ERROR = "calc.error"
        const val KEY_HISTORY = "calc.history"
        const val PREFS_NAME = "calc"
        const val KEY_PRANK_REMAINING_MS = "prank.remainingMs"
        const val MIN_RESUME_DELAY_MS = 3_000L // never the very instant the app comes back
    }
}
