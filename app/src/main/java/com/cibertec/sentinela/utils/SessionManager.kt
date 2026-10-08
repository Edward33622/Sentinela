package com.cibertec.sentinela.utils

import android.content.Context
import androidx.core.content.edit

/** Equivalente a UserDefaults: sesión del usuario y ajustes de la app. */
class SessionManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("sentinela_prefs", Context.MODE_PRIVATE)

    var userToken: String?
        get() = prefs.getString("userToken", null)
        set(value) = prefs.edit { putString("userToken", value) }

    var userId: Int
        get() = prefs.getInt("userId", 0)
        set(value) = prefs.edit { putInt("userId", value) }

    var modoDiscreto: Boolean
        get() = prefs.getBoolean("modoDiscreto", false)
        set(value) = prefs.edit { putBoolean("modoDiscreto", value) }

    var locationEnabled: Boolean
        get() = prefs.getBoolean("locationEnabled", true)
        set(value) = prefs.edit { putBoolean("locationEnabled", value) }

    var recordingEnabled: Boolean
        get() = prefs.getBoolean("recordingEnabled", true)
        set(value) = prefs.edit { putBoolean("recordingEnabled", value) }

    var selectedType: String?
        get() = prefs.getString("selectedType", null)
        set(value) = prefs.edit { putString("selectedType", value) }

    var selectedTypeDisplay: String?
        get() = prefs.getString("selectedType_display", null)
        set(value) = prefs.edit { putString("selectedType_display", value) }

    var profileImageFilename: String
        get() = prefs.getString("profileImageFilename", null) ?: "profile.jpg"
        set(value) = prefs.edit { putString("profileImageFilename", value) }

    fun cerrarSesion() = prefs.edit {
        remove("userToken")
        remove("userId")
    }
}
