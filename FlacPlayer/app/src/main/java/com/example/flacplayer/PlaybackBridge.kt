package com.example.flacplayer

import androidx.media3.exoplayer.ExoPlayer

/**
 * Singleton-Bridge: Service und UI teilen sich denselben Player.
 */
object PlaybackBridge {
    @Volatile private var _player: ExoPlayer? = null

    fun setPlayer(player: ExoPlayer) {
        _player = player
    }

    fun getPlayer(): ExoPlayer? = _player
}
