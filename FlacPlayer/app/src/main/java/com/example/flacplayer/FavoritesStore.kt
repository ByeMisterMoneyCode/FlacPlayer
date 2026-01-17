package com.example.flacplayer

import android.content.Context

class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun favorites(): Set<String> =
        prefs.getStringSet("favorites", emptySet()) ?: emptySet()

    fun isFavorite(uri: String): Boolean = favorites().contains(uri)

    fun toggle(uri: String) {
        val set = favorites().toMutableSet()
        if (!set.add(uri)) set.remove(uri)
        prefs.edit().putStringSet("favorites", set).apply()
    }
}
