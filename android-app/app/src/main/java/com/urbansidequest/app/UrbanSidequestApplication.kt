package com.urbansidequest.app

import android.app.Application
import com.amap.api.maps.MapsInitializer

class UrbanSidequestApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)
    }
}
