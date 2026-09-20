package com.archite.piecesoflife.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import com.archite.piecesoflife.util.PixelGraphics
import com.archite.piecesoflife.util.SpriteDef

class ThreeColorProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    companion object {
        private const val SCALE = 5
    }

    private val sheet: Bitmap = SpriteDef.loadSheet(SpriteDef.ProgressBar3.RES)
    private var done = 0
    private var failed = 0
    private var unfinished = 0

    fun setCounts(done: Int, failed: Int, unfinished: Int) {
        this.done = done
        this.failed = failed
        this.unfinished = unfinished
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            SpriteDef.ProgressBar3.FH * SCALE,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        PixelGraphics.drawThreeColorProgress(
            canvas, sheet, 0, 0, width, SCALE, done, failed, unfinished,
        )
    }
}
