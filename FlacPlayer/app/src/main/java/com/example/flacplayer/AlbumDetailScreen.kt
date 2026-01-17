package com.example.flacplayer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumKey: String,
    title: String,
    artist: String,
    tracks: List<Track>,
    isFavorite: (String) -> Boolean,
    onPlay: (String) -> Unit,
    onToggleFav: (String) -> Unit,
    onBack: () -> Unit,
    bottomBarHeight: Dp = 0.dp,   // ✅ neu
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$artist — $title") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = bottomBarHeight + 12.dp
            )
        ) {
            items(tracks) { t ->
                TrackRow(
                    title = t.title,
                    subtitle = "${t.artist} • ${t.album}",
                    isFav = isFavorite(t.uri),
                    onPlayClick = { onPlay(t.uri) },
                    onToggleFav = { onToggleFav(t.uri) }
                )
                Divider()
            }
        }
    }
}
