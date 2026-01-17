package com.example.flacplayer

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val uri: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val albumArtBytes: ByteArray? = null,
    val hasQueue: Boolean = false,
)

object PlayerHolder {
    private var player: ExoPlayer? = null
    private var scope: CoroutineScope? = null
    private var positionJob: Job? = null
    private var queue: List<Track> = emptyList()

    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    // AlbumArt caching (wichtig: nicht alle 500ms neu dekodieren!)
    private var lastArtUri: String? = null
    private var lastArtBytes: ByteArray? = null

    @OptIn(UnstableApi::class)
    fun ensure(context: Context) {
        val appCtx = context.applicationContext

        // 1) Service starten -> Foreground + Notification + MediaSession
        ContextCompat.startForegroundService(appCtx, Intent(appCtx, PlaybackService::class.java))

        // 2) Player sicherstellen
        getOrCreatePlayer(appCtx)

        // 3) Coroutine-Scope für Positions-Updates (nur während Playback)
        if (scope == null) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        }
    }

    /** Nur lesen (für Service-Lifecycle-Checks). */
    fun peekPlayer(): ExoPlayer? = player

    fun getOrCreatePlayer(appCtx: Context): ExoPlayer {
        val existing = player
        if (existing != null) return existing

        val p = ExoPlayer.Builder(appCtx)
            .setHandleAudioBecomingNoisy(true)
            .build()

        p.setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .build(),
            true
        )

        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Während Playback regelmäßig Position pushen, sonst nicht.
                if (isPlaying) startPositionUpdates(appCtx) else stopPositionUpdates()
                pushState(appCtx, fast = false)
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Beim Trackwechsel AlbumArt neu laden (einmal!)
                lastArtUri = null
                lastArtBytes = null
                pushState(appCtx, fast = false)
            }
            override fun onPlaybackStateChanged(playbackState: Int) = pushState(appCtx, fast = false)
        })

        player = p
        pushState(appCtx, fast = false)
        return p
    }

    private fun startPositionUpdates(appCtx: Context) {
        if (positionJob?.isActive == true) return
        val sc = scope ?: return
        positionJob = sc.launch {
            while (isActive) {
                pushState(appCtx, fast = true)
                // 500ms fühlt sich in der UI gut an, läuft aber nur während Playback.
                delay(500)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    fun playQueue(context: Context, tracks: List<Track>, startUri: String) {
        ensure(context)

        val p = player ?: return
        queue = tracks

        val startIndex = tracks.indexOfFirst { it.uri == startUri }.let { if (it < 0) 0 else it }

        p.clearMediaItems()
        tracks.forEach { t ->
            p.addMediaItem(MediaItem.fromUri(Uri.parse(t.uri)))
        }
        p.prepare()
        p.seekTo(startIndex, 0)
        p.playWhenReady = true

        pushState(context.applicationContext, fast = false)
    }

    fun togglePlayPause(context: Context) {
        ensure(context)
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
        pushState(context.applicationContext, fast = false)
    }

    fun next(context: Context) {
        ensure(context)
        player?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem()
    }

    fun prev(context: Context) {
        ensure(context)
        player?.takeIf { it.hasPreviousMediaItem() }?.seekToPreviousMediaItem()
    }

    fun seekTo(context: Context, positionMs: Long) {
        ensure(context)
        player?.seekTo(positionMs)
        pushState(context.applicationContext, fast = true)
    }

    private fun pushState(context: Context, fast: Boolean) {
        val p = player ?: return

        val duration = p.duration.coerceAtLeast(0L)
        val position = p.currentPosition.coerceAtLeast(0L)
        val current = queue.getOrNull(p.currentMediaItemIndex)

        // AlbumArt nur wenn Track wechselt (oder noch nicht geladen)
        val artBytes =
            if (!fast && current?.uri != null) getAlbumArtOnce(context, current.uri) else lastArtBytes

        _state.value = NowPlaying(
            title = current?.title ?: "",
            artist = current?.artist ?: "",
            album = current?.album ?: "",
            uri = current?.uri,
            isPlaying = p.isPlaying,
            positionMs = position,
            durationMs = duration,
            albumArtBytes = artBytes,
            hasQueue = queue.size > 1
        )
    }

    private fun getAlbumArtOnce(context: Context, uriStr: String): ByteArray? {
        if (lastArtUri == uriStr && lastArtBytes != null) return lastArtBytes
        lastArtUri = uriStr
        lastArtBytes = readEmbeddedArt(context, uriStr)
        return lastArtBytes
    }

    private fun readEmbeddedArt(context: Context, uriStr: String): ByteArray? {
        return try {
            val uri = Uri.parse(uriStr)
            val mmr = MediaMetadataRetriever()
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                mmr.setDataSource(pfd.fileDescriptor)
                val art = mmr.embeddedPicture
                mmr.release()
                art
            }
        } catch (_: Throwable) {
            null
        }
    }
}
