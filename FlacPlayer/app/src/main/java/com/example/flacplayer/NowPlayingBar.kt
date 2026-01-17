package com.example.flacplayer

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious


@Composable
fun NowPlayingBar(
    now: NowPlaying,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit
) {
    if (now.uri == null) return

    val duration = now.durationMs.coerceAtLeast(1L)
    val pos = now.positionMs.coerceIn(0L, duration)

    val bmp = remember(now.albumArtBytes) {
        now.albumArtBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    Card(Modifier.fillMaxWidth().padding(10.dp)) {
        Column(Modifier.padding(12.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Album cover",
                        modifier = Modifier.size(56.dp)
                    )
                } else {
                    // simple placeholder
                    Surface(
                        modifier = Modifier.size(56.dp),
                        tonalElevation = 2.dp
                    ) {}
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = now.title.ifBlank { "Unknown title" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOf(now.artist, now.album).filter { it.isNotBlank() }.joinToString(" • ")
                            .ifBlank { "Unknown artist" },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Controls
                Row {
                    IconButton(onClick = onPrev, enabled = now.hasQueue) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
                    }
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            if (now.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause"
                        )
                    }
                    IconButton(onClick = onNext, enabled = now.hasQueue) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Seek bar
            Slider(
                value = pos.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMs(pos), style = MaterialTheme.typography.bodySmall)
                Text(formatMs(duration), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
