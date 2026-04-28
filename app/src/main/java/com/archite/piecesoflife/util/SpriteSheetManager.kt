package com.archite.piecesoflife.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import androidx.annotation.RawRes
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.InputStream
import java.util.WeakHashMap

object SpriteSheetManager {

    private var index: SpriteIndex? = null
    private val bitmapCache = WeakHashMap<String, Bitmap>()

    data class SpriteRect(val x: Int, val y: Int, val w: Int, val h: Int)

    data class NinePatchInfo(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    data class SheetEntry(
        val image: String,
        val sprites: Map<String, SpriteRect>,
        val ninePatch: NinePatchInfo? = null
    )

    data class SpriteIndex(
        val version: Int,
        val sheets: List<SheetEntry>
    )

    fun load(context: Context, @RawRes indexRes: Int) {
        val json: String = context.resources.openRawResource(indexRes)
            .bufferedReader().use { it.readText() }
        index = Gson().fromJson(json, SpriteIndex::class.java)
    }

    fun getRect(spriteName: String): SpriteRect? {
        index?.sheets?.forEach { sheet ->
            sheet.sprites[spriteName]?.let { return it }
        }
        return null
    }

    fun getNinePatch(spriteName: String): NinePatchInfo? {
        index?.sheets?.forEach { sheet ->
            if (sheet.sprites.containsKey(spriteName)) {
                return sheet.ninePatch
            }
        }
        return null
    }

    fun getSheetImage(spriteName: String): String? {
        index?.sheets?.forEach { sheet ->
            if (sheet.sprites.containsKey(spriteName)) {
                return sheet.image
            }
        }
        return null
    }

    fun getSpriteBitmap(context: Context, spriteName: String): Bitmap? {
        val rect = getRect(spriteName) ?: return null
        val imageName = getSheetImage(spriteName) ?: return null

        val cacheKey = spriteName
        bitmapCache[cacheKey]?.let { return it }

        val sheetBitmap = loadSheetBitmap(context, imageName) ?: return null

        val bitmap = Bitmap.createBitmap(
            sheetBitmap,
            rect.x, rect.y, rect.w, rect.h
        )
        bitmapCache[cacheKey] = bitmap
        return bitmap
    }

    private fun loadSheetBitmap(context: Context, imageName: String): Bitmap? {
        val cacheKey = "sheet:$imageName"
        bitmapCache[cacheKey]?.let { return it }

        val resId = context.resources.getIdentifier(
            imageName.removeSuffix(".png"),
            "drawable",
            context.packageName
        )
        if (resId == 0) return null

        val options = BitmapFactory.Options().apply {
            inScaled = false
        }
        val bitmap = BitmapFactory.decodeResource(context.resources, resId, options)
        if (bitmap != null) {
            bitmapCache[cacheKey] = bitmap
        }
        return bitmap
    }

    fun clearCache() {
        bitmapCache.clear()
    }
}
