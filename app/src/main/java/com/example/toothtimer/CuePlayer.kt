package com.example.toothtimer

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Main-thread owned player. Preparation is asynchronous; each file owns its completion. */
class CuePlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var prepared = false
    private var paused = false

    fun setPaused(value: Boolean) {
        paused = value
        val current = player ?: return
        if (prepared) runCatching {
            if (value && current.isPlaying) current.pause()
            else if (!value && !current.isPlaying) current.start()
        }
    }

    suspend fun play(uri: String): Boolean {
        // Bound preparation only. Playback itself must never be truncated by a timeout.
        var current: MediaPlayer? = null
        val ready = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Boolean> { continuation ->
                val media = MediaPlayer()
                current = media
                player = media
                prepared = false
                fun finish(value: Boolean) { if (continuation.isActive) continuation.resume(value) }
                media.setOnPreparedListener { prepared = true; finish(true) }
                media.setOnErrorListener { _, _, _ -> finish(false); true }
                continuation.invokeOnCancellation { release(media) }
                runCatching {
                    media.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    media.setDataSource(context, Uri.parse(uri))
                    media.prepareAsync()
                }.onFailure { finish(false) }
            }
        } ?: false
        val media = current ?: return false
        if (!ready) { release(media); return false }
        return try {
            suspendCancellableCoroutine { continuation ->
                fun finish(value: Boolean) {
                    release(media)
                    if (continuation.isActive) continuation.resume(value)
                }
                media.setOnCompletionListener { finish(true) }
                media.setOnErrorListener { _, _, _ -> finish(false); true }
                continuation.invokeOnCancellation { release(media) }
                if (!paused) runCatching { media.start() }.onFailure { finish(false) }
            }
        } finally { release(media) }
    }

    private fun release(media: MediaPlayer) {
        // Cancellation, completion and disposal may meet; release once.
        if (player === media) {
            player = null
            prepared = false
            media.setOnCompletionListener(null)
            media.setOnErrorListener(null)
            media.setOnPreparedListener(null)
            media.release()
        }
    }
    fun close() { player?.let { release(it) } }
}
