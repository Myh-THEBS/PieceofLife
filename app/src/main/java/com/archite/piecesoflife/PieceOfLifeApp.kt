package com.archite.piecesoflife

import android.app.Application
import android.content.Context

class PieceOfLifeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: PieceOfLifeApp
            private set

        fun getAppContext(): Context = instance.applicationContext
    }
}
