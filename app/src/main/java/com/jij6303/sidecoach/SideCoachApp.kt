package com.jij6303.sidecoach

import android.app.Application
import com.jij6303.sidecoach.di.ServiceLocator

class SideCoachApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
