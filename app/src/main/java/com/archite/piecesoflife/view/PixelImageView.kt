package com.archite.piecesoflife.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.archite.piecesoflife.R
import com.archite.piecesoflife.util.SpriteSheetManager
import kotlin.math.max

open class PixelImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var spriteName: String? = null
    var pixelScale: Int = 1
        protected set
    private var isNinePatch: Boolean = false

    private val paint = Paint().apply {
        isFilterBitmap = false
    }
    protected var sourceBitmap: Bitmap? = null
    protected var srcRect: Rect? = null
    private var ninePatchInfo: SpriteSheetManager.NinePatchInfo? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)

        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.PixelImageView)
            spriteName = a.getString(R.styleable.PixelImageView_spriteName)
            pixelScale = a.getInteger(R.styleable.PixelImageView_pixelScale, 5)
            isNinePatch = a.getBoolean(R.styleable.PixelImageView_ninePatch, false)
            a.recycle()
        }

        spriteName?.let { loadSprite(it) }
    }

    fun setSprite(name: String, scale: Int = pixelScale) {
        spriteName = name
        pixelScale = scale
        loadSprite(name)
    }

    protected fun loadSprite(name: String) {
        val rect = SpriteSheetManager.getRect(name) ?: return
        srcRect = Rect(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h)
        sourceBitmap = SpriteSheetManager.getSpriteBitmap(context, name)

        if (isNinePatch) {
            ninePatchInfo = SpriteSheetManager.getNinePatch(name)
        }

        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val src = srcRect
        if (src != null && !isNinePatch) {
            val w = src.width() * pixelScale
            val h = src.height() * pixelScale
            setMeasuredDimension(
                resolveSize(w, widthMeasureSpec),
                resolveSize(h, heightMeasureSpec)
            )
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isNinePatch && sourceBitmap != null && srcRect != null && ninePatchInfo != null) {
            drawNinePatch(canvas)
        } else if (sourceBitmap != null && srcRect != null) {
            drawSingle(canvas)
        }
    }

    private fun drawSingle(canvas: Canvas) {
        val src = srcRect ?: return
        val bmp = sourceBitmap ?: return
        val displayW = src.width() * pixelScale
        val displayH = src.height() * pixelScale
        val left = (width - displayW) / 2f
        val top = (height - displayH) / 2f
        val dst = Rect(
            left.toInt(), top.toInt(),
            (left + displayW).toInt(), (top + displayH).toInt()
        )
        canvas.drawBitmap(bmp, src, dst, paint)
    }

    private fun drawNinePatch(canvas: Canvas) {
        val bmp = sourceBitmap ?: return
        val src = srcRect ?: return
        val np = ninePatchInfo ?: return

        val srcW = src.width()
        val srcH = src.height()
        val dstW = width
        val dstH = height

        val leftFixed = np.left
        val topFixed = np.top
        val rightFixed = np.right
        val bottomFixed = np.bottom

        val centerXStretch = max(0, dstW - leftFixed - rightFixed)
        val centerYStretch = max(0, dstH - topFixed - bottomFixed)

        val centerSrcW = max(0, srcW - leftFixed - rightFixed)
        val centerSrcH = max(0, srcH - topFixed - bottomFixed)

        val srcLeft = src.left
        val srcTop = src.top

        drawPatch(canvas, bmp,
            srcLeft, srcTop, srcLeft + leftFixed, srcTop + topFixed,
            0, 0, leftFixed, topFixed
        )
        drawPatch(canvas, bmp,
            srcLeft + leftFixed, srcTop,
            srcLeft + leftFixed + centerSrcW, srcTop + topFixed,
            leftFixed, 0, leftFixed + centerXStretch, topFixed
        )
        drawPatch(canvas, bmp,
            srcLeft + leftFixed + centerSrcW, srcTop,
            srcLeft + srcW, srcTop + topFixed,
            leftFixed + centerXStretch, 0, dstW, topFixed
        )
        drawPatch(canvas, bmp,
            srcLeft, srcTop + topFixed,
            srcLeft + leftFixed, srcTop + topFixed + centerSrcH,
            0, topFixed, leftFixed, topFixed + centerYStretch
        )
        drawPatch(canvas, bmp,
            srcLeft + leftFixed, srcTop + topFixed,
            srcLeft + leftFixed + centerSrcW, srcTop + topFixed + centerSrcH,
            leftFixed, topFixed, leftFixed + centerXStretch, topFixed + centerYStretch
        )
        drawPatch(canvas, bmp,
            srcLeft + leftFixed + centerSrcW, srcTop + topFixed,
            srcLeft + srcW, srcTop + topFixed + centerSrcH,
            leftFixed + centerXStretch, topFixed, dstW, topFixed + centerYStretch
        )
        drawPatch(canvas, bmp,
            srcLeft, srcTop + topFixed + centerSrcH,
            srcLeft + leftFixed, srcTop + srcH,
            0, topFixed + centerYStretch, leftFixed, dstH
        )
        drawPatch(canvas, bmp,
            srcLeft + leftFixed, srcTop + topFixed + centerSrcH,
            srcLeft + leftFixed + centerSrcW, srcTop + srcH,
            leftFixed, topFixed + centerYStretch, leftFixed + centerXStretch, dstH
        )
        drawPatch(canvas, bmp,
            srcLeft + leftFixed + centerSrcW, srcTop + topFixed + centerSrcH,
            srcLeft + srcW, srcTop + srcH,
            leftFixed + centerXStretch, topFixed + centerYStretch, dstW, dstH
        )
    }

    private fun drawPatch(
        canvas: Canvas, bitmap: Bitmap,
        srcLeft: Int, srcTop: Int, srcRight: Int, srcBottom: Int,
        dstLeft: Int, dstTop: Int, dstRight: Int, dstBottom: Int
    ) {
        if (srcRight <= srcLeft || srcBottom <= srcTop) return
        if (dstRight <= dstLeft || dstBottom <= dstTop) return
        val src = Rect(srcLeft, srcTop, srcRight, srcBottom)
        val dst = Rect(dstLeft, dstTop, dstRight, dstBottom)
        canvas.drawBitmap(bitmap, src, dst, paint)
    }
}
