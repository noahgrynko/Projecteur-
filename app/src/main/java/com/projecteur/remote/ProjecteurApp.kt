package com.projecteur.remote

import android.app.Application

class ProjecteurApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this).also { it.start() }
    }
}
