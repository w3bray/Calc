package io.github.w3bray.calc

import android.app.Activity
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

class MainActivity : Activity(), CalcView.Listener {

    private lateinit var calcView: CalcView
    private lateinit var audio: CalcAudio
    private var backCallback: OnBackInvokedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = Config.TITLE
        volumeControlStream = AudioManager.STREAM_MUSIC
        setupEdgeToEdge()
        setupBackHandling()

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

    /**
     * Back (button or gesture) does NOT close the app unless Config.BACK_BUTTON_CLOSES_APP is true.
     * Home and the recent-apps screen keep working. Two paths cover every Android version:
     * onBackPressed() below (the classic path, used while predictive back is off) and, on
     * Android 13+, an OnBackInvokedCallback that swallows Back when predictive back is on.
     */
    private fun setupBackHandling() {
        if (Config.BACK_BUTTON_CLOSES_APP) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = OnBackInvokedCallback { /* Back is intentionally ignored */ }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
            backCallback = callback
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (Config.BACK_BUTTON_CLOSES_APP) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
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
        val callback = backCallback
        if (callback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
        }
        backCallback = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        calcView.saveState(outState)
    }

    override fun onOverlayTriggered() {
        audio.playOverlaySound()
    }
}
