package com.example.flacplayer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.media3.common.Player
import androidx.media3.ui.PlayerNotificationManager

class NotifAdapter(private val ctx: Context) : PlayerNotificationManager.MediaDescriptionAdapter {

    override fun getCurrentContentTitle(player: Player): CharSequence {
        val now = PlayerHolder.state.value
        return if (now.title.isNotBlank()) now.title else "Playing"
    }

    override fun createCurrentContentIntent(player: Player): PendingIntent? {
        val i = Intent(ctx, MainActivity::class.java)
        return PendingIntent.getActivity(
            ctx, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun getCurrentContentText(player: Player): CharSequence? {
        val now = PlayerHolder.state.value
        val a = listOf(now.artist, now.album).filter { it.isNotBlank() }.joinToString(" • ")
        return if (a.isBlank()) null else a
    }

    override fun getCurrentLargeIcon(
        player: Player,
        callback: PlayerNotificationManager.BitmapCallback
    ) = null
}
