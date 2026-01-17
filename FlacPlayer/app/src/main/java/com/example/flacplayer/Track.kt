package com.example.flacplayer

data class Track(
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumKey: String,
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationMs: Long?,
    val fileName: String,
    val folderHint: String
)
