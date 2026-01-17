package com.example.flacplayer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryScanner(private val context: Context) {
    private val mmr = MediaMetadataRetriever()

    fun close() {
        try { mmr.release() } catch (_: Throwable) { }
    }

    suspend fun scanTree(treeUri: Uri): List<Track> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val out = mutableListOf<Track>()
        walk(root, out)
        out
    }

    private fun walk(dir: DocumentFile, out: MutableList<Track>) {
        if (!dir.isDirectory) return

        val children = try { dir.listFiles() } catch (_: Throwable) { return }
        for (f in children) {
            try {
                if (f.isDirectory) {
                    walk(f, out)
                    continue
                }

                val name = f.name ?: continue
                if (!name.endsWith(".flac", ignoreCase = true)) continue

                // Some SAF providers return docs you can't actually open
                val uri = f.uri
                if (!canOpen(uri)) continue

                val meta = readMeta(uri)

                val folderAlbum = dir.name?.takeIf { it.isNotBlank() } ?: "Unknown Album"
                val album = meta.album?.takeIf { it.isNotBlank() } ?: folderAlbum
                val artist = meta.artist?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
                val title = meta.title?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.')

                val albumKey = if (!meta.album.isNullOrBlank()) {
                    "$artist||$album"
                } else {
                    "$artist||FOLDER||$folderAlbum"
                }

                out += Track(
                    uri = uri.toString(),
                    title = title,
                    artist = artist,
                    album = album,
                    albumKey = albumKey,
                    trackNumber = meta.trackNumber,
                    discNumber = meta.discNumber,
                    durationMs = meta.durationMs,
                    fileName = name,
                    folderHint = folderAlbum
                )
            } catch (_: Throwable) {
                // Skip anything that breaks (bad file, permission, buggy provider, etc.)
            }
        }
    }

    private fun canOpen(uri: Uri): Boolean {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (_: Throwable) {
            false
        }
    }


    private data class Meta(
        val title: String?,
        val artist: String?,
        val album: String?,
        val durationMs: Long?,
        val trackNumber: Int?,
        val discNumber: Int?
    )

    private fun readMeta(uri: Uri): Meta {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                mmr.setDataSource(pfd.fileDescriptor)
                val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()

                val trackRaw = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                val track = trackRaw?.takeWhile { it.isDigit() }?.toIntOrNull()

                val discRaw = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                val disc = discRaw?.takeWhile { it.isDigit() }?.toIntOrNull()

                Meta(title, artist, album, duration, track, disc)
            } ?: Meta(null, null, null, null, null, null)
        } catch (_: Throwable) {
            Meta(null, null, null, null, null, null)
        }
    }
}
