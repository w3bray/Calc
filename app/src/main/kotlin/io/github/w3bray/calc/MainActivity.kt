package io.github.w3bray.calc

import android.app.Activity
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.View

class MainActivity : Activity(), CalcView.Listener {

    private lateinit var calcView: CalcView
    private lateinit var audio: CalcAudio

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = Config.TITLE
        volumeControlStream = AudioManager.STREAM_MUSIC
        setupEdgeToEdge()

        audio = CalcAudio(this)
        calcView = CalcView(this).also { it.listener = this }
        setContentView(calcView)
        calcView.requestFocus()

        if (savedInstanceState != null) calcView.restoreState(savedInstanceState)
    }

    /** Draw behind the (transparent) system bars; CalcView pads its UI by the insets. */
    private fun setupEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    }

    override fun onResume() {
        super.onResume()
        audio.resumeAll()
        calcView.startPranks()
    }

    override fun onPause() {
        super.onPause()
        calcView.stopPranks()
        calcView.cancelOverlayNow()
        audio.pauseAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        audio.release()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        calcView.saveState(outState)
    }

    /**
     * Back (button or gesture) does NOT close the app unless Config.BACK_BUTTON_CLOSES_APP is true.
     * Home and the recent-apps screen keep working normally.
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (Config.BACK_BUTTON_CLOSES_APP) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onOverlayTriggered() {
        audio.playOverlaySound()
    }
}
