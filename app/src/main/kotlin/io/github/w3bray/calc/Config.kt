package io.github.w3bray.calc

/**
 * Every knob in one place (the equivalent of the constant block at the top of desktop/calc.py).
 * Sizes are in dp: the desktop window was 420x620 px, roughly a 1x phone, so the original
 * numbers carry over unchanged. This object is pure Kotlin so JVM unit tests can use it too.
 */
object Config {
    const val TITLE = "Calc (short for calculator btw)"

    // Files inside app/src/main/assets/ (same names as the desktop version).
    const val BG_IMAGE = "diddy.png.jpeg"      // background
    const val MUSIC_FILE = "epstien.mp3"       // bg music (loops forever)
    const val OVERLAY_IMAGE = "image.png"      // the image that pops up
    const val OVERLAY_SOUND = "call.mp3"       // sound played the instant the image starts fading in

    // Layout
    const val DISPLAY_HEIGHT_DP = 90f
    const val MARGIN_DP = 14f
    const val GRID_GAP_BELOW_DISPLAY_DP = 20f
    const val BUTTON_RADIUS_DP = 14f
    const val OUTLINE_WIDTH_DP = 2f            // set 0 for fully invisible buttons
    val OUTLINE_COLOR: Int = 0xFFFFFFFF.toInt()
    val TEXT_COLOR: Int = 0xFFFFFFFF.toInt()
    const val FONT_BIG_DP = 42f
    const val FONT_MED_DP = 30f

    // Overlay animation: fade in -> hold -> fade out (seconds). Alpha is 0..255.
    const val OVERLAY_TARGET_ALPHA = 245       // 245 is ~96 % opaque
    const val OVERLAY_FADE_IN_TIME = 1.0f
    const val OVERLAY_HOLD_TIME = 3.0f
    const val OVERLAY_FADE_OUT_TIME = 1.0f

    // Surprise schedule: the overlay (with its sound) shows up on its own at a random moment
    // between MIN and MAX seconds after the app comes to the front / after the previous one.
    const val PRANK_ENABLED = true
    const val PRANK_MIN_INTERVAL_SECONDS = 20f
    const val PRANK_MAX_INTERVAL_SECONDS = 80f
    // Probability (0..1) that pressing "=" ALSO triggers the overlay. 0 = never (a normal calculator).
    const val PRANK_ON_EQUALS_CHANCE = 0f

    // Behaviour
    const val BACK_BUTTON_CLOSES_APP = false   // false: Back is ignored, the app stays open
    const val MUSIC_VOLUME = 0.35f

    // Calculator
    const val ALLOWED_INPUT = "0123456789.+-*/()"
    const val MAX_EXPR_LENGTH = 60
    const val RESULT_PRECISION_DIGITS = 15     // significant digits kept in a result
    const val RESULT_MAX_PLAIN_DIGITS = 15     // longer results switch to scientific notation

    /** How a full-screen image is fitted to the (non 420x620) phone screen. */
    enum class Fit { COVER, STRETCH, CONTAIN }
    val BACKGROUND_FIT = Fit.COVER
    val OVERLAY_FIT = Fit.COVER
}
