package com.radx.app

import android.app.Application
import android.content.Context
import com.radx.app.data.RadxStore

class RadxApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = RadxStore(this)
    }

    companion object {
        lateinit var instance: RadxApplication
            private set

        lateinit var store: RadxStore
            private set

        val context: Context get() = instance.applicationContext
    }
}
