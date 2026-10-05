package io.github.w3bray.calc

/**
 * Every knob in one place (the equivalent of the constant block at the top of desktop/calc.py).
 * Sizes are in dp, colors are ARGB. This object is pure Kotlin so JVM unit tests can use it too.
 */
object Config {
    const val TITLE = "Calc (short for calculator btw)"

    // Files inside app/src/main/assets/ (same names as the desktop version).
    const val BG_IMAGE = "diddy.png.jpeg"      // background
    const val MUSIC_FILE = "epstien.mp3"       // bg music (loops forever)
    const val OVERLAY_IMAGE = "image.png"      // the image that pops up
    const val OVERLAY_SOUND = "call.mp3"       // plays while the image is on screen, stops with it

    // Layout (dp)
    const val SIDE_PADDING_DP = 18f            // left/right/bottom padding around the keypad
    const val KEY_GAP_DP = 12f                 // space between keys
    const val KEY_CORNER_FRACTION = 0.34f      // corner radius as a fraction of the key's shorter side
    const val KEY_MAX_ASPECT = 1.0f            // keys are at most this tall relative to their width
    const val DISPLAY_CARD_RADIUS_DP = 28f
    const val DISPLAY_MAIN_TEXT_DP = 64f       // big line (expression or result), shrinks to fit
    const val DISPLAY_MIN_TEXT_DP = 30f
    const val DISPLAY_SECONDARY_TEXT_DP = 24f  // preview "= 15" / previous expression
    const val KEY_TEXT_DP = 30f
    const val KEY_OPERATOR_TEXT_DP = 36f

    // Palette (ARGB). Glass keys over a darkened background, warm orange/pink accents.
    val COLOR_SCRIM_TOP: Int = 0x8C0B0B14.toInt()      // darkens the background image for contrast
    val COLOR_SCRIM_BOTTOM: Int = 0xF00B0B14.toInt()
    val COLOR_FALLBACK_TOP: Int = 0xFF2A1B4A.toInt()   // used when there is no background image
    val COLOR_FALLBACK_BOTTOM: Int = 0xFF0B0B14.toInt()
    val COLOR_CARD_FILL: Int = 0x14FFFFFF
    val COLOR_CARD_STROKE: Int = 0x1FFFFFFF
    val COLOR_TEXT: Int = 0xFFFFFFFF.toInt()
    val COLOR_TEXT_MUTED: Int = 0x99FFFFFF.toInt()
    val COLOR_PREVIEW: Int = 0xFFFFB86B.toInt()
    val COLOR_ERROR: Int = 0xFFFF6B6B.toInt()
    val COLOR_KEY_DIGIT_TOP: Int = 0x2EFFFFFF
    val COLOR_KEY_DIGIT_BOTTOM: Int = 0x14FFFFFF
    val COLOR_KEY_FUNCTION_TOP: Int = 0x47FFFFFF
    val COLOR_KEY_FUNCTION_BOTTOM: Int = 0x29FFFFFF
    val COLOR_KEY_STROKE: Int = 0x24FFFFFF
    val COLOR_KEY_CLEAR_TEXT: Int = 0xFFFF7A7A.toInt()
    val COLOR_OPERATOR_TOP: Int = 0xFFFFB547.toInt()
    val COLOR_OPERATOR_BOTTOM: Int = 0xFFFF8A00.toInt()
    val COLOR_EQUALS_TOP: Int = 0xFFFF8A00.toInt()
    val COLOR_EQUALS_BOTTOM: Int = 0xFFFF2E63.toInt()
    val COLOR_EQUALS_GLOW: Int = 0x99FF2E63.toInt()

    // Overlay: fade in -> hold -> fade out, 8 s in total. Alpha 255 = fully opaque.
    const val OVERLAY_TARGET_ALPHA = 255
    const val OVERLAY_FADE_IN_TIME = 0.5f
    const val OVERLAY_HOLD_TIME = 7.0f
    const val OVERLAY_FADE_OUT_TIME = 0.5f

    // Surprise schedule: the overlay (with its sound) shows up on its own after a random MIN..MAX
    // seconds of foreground use (drawn on first launch and again after each surprise). Leaving the
    // app pauses the countdown; coming back resumes it, never sooner than 3 s after the return.
    const val PRANK_ENABLED = true
    const val PRANK_MIN_INTERVAL_SECONDS = 20f
    const val PRANK_MAX_INTERVAL_SECONDS = 80f
    // Probability (0..1) that pressing "=" ALSO triggers the overlay. 0 = never (a normal calculator).
    const val PRANK_ON_EQUALS_CHANCE = 0f

    // Behaviour
    const val BACK_BUTTON_CLOSES_APP = false   // false: Back is ignored, the app stays open
    const val MUSIC_VOLUME = 0.35f

    // Calculator
    const val ALLOWED_INPUT = "0123456789.+-*/()"   // what the keypad / a keyboard may type
    const val MAX_EXPR_LENGTH = 60
    const val RESULT_PRECISION_DIGITS = 15     // significant digits shown (the arithmetic itself is exact)
    const val RESULT_MAX_PLAIN_DIGITS = 15     // integer part longer than this -> scientific (1E+15)
    const val RESULT_MIN_PLAIN_EXPONENT = -6   // smaller than 0.000001 -> scientific (1E-7)

    /** How a full-screen image is fitted to the (non 420x620) phone screen. */
    enum class Fit { COVER, STRETCH, CONTAIN }
    val BACKGROUND_FIT = Fit.COVER
    val OVERLAY_FIT = Fit.COVER
}
