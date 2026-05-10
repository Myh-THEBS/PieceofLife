package com.archite.piecesoflife.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import com.archite.piecesoflife.databinding.DialogPixelBinding
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader
import androidx.core.graphics.toColorInt
import androidx.core.graphics.drawable.toDrawable

class PixelDialog(context: Context) : Dialog(context) {

    enum class DialogType {
        INFO,
        WARN,
        ERROR,
    }

    private val binding: DialogPixelBinding =
        DialogPixelBinding.inflate(layoutInflater)

    private var onConfirmAction: (() -> Unit)? = null
    private var onCancelAction: (() -> Unit)? = null
    private var dialogType: DialogType = DialogType.INFO

    init {
        setContentView(binding.root)
        setupWindow()
        renderBackgrounds()
    }

    fun setTitle(text: String): PixelDialog {
        binding.dialogTitle.text = text
        return this
    }

    fun setMessage(text: String): PixelDialog {
        binding.dialogMessage.text = text
        return this
    }

    fun setType(type: DialogType): PixelDialog {
        dialogType = type
        val iconIndex = when (type) {
            DialogType.INFO -> 30
            DialogType.WARN -> 31
            DialogType.ERROR -> 32
        }
        val titleColor = when (type) {
            DialogType.INFO -> "#217ACE".toColorInt()
            DialogType.WARN -> "#6f6806".toColorInt()
            DialogType.ERROR -> "#D24C4C".toColorInt()
        }
        SpriteLoader.setIcon(binding.dialogIcon, index = iconIndex, scale = 5)
        binding.dialogTitle.setTextColor(titleColor)
        return this
    }

    fun setConfirmText(text: String): PixelDialog {
        return this
    }

    fun setCancelText(text: String): PixelDialog {
        return this
    }

    fun onConfirm(callback: () -> Unit): PixelDialog {
        onConfirmAction = callback
        return this
    }

    fun onCancel(callback: () -> Unit): PixelDialog {
        onCancelAction = callback
        return this
    }

    fun setCancelableOutside(cancelable: Boolean): PixelDialog {
        setCanceledOnTouchOutside(cancelable)
        return this
    }

    override fun show() {
        renderButtons()
        bindEvents()
        setOnCancelListener {
            onCancelAction?.invoke()
        }
        super.show()
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
        binding.dialogRoot.background =
            SpriteLoader.background48(index = 0, pixelScale = 5)
    }

    private fun renderButtons() {
        // INFO：仅确认按钮；WARN/ERROR：取消+确认
        if (dialogType == DialogType.INFO) {
            binding.btnCancel.visibility = View.GONE
            binding.btnConfirm.layoutParams = binding.btnConfirm.layoutParams?.apply {
                if (this is ViewGroup.MarginLayoutParams) {
                    marginStart = 0
                    marginEnd = 0
                }
            }
            SpriteLoader.setButton(
            binding.btnConfirm,
            SpriteDef.B48x24.RES,
            frame = SpriteDef.B48x24.frame(12),
            scale = 5,
            )
        } else {
            binding.btnCancel.visibility = View.VISIBLE
            SpriteLoader.setButton(
                binding.btnCancel,
                SpriteDef.B48x24.RES,
                frame = SpriteDef.B48x24.frame(16),
                scale = 5,
            )
            SpriteLoader.setButton(
            binding.btnConfirm,
            SpriteDef.B48x24.RES,
            frame = SpriteDef.B48x24.frame(14),
            scale = 5,
            )
        }

        
    }

    private fun bindEvents() {
        binding.btnConfirm.setOnClickListener {
            onConfirmAction?.invoke()
            dismiss()
        }
        binding.btnCancel.setOnClickListener {
            onCancelAction?.invoke()
            dismiss()
        }
    }
}
