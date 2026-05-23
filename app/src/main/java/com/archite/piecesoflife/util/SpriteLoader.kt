package com.archite.piecesoflife.util

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.widget.ImageView

object SpriteLoader {

    private val sheetCache = HashMap<Int, Bitmap>()
    private val cropCache = HashMap<String, Bitmap>()

    private fun sheet(resId: Int): Bitmap {
        return sheetCache.getOrPut(resId) { SpriteDef.loadSheet(resId) }
    }

    private fun cropKey(resId: Int, frame: SpriteFrame, scale: Int) =
        "$resId@${frame.x}_${frame.y}_${frame.w}_${frame.h}x$scale"

    internal fun loadCrop(resId: Int, frame: SpriteFrame, scale: Int): Bitmap {
        val key = cropKey(resId, frame, scale)
        return cropCache.getOrPut(key) {
            PixelGraphics.crop(sheet(resId), frame.x, frame.y, frame.w, frame.h, scale)
        }
    }

    fun icon(index: Int, scale: Int = 5): Bitmap {
        return loadCrop(SpriteDef.Icons.RES, SpriteDef.Icons.frame(index), scale)
    }

    fun button16(index: Int, scale: Int = 5): Bitmap {
        return loadCrop(SpriteDef.B16.RES, SpriteDef.B16.frame(index), scale)
    }

    fun button24(index: Int, scale: Int = 5): Bitmap {
        return loadCrop(SpriteDef.B24.RES, SpriteDef.B24.frame(index), scale)
    }

    fun button32(index: Int, scale: Int = 5): Bitmap {
        return loadCrop(SpriteDef.B32.RES, SpriteDef.B32.frame(index), scale)
    }

    fun button48x24(index: Int, scale: Int = 5): Bitmap {
        return loadCrop(SpriteDef.B48x24.RES, SpriteDef.B48x24.frame(index), scale)
    }

    fun button48x32(index: Int, scale: Int = 5): Bitmap {
        return loadCrop(SpriteDef.B48x32.RES, SpriteDef.B48x32.frame(index), scale)
    }

    fun button72x32(index: Int, scale: Int = 5): Bitmap {
        return loadCrop(SpriteDef.B72x32.RES, SpriteDef.B72x32.frame(index), scale)
    }

    fun button72x72(index: Int, scale: Int = 5): Bitmap {
        return loadCrop(SpriteDef.B72x72.RES, SpriteDef.B72x72.frame(index), scale)
    }

    fun button96x32(index: Int, scale: Int = 5): Bitmap {
        return loadCrop(SpriteDef.B96x32.RES, SpriteDef.B96x32.frame(index), scale)
    }

    fun processBar(index: Int, scale: Int = 1): Bitmap {
        return loadCrop(SpriteDef.ProcessBar.RES, SpriteDef.ProcessBar.frame(index), scale)
    }

    fun setIcon(view: ImageView, index: Int, scale: Int = 5) {
        view.setImageBitmap(icon(index, scale))
    }

    fun setButton(view: ImageView, resId: Int, frame: SpriteFrame, scale: Int = 5, downFrame: SpriteFrame? = null) {
        val upBmp = loadCrop(resId, frame, scale)
        val down = downFrame ?: SpriteFrame(frame.x + frame.w, frame.y, frame.w, frame.h)
        val downBmp = loadCrop(resId, down, scale)
        view.setImageBitmap(upBmp)
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> view.setImageBitmap(downBmp)
                MotionEvent.ACTION_UP -> {
                    view.setImageBitmap(upBmp)
                    view.performClick()
                }
                MotionEvent.ACTION_CANCEL -> view.setImageBitmap(upBmp)
            }
            true
        }
    }

    fun backgroundDrawable(
        sheetRes: Int,
        frameIndex: Int,
        frameW: Int,
        frameH: Int,
        np: NinePatchRect,
        pixelScale: Int = 5,
    ): Drawable {
        val bmp = sheet(sheetRes)
        val srcX = frameIndex * frameW
        return PixelGraphics.ninePatchDrawable(bmp, srcX, 0, frameW, frameH, np, pixelScale)
    }

    fun background48(index: Int, pixelScale: Int = 5): Drawable {
        return backgroundDrawable(
            SpriteDef.Bg48.RES, index,
            SpriteDef.Bg48.FW, SpriteDef.Bg48.FH,
            SpriteDef.Bg48.NINE_PATCH, pixelScale,
        )
    }
}
