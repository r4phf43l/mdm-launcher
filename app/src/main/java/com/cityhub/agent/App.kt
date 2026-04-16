package com.cityhub.agent

import android.app.Application

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        PrefsManager.init(this)
        PrefsManager.applyMdmConfig(this)   // sem-op em API < 18
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
