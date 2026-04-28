package com.archite.piecesoflife

import android.app.Application
import android.content.Context
import com.archite.piecesoflife.util.SpriteSheetManager

class PieceOfLifeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        SpriteSheetManager.load(this, R.raw.sprite_index)
    }

    companion object {
        lateinit var instance: PieceOfLifeApp
            private set

        fun getAppContext(): Context = instance.applicationContext
    }
}
