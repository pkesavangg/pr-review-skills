package com.greatergoods.meapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
