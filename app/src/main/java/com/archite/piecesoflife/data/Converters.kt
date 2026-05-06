package com.archite.piecesoflife.data

import androidx.room.TypeConverter
import org.json.JSONArray

class Converters {

    @TypeConverter
    fun fromItemList(items: List<Int>): String {
        return JSONArray(items).toString()
    }

    @TypeConverter
    fun toItemList(json: String): List<Int> {
        if (json.isBlank()) return emptyList()
        val arr = JSONArray(json)
        return List(arr.length()) { arr.getInt(it) }
    }
}
