package com.kaze.app

import android.app.Application
import com.kaze.app.net.KazeConnectionManager

class KazeApplication : Application() {
    lateinit var connectionManager: KazeConnectionManager
        private set

    override fun onCreate() {
        super.onCreate()
        connectionManager = KazeConnectionManager(applicationContext)
    }
}
