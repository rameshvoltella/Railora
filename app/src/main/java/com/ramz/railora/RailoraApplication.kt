package com.ramz.railora

import android.app.Application
import com.ramz.railora.data.RailData
import org.osmdroid.config.Configuration
import java.io.File

class RailoraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RailData.init(this)
        // OpenStreetMap tiles: identify the app, and keep the tile cache in app storage.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
    }
}
