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

    private val binding: DialogInputBinding = DialogInputBinding.inflate(layoutInflater)

    private var onConfirmAction: ((String) -> Unit)? = null
    private var onCancelAction: (() -> Unit)? = null

    init {
        setContentView(binding.root)
        setupWindow()
        renderBackgrounds()
    }

    fun setTitle(text: String): InputDialog {
        binding.dialogTitle.text = text
        return this
    }

    fun setInitialText(text: String): InputDialog {
        binding.dialogInput.setText(text)
        binding.dialogInput.setSelection(text.length)
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
        SpriteLoader.setButton(binding.btnCancel, SpriteDef.B48x24.RES, SpriteDef.B48x24.frame(16), scale = 5)
        SpriteLoader.setButton(binding.btnConfirm, SpriteDef.B48x24.RES, SpriteDef.B48x24.frame(14), scale = 5)
    }

    private fun bindEvents() {
        binding.btnConfirm.setOnClickListener {
            val text = binding.dialogInput.text.toString().trim().ifEmpty { "默认用户" }
            onConfirmAction?.invoke(text)
            dismiss()
        }
        binding.btnCancel.setOnClickListener {
            onCancelAction?.invoke()
            dismiss()
        }
    }
}
