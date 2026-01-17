package com.example.flacplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel


sealed interface Screen {
    data object Albums : Screen
    data object Favorites : Screen
    data class AlbumDetail(val key: String, val album: String, val artist: String) : Screen
}

class MainActivity : ComponentActivity() {
    private val notifPermission =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    private var lastPickedTreeUri: Uri? = null
    private var folderPickTick by mutableIntStateOf(0)

    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri ?: return@registerForActivityResult
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Throwable) { /* ignore */ }

            lastPickedTreeUri = uri
            folderPickTick++
        }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        super.onCreate(savedInstanceState)

        setContent {
            val vm: LibraryViewModel = viewModel(factory = SimpleVmFactory(this))
            val now by PlayerHolder.state.collectAsState()

            var screen by remember { mutableStateOf<Screen>(Screen.Albums) }

            // load saved folder + scan on start
            LaunchedEffect(Unit) {
                vm.loadSavedFolderAndScan()
            }

            // When user picks a folder
            LaunchedEffect(folderPickTick) {
                val u = lastPickedTreeUri ?: return@LaunchedEffect
                vm.setLibraryTreeUri(u)
                vm.rescan()
            }

            val albums by vm.albums
            val scanning by vm.isScanning

            MaterialTheme {
                Scaffold(
                    topBar = {
                        Column {
                            TopAppBar(
                                title = { Text("FLAC Player") },
                                actions = {
                                    TextButton(onClick = { pickFolder.launch(null) }) { Text("Set Folder") }
                                    TextButton(onClick = { vm.rescan() }) { Text("Rescan") }
                                }
                            )

                            if (scanning) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text(
                                    "Scanning your library…",
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = screen is Screen.Albums,
                                onClick = { screen = Screen.Albums },
                                label = { Text("Albums") },
                                icon = {}
                            )
                            NavigationBarItem(
                                selected = screen is Screen.Favorites,
                                onClick = { screen = Screen.Favorites },
                                label = { Text("Favorites") },
                                icon = {}
                            )
                        }
                    }
                ) { pad ->
                    Column(Modifier.padding(pad).fillMaxSize()) {

                        // Content area
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when (val s = screen) {
                                Screen.Albums -> {
                                    if (albums.isEmpty()) {
                                        Box(Modifier.fillMaxSize()) {
                                            Column(Modifier.padding(16.dp)) {
                                                Text("No library yet.")
                                                Spacer(Modifier.height(8.dp))
                                                Text("Tap Set Folder → choose your Music folder → Rescan.")
                                            }
                                        }
                                    } else {
                                        LazyColumn(Modifier.fillMaxSize()) {
                                            items(albums) { a ->
                                                ListItem(
                                                    headlineContent = { Text(a.album) },
                                                    supportingContent = { Text(a.artist) },
                                                    modifier = Modifier.clickable {
                                                        screen = Screen.AlbumDetail(a.key, a.album, a.artist)
                                                    }
                                                )
                                                Divider()
                                            }
                                        }
                                    }
                                }

                                Screen.Favorites -> {
                                    val favs = vm.favoritesOnly()
                                    if (favs.isEmpty()) {
                                        Box(Modifier.fillMaxSize()) {
                                            Text("No favorites yet.", modifier = Modifier.padding(16.dp))
                                        }
                                    } else {
                                        LazyColumn(Modifier.fillMaxSize()) {
                                            items(favs) { t ->
                                                TrackRow(
                                                    title = t.title,
                                                    subtitle = "${t.artist} • ${t.album}",
                                                    isFav = vm.isFavorite(t.uri),
                                                    onPlayClick = {
                                                        PlayerHolder.playQueue(
                                                            context = this@MainActivity,
                                                            tracks = favs,
                                                            startUri = t.uri
                                                        )
                                                    },
                                                    onToggleFav = { vm.toggleFavorite(t.uri) }
                                                )
                                                Divider()
                                            }
                                        }
                                    }
                                }

                                is Screen.AlbumDetail -> {
                                    val albumTracks = vm.albumTracks(s.key)
                                    AlbumDetailScreen(
                                        albumKey = s.key,
                                        title = s.album,
                                        artist = s.artist,
                                        tracks = albumTracks,
                                        isFavorite = { uri -> vm.isFavorite(uri) },
                                        onPlay = { uri ->
                                            PlayerHolder.playQueue(
                                                context = this@MainActivity,
                                                tracks = albumTracks,
                                                startUri = uri
                                            )
                                        },
                                        onToggleFav = { uri -> vm.toggleFavorite(uri) },
                                        onBack = { screen = Screen.Albums }
                                    )
                                }
                            }
                        }

                        // Now Playing (cover + controls + progress/seek)
                        NowPlayingBar(
                            now = now,
                            onPlayPause = { PlayerHolder.togglePlayPause(this@MainActivity) },
                            onNext = { PlayerHolder.next(this@MainActivity) },
                            onPrev = { PlayerHolder.prev(this@MainActivity) },
                            onSeek = { ms -> PlayerHolder.seekTo(this@MainActivity, ms) }
                        )
                    }
                }
            }
        }
    }
}
