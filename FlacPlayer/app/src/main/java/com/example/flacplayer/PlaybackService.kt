package com.example.flacplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.ui.PlayerNotificationManager

/**
 * Media3-konformer Playback-Foreground-Service.
 *
 * Wichtige Punkte:
 * - startForeground() direkt in onCreate() (innerhalb der 5s-Deadline).
 * - PlayerNotificationManager hält die Notification aktuell.
 * - MediaSession liefert LockScreen/Headset/MediaControls.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    companion object {
        const val CHANNEL_ID = "playback"
        const val NOTIF_ID = 1
    }

    private var mediaSession: MediaSession? = null
    private var notifManager: PlayerNotificationManager? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        // Wichtig: schnell eine minimale Foreground-Notification posten.
        // Danach übernimmt PlayerNotificationManager und ersetzt sie.
        startForeground(NOTIF_ID, buildBootNotification())

        // Ein Player für Service + UI (Singleton).
        val player = PlayerHolder.getOrCreatePlayer(applicationContext)

        mediaSession = MediaSession.Builder(this, player).build()

        notifManager = PlayerNotificationManager.Builder(this, NOTIF_ID, CHANNEL_ID)
            .setSmallIconResourceId(android.R.drawable.ic_media_play)
            .setMediaDescriptionAdapter(ServiceNotifAdapter())
            .setNotificationListener(object : PlayerNotificationManager.NotificationListener {
                override fun onNotificationPosted(
                    notificationId: Int,
                    notification: Notification,
                    ongoing: Boolean
                ) {
                    // Nur als Foreground halten, solange es "ongoing" ist (also Playback/Controls aktiv).
                    if (ongoing) {
                        startForeground(notificationId, notification)
                    } else {
                        // Notification darf bleiben, aber Service ist nicht zwingend Foreground.
                        stopForeground(STOP_FOREGROUND_DETACH)
                    }
                }

                @RequiresApi(Build.VERSION_CODES.N)
                override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            })
            .build()

        notifManager?.setPlayer(player)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Wenn der User die App aus den Recents entfernt, aber Musik läuft: weiterspielen.
        // Wenn nichts läuft: Service beenden.
        val p = PlayerHolder.peekPlayer()
        if (p == null || (!p.isPlaying && p.playbackState == Player.STATE_IDLE)) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    @OptIn(UnstableApi::class)
    override fun onDestroy() {
        notifManager?.setPlayer(null)
        notifManager = null

        mediaSession?.release()
        mediaSession = null

        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Playback",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
    }

    private fun buildBootNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FLAC Player")
            .setContentText("Wird gestartet…")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private inner class ServiceNotifAdapter : PlayerNotificationManager.MediaDescriptionAdapter {
        override fun getCurrentContentTitle(player: Player): CharSequence {
            val t = PlayerHolder.state.value.title
            return if (t.isBlank()) "FLAC Player" else t
        }

        override fun createCurrentContentIntent(player: Player): PendingIntent? {
            val intent = Intent(this@PlaybackService, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val flags = if (Build.VERSION.SDK_INT >= 23)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT

            return PendingIntent.getActivity(this@PlaybackService, 0, intent, flags)
        }

        override fun getCurrentContentText(player: Player): CharSequence? {
            val now = PlayerHolder.state.value
            val sub = listOf(now.artist, now.album).filter { it.isNotBlank() }.joinToString(" • ")
            return sub.ifBlank { null }
        }

        override fun getCurrentLargeIcon(
            player: Player,
            callback: PlayerNotificationManager.BitmapCallback
        ): Bitmap? {
            val bytes = PlayerHolder.state.value.albumArtBytes ?: return null
            return try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Throwable) {
                null
            }
        }
    }
}
