package com.example.flacplayer

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File


class LibraryViewModel(private val context: Context) : ViewModel() {

    private val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    private val favs = FavoritesStore(context)
    private val scanner = LibraryScanner(context)

    private val cacheFile = File(context.filesDir, "library_cache.json")

    private fun saveCache(list: List<Track>) {
        try {
            val arr = JSONArray()
            for (t in list) {
                arr.put(JSONObject().apply {
                    put("uri", t.uri)
                    put("title", t.title)
                    put("artist", t.artist)
                    put("album", t.album)
                    put("albumKey", t.albumKey)
                    put("trackNumber", t.trackNumber)
                    put("discNumber", t.discNumber)
                    put("durationMs", t.durationMs)
                    put("fileName", t.fileName)
                    put("folderHint", t.folderHint)
                })
            }
            cacheFile.writeText(arr.toString())
        } catch (_: Throwable) {}
    }

    private fun loadCache(): List<Track> {
        return try {
            if (!cacheFile.exists()) return emptyList()
            val arr = JSONArray(cacheFile.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Track(
                            uri = o.getString("uri"),
                            title = o.getString("title"),
                            artist = o.getString("artist"),
                            album = o.getString("album"),
                            albumKey = o.getString("albumKey"),
                            trackNumber = o.optInt("trackNumber").takeIf { it != 0 },
                            discNumber = o.optInt("discNumber").takeIf { it != 0 },
                            durationMs = o.optLong("durationMs").takeIf { it != 0L },
                            fileName = o.getString("fileName"),
                            folderHint = o.getString("folderHint")
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }


    private val _tracks = mutableStateOf<List<Track>>(emptyList())
    val tracks: State<List<Track>> = _tracks

    private val _isScanning = mutableStateOf(false)
    val isScanning: State<Boolean> get() = _isScanning



    data class Album(val key: String, val album: String, val artist: String)

    val albums: State<List<Album>> = derivedStateOf {
        _tracks.value
            .groupBy { it.albumKey }
            .map { (key, list) ->
                val first = list.first()
                Album(key, first.album, first.artist)
            }
            .sortedWith(compareBy({ it.artist }, { it.album }))
    }

    fun setLibraryTreeUri(uri: Uri) {
        prefs.edit().putString("libraryTreeUri", uri.toString()).apply()
    }

    fun loadSavedFolderAndScan() {
        val uriStr = prefs.getString("libraryTreeUri", null) ?: return

        val cached = loadCache()
        if (cached.isNotEmpty()) {
            _tracks.value = cached
            return
        }

        // Cache leer => einmal scannen
        rescan()
    }


    fun rescan() {
        viewModelScope.launch {
            val uriStr = prefs.getString("libraryTreeUri", null) ?: return@launch

            _isScanning.value = true
            try {
                val list = scanner.scanTree(Uri.parse(uriStr))
                _tracks.value = list
                saveCache(list)
            } finally {
                _isScanning.value = false
            }
        }
    }


    fun isFavorite(uri: String) = favs.isFavorite(uri)
    fun toggleFavorite(uri: String) = favs.toggle(uri)

    fun favoritesOnly(): List<Track> {
        val set = favs.favorites()
        return _tracks.value
            .filter { set.contains(it.uri) }
            .sortedWith(compareBy({ it.artist }, { it.album }, { it.trackNumber ?: 0 }, { it.title }))
    }

    fun albumTracks(albumKey: String): List<Track> =
        _tracks.value
            .filter { it.albumKey == albumKey }
            .sortedWith(compareBy({ it.discNumber ?: 0 }, { it.trackNumber ?: 0 }, { it.title }))

    override fun onCleared() {
        super.onCleared()
        // Release the shared MediaMetadataRetriever...
        scanner.close()
    }
}
