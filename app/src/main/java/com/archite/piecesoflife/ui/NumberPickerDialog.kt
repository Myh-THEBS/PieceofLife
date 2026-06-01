package com.archite.piecesoflife.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import com.archite.piecesoflife.R
import com.archite.piecesoflife.databinding.DialogNumberPickerBinding
import com.archite.piecesoflife.util.SpriteDef
import com.archite.piecesoflife.util.SpriteLoader

class NumberPickerDialog(context: Context) : Dialog(context) {

    enum class PickerMode { RANGE_INT, TIME }

    private val binding: DialogNumberPickerBinding = DialogNumberPickerBinding.inflate(layoutInflater)
    private var mode = PickerMode.RANGE_INT
    private var currentValue = 0
    private var currentHour = 22
    private var currentMinute = 0
    private var minValue = 0
    private var maxValue = 99
    private var step = 1
    private var onConfirmAction: ((Int) -> Unit)? = null

    init {
        setContentView(binding.root)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (300 * context.resources.displayMetrics.density).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setGravity(Gravity.CENTER)
            attributes = attributes?.apply {
                width = (300 * context.resources.displayMetrics.density).toInt()
                dimAmount = 0.6f
            }
        }
        binding.dialogRoot.background = SpriteLoader.background48(index = 0, pixelScale = 5)
    }

    fun setMode(mode: PickerMode): NumberPickerDialog {
        this.mode = mode
        return this
    }

    fun setTitle(text: String): NumberPickerDialog {
        binding.dialogTitle.text = text
        return this
    }

    fun setRange(min: Int, max: Int, step: Int = 1): NumberPickerDialog {
        this.minValue = min
        this.maxValue = max
        this.step = step
        return this
    }

    fun setInitialValue(value: Int): NumberPickerDialog {
        this.currentValue = value
        return this
    }

    fun setInitialTime(hour: Int, minute: Int): NumberPickerDialog {
        this.currentHour = hour.coerceIn(0, 23)
        this.currentMinute = minute.coerceIn(0, 59)
        return this
    }

    fun onConfirm(callback: (Int) -> Unit): NumberPickerDialog {
        onConfirmAction = callback
        return this
    }

    override fun show() {
        when (mode) {
            PickerMode.RANGE_INT -> {
                binding.rangeIntContainer.visibility = android.view.View.VISIBLE
                binding.timeContainer.visibility = android.view.View.GONE
                binding.tvRangeValue.text = currentValue.toString()
            }
            PickerMode.TIME -> {
                binding.rangeIntContainer.visibility = android.view.View.GONE
                binding.timeContainer.visibility = android.view.View.VISIBLE
                updateTimeDisplay()
            }
        }
        renderSprites()
        bindEvents()
        super.show()
    }

    private fun renderSprites() {
        binding.dialogIcon.setImageBitmap(SpriteLoader.icon(0, scale = 5))

        SpriteLoader.setButton(binding.btnCancel, SpriteDef.B48x24.RES, SpriteDef.B48x24.frame(2), scale = 5)
        SpriteLoader.setButton(binding.btnConfirm, SpriteDef.B48x24.RES, SpriteDef.B48x24.frame(0), scale = 5)

        SpriteLoader.setButton(binding.btnMinusRange, SpriteDef.B32.RES, SpriteDef.B32.frame(16), downFrame = SpriteDef.B32.frame(17), scale = 5)
        SpriteLoader.setButton(binding.btnPlusRange, SpriteDef.B32.RES, SpriteDef.B32.frame(14), downFrame = SpriteDef.B32.frame(15), scale = 5)

        SpriteLoader.setButton(binding.btnHourPlus, SpriteDef.B32.RES, SpriteDef.B32.frame(18), downFrame = SpriteDef.B32.frame(19), scale = 5)
        SpriteLoader.setButton(binding.btnHourMinus, SpriteDef.B32.RES, SpriteDef.B32.frame(20), downFrame = SpriteDef.B32.frame(21), scale = 5)
        SpriteLoader.setButton(binding.btnMinPlus, SpriteDef.B32.RES, SpriteDef.B32.frame(18), downFrame = SpriteDef.B32.frame(19), scale = 5)
        SpriteLoader.setButton(binding.btnMinMinus, SpriteDef.B32.RES, SpriteDef.B32.frame(20), downFrame = SpriteDef.B32.frame(21), scale = 5)
    }

    private fun bindEvents() {
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnConfirm.setOnClickListener {
            when (mode) {
                PickerMode.RANGE_INT -> onConfirmAction?.invoke(currentValue)
                PickerMode.TIME -> onConfirmAction?.invoke(currentHour * 100 + currentMinute)
            }
            dismiss()
        }

        binding.btnMinusRange.setOnClickListener {
            currentValue = (currentValue - step).coerceIn(minValue, maxValue)
            binding.tvRangeValue.text = currentValue.toString()
        }
        binding.btnPlusRange.setOnClickListener {
            currentValue = (currentValue + step).coerceIn(minValue, maxValue)
            binding.tvRangeValue.text = currentValue.toString()
        }

        binding.btnHourPlus.setOnClickListener {
            currentHour = (currentHour + 1) % 24
            updateTimeDisplay()
        }
        binding.btnHourMinus.setOnClickListener {
            currentHour = (currentHour - 1 + 24) % 24
            updateTimeDisplay()
        }
        binding.btnMinPlus.setOnClickListener {
            currentMinute = (currentMinute + 1) % 60
            updateTimeDisplay()
        }
        binding.btnMinMinus.setOnClickListener {
            currentMinute = (currentMinute - 1 + 60) % 60
            updateTimeDisplay()
        }
    }

    private fun updateTimeDisplay() {
        binding.tvHourValue.text = String.format("%02d", currentHour)
        binding.tvMinValue.text = String.format("%02d", currentMinute)
    }
}
