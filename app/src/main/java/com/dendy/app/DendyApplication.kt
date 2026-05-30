package com.dendy.app

import android.app.Application
import com.dendy.app.di.AppGraph
import com.dendy.app.di.DefaultAppGraph

class DendyApplication : Application() {
    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        appGraph = DefaultAppGraph(this).also { it.bootstrap() }
    }
}

