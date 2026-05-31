package com.archite.piecesoflife.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import com.archite.piecesoflife.databinding.DialogInputBinding
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader

class InputDialog(context: Context) : Dialog(context) {

    enum class InputMode { USERNAME, FILENAME }

    private val binding: DialogInputBinding = DialogInputBinding.inflate(layoutInflater)

    private var onConfirmAction: ((String) -> Unit)? = null
    private var onCancelAction: (() -> Unit)? = null
    private var inputMode = InputMode.USERNAME

    init {
        setContentView(binding.root)
        setupWindow()
        renderBackgrounds()
    }

    fun setMode(mode: InputMode): InputDialog {
        inputMode = mode
        return this
    }

    fun setTitle(text: String): InputDialog {
        binding.dialogTitle.text = text
        return this
    }

    fun setInitialText(text: String): InputDialog {
        when (inputMode) {
            InputMode.USERNAME -> {
                binding.dialogInput.setText(text)
                binding.dialogInput.setSelection(text.length)
            }
            InputMode.FILENAME -> {
                binding.dialogInputMultiline.setText(text)
                binding.dialogInputMultiline.setSelection(text.length)
            }
        }
        return this
    }

    fun onConfirm(callback: (String) -> Unit): InputDialog {
        onConfirmAction = callback
        return this
    }

    fun onCancel(callback: () -> Unit): InputDialog {
        onCancelAction = callback
        return this
    }

    override fun show() {
        when (inputMode) {
            InputMode.USERNAME -> {
                binding.dialogInput.visibility = android.view.View.VISIBLE
                binding.dialogInputMultiline.visibility = android.view.View.GONE
            }
            InputMode.FILENAME -> {
                binding.dialogInput.visibility = android.view.View.GONE
                binding.dialogInputMultiline.visibility = android.view.View.VISIBLE
            }
        }
        renderSprites()
        bindEvents()
        super.show()
    }

    private fun setupWindow() {
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
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
        binding.dialogIcon.setImageBitmap(SpriteLoader.icon(31, scale = 5))
        binding.dialogTitle.setTextColor(android.graphics.Color.parseColor("#6f6806"))
        SpriteLoader.setButton(binding.btnCancel, SpriteDef.B48x24.RES, SpriteDef.B48x24.frame(2), scale = 5)
        SpriteLoader.setButton(binding.btnConfirm, SpriteDef.B48x24.RES, SpriteDef.B48x24.frame(0), scale = 5)
    }

    private fun bindEvents() {
        binding.btnConfirm.setOnClickListener {
            val text = when (inputMode) {
                InputMode.USERNAME -> binding.dialogInput.text.toString().trim().ifEmpty { "默认用户" }
                InputMode.FILENAME -> binding.dialogInputMultiline.text.toString().trim().ifEmpty { "untitled" }
            }
            onConfirmAction?.invoke(text)
            dismiss()
        }
        binding.btnCancel.setOnClickListener {
            onCancelAction?.invoke()
            dismiss()
        }
    }
}
