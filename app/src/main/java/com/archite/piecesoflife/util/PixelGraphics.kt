package com.archite.piecesoflife.util

import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.annotation.IntRange
import kotlin.math.max
import kotlin.math.min

data class NinePatchRect(
    val left: Int, val top: Int,
    val right: Int, val bottom: Int
)

object PixelGraphics {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        isFilterBitmap = false
    }

    private val srcR = Rect()
    private val dstR = Rect()

    fun drawSprite(
        canvas: Canvas, sheet: Bitmap,
        srcX: Int, srcY: Int, srcW: Int, srcH: Int,
        dstLeft: Float, dstTop: Float, scale: Float,
    ) {
        srcR.set(srcX, srcY, srcX + srcW, srcY + srcH)
        val w = (srcW * scale).toInt()
        val h = (srcH * scale).toInt()
        dstR.set(dstLeft.toInt(), dstTop.toInt(), dstLeft.toInt() + w, dstTop.toInt() + h)
        canvas.drawBitmap(sheet, srcR, dstR, paint)
    }

    fun drawNinePatch(
        canvas: Canvas, sheet: Bitmap,
        srcX: Int, srcY: Int, srcW: Int, srcH: Int,
        np: NinePatchRect,
        dstLeft: Int, dstTop: Int, dstRight: Int, dstBottom: Int,
        pixelScale: Int = 1,
    ) {
        val l = np.left; val t = np.top; val r = np.right; val b = np.bottom
        val sx = srcX; val sy = srcY

        val tl = l * pixelScale     // 左上角显示宽度
        val tt = t * pixelScale     // 左上角显示高度
        val tr = r * pixelScale     // 右下角显示宽度
        val tb = b * pixelScale     // 右下角显示高度
        val cw = (srcW - l - r) * pixelScale  // 中心平铺块宽度
        val ch = (srcH - t - b) * pixelScale  // 中心平铺块高度

        val leftEnd = dstLeft + tl
        val topEnd = dstTop + tt
        val rightStart = dstRight - tr
        val bottomStart = dstBottom - tb

        fun drawOne(sl: Int, st: Int, sr: Int, sb: Int, dl: Int, dt: Int, dr: Int, db: Int) {
            if (sr <= sl || sb <= st || dr <= dl || db <= dt) return
            srcR.set(sl, st, sr, sb); dstR.set(dl, dt, dr, db)
            canvas.drawBitmap(sheet, srcR, dstR, paint)
        }

        fun drawTiled(
            sl: Int, st: Int, tileSrcW: Int, tileSrcH: Int,
            dl: Int, dt: Int, dr: Int, db: Int,
        ) {
            val tileW = tileSrcW * pixelScale
            val tileH = tileSrcH * pixelScale
            if (tileW <= 0 || tileH <= 0) return
            var y = dt
            while (y < db) {
                var x = dl
                while (x < dr) {
                    srcR.set(sl, st, sl + tileSrcW, st + tileSrcH)
                    dstR.set(x, y, min(x + tileW, dr), min(y + tileH, db))
                    canvas.drawBitmap(sheet, srcR, dstR, paint)
                    x += tileW
                }
                y += tileH
            }
        }

        drawOne(sx, sy, sx + l, sy + t, dstLeft, dstTop, leftEnd, topEnd)
        drawOne(sx + srcW - r, sy, sx + srcW, sy + t, rightStart, dstTop, dstRight, topEnd)
        drawOne(sx, sy + srcH - b, sx + l, sy + srcH, dstLeft, bottomStart, leftEnd, dstBottom)
        drawOne(sx + srcW - r, sy + srcH - b, sx + srcW, sy + srcH, rightStart, bottomStart, dstRight, dstBottom)

        drawTiled(sx + l, sy, srcW - l - r, t, leftEnd, dstTop, rightStart, topEnd)
        drawTiled(sx + l, sy + srcH - b, srcW - l - r, b, leftEnd, bottomStart, rightStart, dstBottom)
        drawTiled(sx, sy + t, l, srcH - t - b, dstLeft, topEnd, leftEnd, bottomStart)
        drawTiled(sx + srcW - r, sy + t, r, srcH - t - b, rightStart, topEnd, dstRight, bottomStart)
        drawTiled(sx + l, sy + t, srcW - l - r, srcH - t - b, leftEnd, topEnd, rightStart, bottomStart)
    }

    fun ninePatchDrawable(
        sheet: Bitmap,
        srcX: Int, srcY: Int, srcW: Int, srcH: Int,
        np: NinePatchRect,
        pixelScale: Int = 1,
    ): Drawable = object : Drawable() {

        override fun draw(canvas: Canvas) {
            val b = bounds
            drawNinePatch(canvas, sheet, srcX, srcY, srcW, srcH, np, b.left, b.top, b.right, b.bottom, pixelScale)
        }

        override fun setAlpha(@IntRange(from = 0, to = 255) alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    fun crop(sheet: Bitmap, x: Int, y: Int, w: Int, h: Int, scale: Int = 1): Bitmap {
        val cropped = Bitmap.createBitmap(sheet, x, y, w, h)
        if (scale == 1) return cropped
        return Bitmap.createScaledBitmap(cropped, w * scale, h * scale, false)
    }
}
