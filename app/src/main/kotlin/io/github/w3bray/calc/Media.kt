package io.github.w3bray.calc

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.IOException

/** "Safe" asset loaders: a missing or broken file is logged and skipped, never fatal. */
object Media {
    const val TAG = "Calc"

    /**
     * Decodes an image from assets/, downsampling it so it is not much larger than
     * [reqW] x [reqH] (keeps memory under control for big photos).
     */
    fun loadImageSafe(assets: AssetManager, name: String, reqW: Int, reqH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            assets.open(name).use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (e: IOException) {
            Log.w(TAG, "[image] not found: $name")
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(TAG, "[image] failed to decode: $name")
            return null
        }

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return try {
            val bmp = assets.open(name).use { BitmapFactory.decodeStream(it, null, opts) }
            if (bmp == null) Log.w(TAG, "[image] failed to decode: $name")
            else Log.i(TAG, "[image] loaded $name (${bmp.width}x${bmp.height}, sample=${opts.inSampleSize})")
            bmp
        } catch (e: Exception) {
            Log.w(TAG, "[image] failed to load $name: $e")
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "[image] out of memory loading $name")
            null
        }
    }

    /**
     * Power-of-two downsampling factor: keep both dimensions >= the requested size (so a
     * centre-crop never has to upscale), but never let the longer edge exceed [MAX_EDGE_PX],
     * the largest texture the GPU can draw on older devices (a 6000x3375 photo on a
     * 1080x2400 phone would otherwise decode to an 81 MB bitmap and draw nothing).
     */
    private fun sampleSize(w: Int, h: Int, reqW: Int, reqH: Int): Int {
        var s = 1
        if (reqW > 0 && reqH > 0) {
            while (w / (s * 2) >= reqW && h / (s * 2) >= reqH) s *= 2
        }
        while (maxOf(w, h) / s > MAX_EDGE_PX) s *= 2
        return s
    }

    private const val MAX_EDGE_PX = 4096
}
