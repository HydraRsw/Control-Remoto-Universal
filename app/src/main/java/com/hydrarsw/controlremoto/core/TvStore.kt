package com.hydrarsw.controlremoto.core

import android.content.Context
import org.json.JSONArray

/** Guarda las televisiones en el almacenamiento privado de la app. */
class TvStore(context: Context) {
    private val prefs = context.getSharedPreferences("tvs", Context.MODE_PRIVATE)

    fun load(): List<SavedTv> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        List(array.length()) { SavedTv.fromJson(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun save(tvs: List<SavedTv>) {
        val array = JSONArray()
        tvs.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "saved"
    }
}
