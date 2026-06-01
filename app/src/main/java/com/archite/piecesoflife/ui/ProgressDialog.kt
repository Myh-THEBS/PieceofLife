package com.archite.piecesoflife.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import com.archite.piecesoflife.databinding.DialogProgressBinding
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader

class ProgressDialog(context: Context) : Dialog(context) {

    private val binding: DialogProgressBinding = DialogProgressBinding.inflate(layoutInflater)

    private var cancellable = true

    init {
        setContentView(binding.root)
        setupWindow()
        renderBackgrounds()
        renderSprites()
    }

    private fun setupWindow() {
        window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(
                (280 * context.resources.displayMetrics.density).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setGravity(Gravity.CENTER)
            attributes = attributes?.apply {
                width = (280 * context.resources.displayMetrics.density).toInt()
                dimAmount = 0.6f
            }
        }
    }

    private fun renderBackgrounds() {
        binding.dialogRoot.background = SpriteLoader.background48(index = 0, pixelScale = 5)
    }

    private fun renderSprites() {
        SpriteLoader.setIcon(binding.dialogIcon, index = 12, scale = 5)
        SpriteLoader.setButton(binding.btnSingle, SpriteDef.B64x24.RES, SpriteDef.B64x24.frame(2), scale = 5)
    }

    fun setTitle(text: String): ProgressDialog {
        binding.dialogTitle.text = text
        return this
    }

    fun setMessage(text: String): ProgressDialog {
        binding.dialogMessage.text = text
        return this
    }

    fun setCancellable(canCancel: Boolean): ProgressDialog {
        this.cancellable = canCancel
        setCancelable(canCancel)
        return this
    }

    fun onCancel(action: () -> Unit): ProgressDialog {
        binding.btnSingle.setOnClickListener {
            action()
            dismiss()
        }
        return this
    }

    fun updateProgress(current: Int, total: Int, info: String) {
        binding.tvProgressInfo.text = info
        drawBar(current, total)
    }

    fun setIndeterminate(info: String) {
        binding.tvProgressInfo.text = info
        drawBar(0, 1)
    }

    private fun drawBar(current: Int, total: Int) {
        if (total <= 0) return

        val scale = 3
        val segW = SpriteDef.ProcessBar.FW * scale
        val segH = SpriteDef.ProcessBar.FH * scale

        val barView = binding.ivProgressBar
        val leftWidth = segW
        val rightWidth = segW
        val availableWidth = if (barView.width > 0) {
            barView.width
        } else {
            (280 * context.resources.displayMetrics.density).toInt() - 64
        }
        val bodyWidth = availableWidth - leftWidth - rightWidth
        val filledWidth = (bodyWidth.toFloat() * current / total).toInt()
        val emptyWidth = bodyWidth - filledWidth

        val bitmap = android.graphics.Bitmap.createBitmap(availableWidth, segH, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)

        val leftBmp = SpriteLoader.loadCrop(SpriteDef.ProcessBar.RES, SpriteDef.ProcessBar.frame(0), scale)
        val filledBmp = SpriteLoader.loadCrop(SpriteDef.ProcessBar.RES, SpriteDef.ProcessBar.frame(1), scale)
        val emptyBmp = SpriteLoader.loadCrop(SpriteDef.ProcessBar.RES, SpriteDef.ProcessBar.frame(2), scale)
        val rightBmp = SpriteLoader.loadCrop(SpriteDef.ProcessBar.RES, SpriteDef.ProcessBar.frame(3), scale)

        canvas.drawBitmap(leftBmp, 0f, 0f, paint)

        if (filledWidth > 0) {
            val filled = android.graphics.Bitmap.createScaledBitmap(filledBmp, filledWidth, segH, true)
            canvas.drawBitmap(filled, leftWidth.toFloat(), 0f, paint)
        }
        if (emptyWidth > 0) {
            val empty = android.graphics.Bitmap.createScaledBitmap(emptyBmp, emptyWidth, segH, true)
            canvas.drawBitmap(empty, (leftWidth + filledWidth).toFloat(), 0f, paint)
        }

        canvas.drawBitmap(rightBmp, (availableWidth - rightWidth).toFloat(), 0f, paint)

        barView.setImageBitmap(bitmap)
    }
}
