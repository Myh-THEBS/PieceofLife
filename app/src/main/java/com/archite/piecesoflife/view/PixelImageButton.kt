package com.archite.piecesoflife.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import com.archite.piecesoflife.R

class PixelImageButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PixelImageView(context, attrs, defStyleAttr) {

    private var spriteUp: String? = null
    private var spriteDown: String? = null
    private var isPressedState: Boolean = false
    private var isToggleMode: Boolean = false

    private var onClickListener: OnClickListener? = null

    init {
        isClickable = true
        isFocusable = true

        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.PixelImageButton)
            spriteUp = a.getString(R.styleable.PixelImageButton_spriteUp)
            spriteDown = a.getString(R.styleable.PixelImageButton_spriteDown)
            val scale = a.getInteger(R.styleable.PixelImageButton_pixelScale, 5)
            val defaultPressed = a.getBoolean(R.styleable.PixelImageButton_defaultPressed, false)
            a.recycle()

            pixelScale = scale

            if (spriteUp != null && spriteDown != null) {
                if (defaultPressed) {
                    isPressedState = true
                    loadSprite(spriteDown!!)
                } else {
                    loadSprite(spriteUp!!)
                }
            } else {
                spriteUp?.let { loadSprite(it) }
            }
        }
    }

    fun setSprites(up: String, down: String, startPressed: Boolean = false) {
        spriteUp = up
        spriteDown = down
        isPressedState = startPressed
        loadSprite(if (startPressed) down else up)
    }

    fun isPressedDown(): Boolean = isPressedState

    fun setPressedDown(pressed: Boolean) {
        if (spriteUp == null || spriteDown == null) return
        isPressedState = pressed
        loadSprite(if (pressed) spriteDown!! else spriteUp!!)
    }

    fun setToggleMode(toggle: Boolean) {
        isToggleMode = toggle
    }

    override fun setOnClickListener(l: OnClickListener?) {
        onClickListener = l
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (spriteUp == null || spriteDown == null) {
            return super.onTouchEvent(event)
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isPressedState = true
                loadSprite(spriteDown!!)
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            }
            MotionEvent.ACTION_UP -> {
                if (isToggleMode) {
                    // 开关模式：不弹起，等外部调用 setPressedDown 切回
                } else {
                    isPressedState = false
                    loadSprite(spriteUp!!)
                }
                if (containsPoint(event.x, event.y)) {
                    onClickListener?.onClick(this)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (!isToggleMode) {
                    isPressedState = false
                    loadSprite(spriteUp!!)
                }
            }
        }
        return true
    }

    private fun containsPoint(x: Float, y: Float): Boolean {
        return x >= 0 && x <= width && y >= 0 && y <= height
    }
}
