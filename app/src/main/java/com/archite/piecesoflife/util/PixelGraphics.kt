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

    private const val FRAME_LEFT = 0
    private const val FRAME_RIGHT = 1
    private const val FRAME_DONE = 2
    private const val FRAME_FAILED = 3
    private const val FRAME_UNFINISHED = 4

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

    /**
     * 绘制三段三色进度条。
     * 帧序：0=左盖, 1=右盖, 2=绿(完成), 3=红(失败), 4=白(未完成),
     *      5=绿红过渡, 6=绿白过渡, 7=红白过渡。
     * 左右盖各占一帧宽度，其中靠内的一半是透明窗，因此填充层先铺满内区、盖最后压在上层。
     */
    fun drawThreeColorProgress(
        canvas: Canvas, sheet: Bitmap,
        left: Int, top: Int, barWidth: Int, scale: Int,
        done: Int, failed: Int, unfinished: Int,
    ) {
        val fw = SpriteDef.ProgressBar3.FW
        val frameW = fw * scale
        val halfCap = frameW / 2
        val fillLeft = left + halfCap
        val innerWidth = left + barWidth - halfCap - fillLeft
        val units = (innerWidth + frameW - 1) / frameW

        if (units > 0) {
            val total = done + failed + unfinished
            if (total == 0) {
                tileFrame(canvas, sheet, FRAME_UNFINISHED, fillLeft, top, units, scale)
            } else {
                val segments = ArrayList<Pair<Int, Int>>(3)
                if (done > 0) segments.add(FRAME_DONE to done)
                if (failed > 0) segments.add(FRAME_FAILED to failed)
                if (unfinished > 0) segments.add(FRAME_UNFINISHED to unfinished)

                var junctionUnits = if (segments.size > 1) segments.size - 1 else 0
                var fillUnits = units - junctionUnits
                if (fillUnits < segments.size) {
                    junctionUnits = 0
                    fillUnits = units
                }

                val segUnits = IntArray(segments.size)
                var remaining = fillUnits
                for (i in segments.indices) {
                    val needAfter = segments.size - i - 1
                    segUnits[i] = if (i == segments.lastIndex) {
                        remaining
                    } else {
                        val proposed = (fillUnits.toFloat() * segments[i].second / total).toInt()
                        proposed.coerceIn(1, max(1, remaining - needAfter))
                    }
                    remaining -= segUnits[i]
                }

                var x = fillLeft
                for (i in segments.indices) {
                    tileFrame(canvas, sheet, segments[i].first, x, top, segUnits[i], scale)
                    x += segUnits[i] * frameW
                    if (junctionUnits > 0 && i != segments.lastIndex) {
                        val transition = transitionFrame(segments[i].first, segments[i + 1].first)
                        drawSprite(canvas, sheet, transition * fw, 0, fw, SpriteDef.ProgressBar3.FH, x.toFloat(), top.toFloat(), scale.toFloat())
                        x += frameW
                    }
                }
            }
        }

        drawSprite(canvas, sheet, FRAME_LEFT * fw, 0, fw, SpriteDef.ProgressBar3.FH, left.toFloat(), top.toFloat(), scale.toFloat())
        drawSprite(canvas, sheet, FRAME_RIGHT * fw, 0, fw, SpriteDef.ProgressBar3.FH, (left + barWidth - frameW).toFloat(), top.toFloat(), scale.toFloat())
    }

    private fun tileFrame(
        canvas: Canvas, sheet: Bitmap, frameIndex: Int,
        dstLeft: Int, dstTop: Int, units: Int, scale: Int,
    ) {
        val fw = SpriteDef.ProgressBar3.FW
        val frameW = fw * scale
        val frameH = SpriteDef.ProgressBar3.FH * scale
        for (i in 0 until units) {
            srcR.set(frameIndex * fw, 0, frameIndex * fw + fw, SpriteDef.ProgressBar3.FH)
            val x = dstLeft + i * frameW
            dstR.set(x, dstTop, x + frameW, dstTop + frameH)
            canvas.drawBitmap(sheet, srcR, dstR, paint)
        }
    }

    private fun transitionFrame(from: Int, to: Int): Int = when {
        from == FRAME_DONE && to == FRAME_FAILED -> 5
        from == FRAME_DONE && to == FRAME_UNFINISHED -> 6
        from == FRAME_FAILED && to == FRAME_UNFINISHED -> 7
        else -> from
    }

    fun crop(sheet: Bitmap, x: Int, y: Int, w: Int, h: Int, scale: Int = 1): Bitmap {
        val cropped = Bitmap.createBitmap(sheet, x, y, w, h)
        if (scale == 1) return cropped
        return Bitmap.createScaledBitmap(cropped, w * scale, h * scale, false)
    }

    /**
     * 绘制经验条。
     * 精灵图 process_bar.png 每帧 8×8：0=左端点, 1=已填充, 2=未填充, 3=右端点
     * @param sheet   process_bar 精灵图
     * @param ratio   填充比例 0.0~1.0
     * @param barWidth  目标总宽度（px）
     * @param barHeight 目标总高度（px）
     */
    fun drawExpBar(sheet: Bitmap, ratio: Float, barWidth: Int, barHeight: Int): Bitmap {
        val result = Bitmap.createBitmap(barWidth, barHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val capW = barHeight
        val fillW = barWidth - 2 * capW
        val filledW = (fillW * ratio.coerceIn(0f, 1f)).toInt()
        val unfilledW = fillW - filledW

        fun drawFrame(index: Int, dx: Int, dw: Int) {
            if (dw <= 0) return
            val sx = index * 8
            srcR.set(sx, 0, sx + 8, 8)
            dstR.set(dx, 0, dx + dw, barHeight)
            canvas.drawBitmap(sheet, srcR, dstR, paint)
        }

        drawFrame(0, 0, capW)
        if (filledW > 0) drawFrame(1, capW, filledW)
        if (unfilledW > 0) drawFrame(2, capW + filledW, unfilledW)
        drawFrame(3, barWidth - capW, capW)

        return result
    }
}
