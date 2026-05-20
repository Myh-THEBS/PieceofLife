package com.archite.piecesoflife.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import com.archite.piecesoflife.R

data class SpriteFrame(val x: Int, val y: Int, val w: Int, val h: Int) {
    fun rect() = Rect(x, y, x + w, y + h)
}

object SpriteDef {

    object Icons {
        val RES = R.drawable.icons
        const val FW = 24; const val FH = 24
        const val COLS = 10
        fun frame(index: Int) = SpriteFrame((index % COLS) * FW, (index / COLS) * FH, FW, FH)
    }

    object Bg48 {
        val RES = R.drawable.background_48x48
        const val FW = 48; const val FH = 48
        val NINE_PATCH = NinePatchRect(16, 16, 16, 16)
        fun frame(index: Int) = SpriteFrame(index * FW, 0, FW, FH)
    }

    object B16 {
        val RES = R.drawable.button_16x16
        const val FW = 16; const val FH = 16
        fun frame(index: Int) = SpriteFrame(index * FW, 0, FW, FH)
    }

    object B24 {
        val RES = R.drawable.button_24x24
        const val FW = 24; const val FH = 24
        fun frame(index: Int) = SpriteFrame(index * FW, 0, FW, FH)
    }

    object B32 {
        val RES = R.drawable.button_32x32
        const val FW = 32; const val FH = 32
        fun frame(index: Int) = SpriteFrame(index * FW, 0, FW, FH)
    }

    object B48x24 {
        val RES = R.drawable.button_48x24
        const val FW = 48; const val FH = 24
        fun frame(index: Int) = SpriteFrame(index * FW, 0, FW, FH)
    }

    object B48x32 {
        val RES = R.drawable.button_48x32
        const val FW = 48; const val FH = 32
        fun frame(index: Int) = SpriteFrame(index * FW, 0, FW, FH)
    }

    object B72x32 {
        val RES = R.drawable.button_72x32
        const val FW = 72; const val FH = 32
        fun frame(index: Int) = SpriteFrame(index * FW, 0, FW, FH)
    }

    object B72x72 {
        val RES = R.drawable.button_72x72
        const val FW = 72; const val FH = 72
        fun frame(index: Int) = SpriteFrame(index * FW, 0, FW, FH)
    }

    object B96x32 {
        val RES = R.drawable.button_96x32
        const val FW = 96; const val FH = 32
        fun frame(index: Int) = SpriteFrame(index * FW, 0, FW, FH)
    }

    object ProcessBar {
        val RES = R.drawable.process_bar
        const val FW = 8; const val FH = 8
        // 帧顺序：0=左端点, 1=已填充段, 2=未填充段, 3=右端点
        fun frame(index: Int) = SpriteFrame(index * FW, 0, FW, FH)
    }

    fun loadSheet(resId: Int): Bitmap {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        return BitmapFactory.decodeResource(com.archite.piecesoflife.PieceOfLifeApp.getAppContext().resources, resId, opts)
    }
}
