package io.github.w3bray.calc

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import java.io.IOException

/**
 * Background music + the overlay sound effect, both loaded from assets/.
 * Equivalent of pygame.mixer.music / pygame.mixer.Sound in the desktop version.
 * Every MediaPlayer call is guarded: a broken or missing file can never crash the app.
 */
class CalcAudio(private val context: Context) {

    private var music: MediaPlayer? = null
    private val sfx = ArrayList<MediaPlayer>()

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    /**
     * Opens an asset as a MediaPlayer, or null (logged) if missing/broken. With [async] the
     * player is returned while still preparing and starts itself when ready, so the UI thread
     * is never blocked by decoding (used for the surprise sound at the start of the fade).
     */
    private fun create(name: String, tag: String, async: Boolean = false): MediaPlayer? {
        val afd = try {
            context.assets.openFd(name)
        } catch (e: IOException) {
            Log.w(Media.TAG, "[$tag] not found: $name")
            return null
        }
        val player = MediaPlayer()
        return try {
            player.setAudioAttributes(attributes)
            afd.use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            if (async) {
                player.setOnPreparedListener { it.safeStart() }
                player.prepareAsync()
            } else {
                player.prepare()
            }
            player
        } catch (e: Exception) {
            Log.w(Media.TAG, "[$tag] failed to load $name: $e")
            player.release()
            null
        }
    }

    /** Starts the looping background music, or resumes it if it was paused. */
    fun startMusic() {
        music?.let {
            if (!it.safeIsPlaying()) it.safeStart()
            return
        }
        val player = create(Config.MUSIC_FILE, "music") ?: return
        player.isLooping = true
        player.setVolume(Config.MUSIC_VOLUME, Config.MUSIC_VOLUME)
        player.setOnErrorListener { p, what, extra ->
            Log.w(Media.TAG, "[music] error $what/$extra, stopping")
            if (music === p) music = null
            p.release()
            true
        }
        music = player
        player.safeStart()
        Log.i(Media.TAG, "[music] playing: ${Config.MUSIC_FILE}")
    }

    /** Plays the overlay sound (starts as soon as it is decoded). Overlapping plays are allowed. */
    fun playOverlaySound() {
        val player = create(Config.OVERLAY_SOUND, "sfx", async = true) ?: return
        if (sfx.size >= MAX_CONCURRENT_SFX) {
            sfx.removeAt(0).release()
        }
        player.setOnCompletionListener { p ->
            sfx.remove(p)
            p.release()
        }
        player.setOnErrorListener { p, what, extra ->
            Log.w(Media.TAG, "[sfx] error $what/$extra")
            sfx.remove(p)
            p.release()
            true
        }
        sfx.add(player)
    }

    /** Called from Activity.onPause: pause the music and cut any sound effect short. */
    fun pauseAll() {
        music?.let { if (it.safeIsPlaying()) it.safePause() }
        stopEffects()
    }

    /** Called from Activity.onResume. */
    fun resumeAll() {
        startMusic()
    }

    fun release() {
        music?.release()
        music = null
        stopEffects()
    }

    private fun stopEffects() {
        for (p in sfx) p.release()
        sfx.clear()
    }

    private fun MediaPlayer.safeIsPlaying(): Boolean = try {
        isPlaying
    } catch (e: IllegalStateException) {
        false
    }

    private fun MediaPlayer.safeStart() {
        try {
            start()
        } catch (e: IllegalStateException) {
            Log.w(Media.TAG, "[audio] start failed: $e")
        }
    }

    private fun MediaPlayer.safePause() {
        try {
            pause()
        } catch (e: IllegalStateException) {
            Log.w(Media.TAG, "[audio] pause failed: $e")
        }
    }

    private companion object {
        const val MAX_CONCURRENT_SFX = 8
    }
}
