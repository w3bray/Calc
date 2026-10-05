package io.github.w3bray.calc

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import java.io.IOException

/**
 * Background music + the overlay sound effect, both loaded from assets/.
 * Equivalent of pygame.mixer.music / pygame.mixer.Sound in the desktop version.
 */
class CalcAudio(private val context: Context) {

    private var music: MediaPlayer? = null
    private val sfx = ArrayList<MediaPlayer>()

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    /** Opens an asset as a prepared MediaPlayer, or null (logged) if missing/broken. */
    private fun create(name: String, tag: String): MediaPlayer? {
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
            player.prepare()
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
            if (!it.isPlaying) it.start()
            return
        }
        val player = create(Config.MUSIC_FILE, "music") ?: return
        player.isLooping = true
        player.setVolume(Config.MUSIC_VOLUME, Config.MUSIC_VOLUME)
        player.start()
        music = player
        Log.i(Media.TAG, "[music] playing: ${Config.MUSIC_FILE}")
    }

    /** Plays the overlay sound. Overlapping plays are allowed, like pygame channels. */
    fun playOverlaySound() {
        val player = create(Config.OVERLAY_SOUND, "sfx") ?: return
        if (sfx.size >= MAX_CONCURRENT_SFX) {
            sfx.removeAt(0).release()
        }
        player.setOnCompletionListener { p ->
            sfx.remove(p)
            p.release()
        }
        sfx.add(player)
        player.start()
    }

    /** Called from Activity.onPause: pause the music and cut any sound effect short. */
    fun pauseAll() {
        music?.let { if (it.isPlaying) it.pause() }
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

    private companion object {
        const val MAX_CONCURRENT_SFX = 8
    }
}
