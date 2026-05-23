package com.archite.piecesoflife.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.core.graphics.drawable.toDrawable
import com.archite.piecesoflife.databinding.DialogPixelBinding
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader

class PixelDialog(context: Context) : Dialog(context) {

    enum class DialogType {
        INFO, WARN, ERROR,
    }

    enum class ButtonMode {
        SINGLE_CLOSE,
        SINGLE_KNOWN,
        DUAL_CONFIRM_CANCEL,
        DUAL_YES_NO,
        DUAL_DELETE_CANCEL,
        DUAL_IMPORT_CONFIRM,
        TRIPLE_RETURN_FAIL_SUCCESS,
    }

    private val binding: DialogPixelBinding = DialogPixelBinding.inflate(layoutInflater)

    private var onConfirmAction: (() -> Unit)? = null
    private var onCancelAction: (() -> Unit)? = null
    private var onFailAction: (() -> Unit)? = null
    private var dialogType: DialogType = DialogType.INFO
    private var buttonMode: ButtonMode = ButtonMode.SINGLE_KNOWN

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
        if (buttonMode == ButtonMode.SINGLE_KNOWN && type != DialogType.INFO) {
            buttonMode = ButtonMode.DUAL_CONFIRM_CANCEL
        }
        return this
    }

    fun setButtons(mode: ButtonMode): PixelDialog {
        buttonMode = mode
        return this
    }

    fun setConfirmText(text: String): PixelDialog = this
    fun setCancelText(text: String): PixelDialog = this

    fun onConfirm(callback: () -> Unit): PixelDialog {
        onConfirmAction = callback
        return this
    }

    fun onCancel(callback: () -> Unit): PixelDialog {
        onCancelAction = callback
        return this
    }

    fun onFail(callback: () -> Unit): PixelDialog {
        onFailAction = callback
        return this
    }

    fun setCancelableOutside(cancelable: Boolean): PixelDialog {
        setCanceledOnTouchOutside(cancelable)
        return this
    }

    override fun show() {
        renderButtons()
        bindEvents()
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
        binding.dialogRoot.background = SpriteLoader.background48(index = 0, pixelScale = 5)
    }

    private fun renderButtons() {
        binding.btnRowSingle.visibility = View.GONE
        binding.btnRowDual.visibility = View.GONE
        binding.btnRowTriple.visibility = View.GONE

        when (buttonMode) {
            ButtonMode.SINGLE_CLOSE -> {
                binding.btnRowSingle.visibility = View.VISIBLE
                SpriteLoader.setButton(binding.btnSingle,
                    SpriteDef.B64x24.RES, SpriteDef.B64x24.frame(0), 5)
            }
            ButtonMode.SINGLE_KNOWN -> {
                binding.btnRowSingle.visibility = View.VISIBLE
                SpriteLoader.setButton(binding.btnSingle,
                    SpriteDef.B64x24.RES, SpriteDef.B64x24.frame(2), 5)
            }
            ButtonMode.DUAL_CONFIRM_CANCEL -> {
                binding.btnRowDual.visibility = View.VISIBLE
                SpriteLoader.setButton(binding.btnCancel,
                    SpriteDef.B48x24.RES, SpriteDef.B48x24.frame(2), 5)
                SpriteLoader.setButton(binding.btnConfirm,
                    SpriteDef.B48x24.RES, SpriteDef.B48x24.frame(0), 5)
            }
            ButtonMode.DUAL_YES_NO -> {
                binding.btnRowDual.visibility = View.VISIBLE
                SpriteLoader.setButton(binding.btnCancel,
                    SpriteDef.B48x24.RES, SpriteDef.B48x24.frame(6), 5)
                SpriteLoader.setButton(binding.btnConfirm,
                    SpriteDef.B48x24.RES, SpriteDef.B48x24.frame(4), 5)
            }
            ButtonMode.DUAL_DELETE_CANCEL -> {
                binding.btnRowDual.visibility = View.VISIBLE
                SpriteLoader.setButton(binding.btnCancel,
                    SpriteDef.B48x24.RES, SpriteDef.B48x24.frame(2), 5)
                SpriteLoader.setButton(binding.btnConfirm,
                    SpriteDef.B48x24.RES, SpriteDef.B48x24.frame(8), 5)
            }
            ButtonMode.DUAL_IMPORT_CONFIRM -> {
                binding.btnRowDual.visibility = View.VISIBLE
                SpriteLoader.setButton(binding.btnCancel,
                    SpriteDef.B48x24.RES, SpriteDef.B48x24.frame(2), 5)
                SpriteLoader.setButton(binding.btnConfirm,
                    SpriteDef.B48x24.RES, SpriteDef.B48x24.frame(10), 5)
            }
            ButtonMode.TRIPLE_RETURN_FAIL_SUCCESS -> {
                binding.btnRowTriple.visibility = View.VISIBLE
                SpriteLoader.setButton(binding.btnLeft,
                    SpriteDef.B36x24.RES, SpriteDef.B36x24.frame(0), 5)
                SpriteLoader.setButton(binding.btnCenter,
                    SpriteDef.B36x24.RES, SpriteDef.B36x24.frame(2), 5)
                SpriteLoader.setButton(binding.btnRight,
                    SpriteDef.B36x24.RES, SpriteDef.B36x24.frame(4), 5)
            }
        }
    }

    private fun bindEvents() {
        binding.btnSingle.setOnClickListener {
            onConfirmAction?.invoke()
            dismiss()
        }

        binding.btnConfirm.setOnClickListener {
            onConfirmAction?.invoke()
            dismiss()
        }
        binding.btnCancel.setOnClickListener {
            onCancelAction?.invoke()
            dismiss()
        }

        binding.btnLeft.setOnClickListener {
            onCancelAction?.invoke()
            dismiss()
        }
        binding.btnCenter.setOnClickListener {
            onFailAction?.invoke()
            dismiss()
        }
        binding.btnRight.setOnClickListener {
            onConfirmAction?.invoke()
            dismiss()
        }
    }
}
