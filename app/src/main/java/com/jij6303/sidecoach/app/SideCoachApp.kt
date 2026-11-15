package com.jij6303.sidecoach.app

import android.app.Application
import com.jij6303.sidecoach.app.di.ServiceLocator

class SideCoachApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
