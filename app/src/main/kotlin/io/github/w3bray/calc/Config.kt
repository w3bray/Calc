package io.github.w3bray.calc

import android.graphics.Color

/**
 * Mirrors the configuration block at the top of the original pygame script
 * (desktop/calc.py). Sizes are in dp: the desktop window was 420x620 px, which
 * is roughly a 1x phone, so the original numbers carry over unchanged.
 */
object Config {
    const val TITLE = "Calc (short for calculator btw)"

    // Files inside app/src/main/assets/ (same names as the desktop version).
    const val BG_IMAGE = "diddy.png.jpeg"      // background
    const val MUSIC_FILE = "epstien.mp3"       // bg music (loops forever)
    const val OVERLAY_IMAGE = "image.png"      // image that fades in on "="
    const val OVERLAY_SOUND = "call.mp3"       // sound played when the fade starts

    // Layout
    const val DISPLAY_HEIGHT_DP = 90f
    const val MARGIN_DP = 14f
    const val GRID_GAP_BELOW_DISPLAY_DP = 20f
    const val BUTTON_RADIUS_DP = 14f
    const val OUTLINE_WIDTH_DP = 2f            // set 0 for fully invisible buttons
    val OUTLINE_COLOR: Int = Color.rgb(255, 255, 255)
    val TEXT_COLOR: Int = Color.rgb(255, 255, 255)
    const val FONT_BIG_DP = 42f
    const val FONT_MED_DP = 30f

    // Fade config
    const val OVERLAY_TARGET_ALPHA = 245       // 0..255 (245 is ~96 % opaque)
    const val OVERLAY_FADE_TIME = 1.0f         // seconds from 0 -> target

    // Audio
    const val MUSIC_VOLUME = 0.35f

    // Calculator "logic"
    const val FORCED_RESULT = "67"
    const val ALLOWED_INPUT = "0123456789.+-*/()"

    /** How a full-screen image is fitted to the (non 420x620) phone screen. */
    enum class Fit { COVER, STRETCH, CONTAIN }
    val BACKGROUND_FIT = Fit.COVER
    val OVERLAY_FIT = Fit.COVER
}
