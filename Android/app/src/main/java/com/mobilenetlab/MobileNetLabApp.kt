package com.mobilenetlab

import android.app.Application

class MobileNetLabApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: MobileNetLabApp
            private set
    }
}
