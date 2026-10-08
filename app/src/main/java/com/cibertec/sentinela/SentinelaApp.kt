package com.cibertec.sentinela

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class SentinelaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Configuración del mapa (OpenStreetMap): identifica la app y guarda los tiles en caché interna
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
    }
}
