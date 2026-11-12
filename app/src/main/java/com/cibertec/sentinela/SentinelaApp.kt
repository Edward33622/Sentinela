package com.cibertec.sentinela

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.cibertec.sentinela.services.ApiClient
import com.cibertec.sentinela.utils.Conexion
import com.cibertec.sentinela.utils.SessionManager
import org.osmdroid.config.Configuration
import java.io.File

class SentinelaApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val session = SessionManager(this)
        // Tema elegido en Configuración (claro u oscuro); si no eligió, sigue al sistema
        AppCompatDelegate.setDefaultNightMode(session.themeMode)
        // Si el usuario configuró una IP manual diferente a la default local, la respeta; de lo contrario usa la nube (Railway)
        if (session.serverIp.isNotBlank() && session.serverIp != Conexion.DEFAULT_PC_IP) {
            val url = if (session.serverIp.startsWith("http")) session.serverIp else "http://${session.serverIp}:${Conexion.DEFAULT_PORT}"
            ApiClient.actualizarBaseUrl(url)
        } else {
            ApiClient.actualizarBaseUrl(Conexion.baseURL)
        }

        // Configuración del mapa (OpenStreetMap): identifica la app y guarda los tiles en caché interna
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
    }
}
