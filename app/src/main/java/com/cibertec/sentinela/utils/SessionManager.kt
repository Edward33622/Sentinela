package com.cibertec.sentinela.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Equivalente a UserDefaults: sesión del usuario y ajustes de la app. */
class SessionManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("sentinela_prefs", Context.MODE_PRIVATE)
    private val securePinStore = SecurePinStore(context)

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

    /** true cuando el usuario ya pasó por las tarjetas de bienvenida. No se borra al cerrar sesión. */
    var onboardingVisto: Boolean
        get() = prefs.getBoolean("onboardingVisto", false)
        set(value) = prefs.edit { putBoolean("onboardingVisto", value) }

    var profileImageFilename: String
        get() = prefs.getString("profileImageFilename", null) ?: "profile.jpg"
        set(value) = prefs.edit { putString("profileImageFilename", value) }

    var pinSeguridad: String
        get() = securePinStore.get("pinSeguridad", userId)
        set(value) = securePinStore.set("pinSeguridad", userId, value)

    var pinCoaccion: String
        get() = securePinStore.get("pinCoaccion", userId)
        set(value) = securePinStore.set("pinCoaccion", userId, value)

    var pinsConfigured: Boolean
        get() = prefs.getBoolean("pinsConfigured", false)
        set(value) = prefs.edit { putBoolean("pinsConfigured", value) }

    /** El tutorial se registra por cuenta para no repetirlo en cada inicio. */
    var tutorialSentinelaVisto: Boolean
        get() = prefs.getBoolean("tutorialSentinelaVisto_$userId", false)
        set(value) = prefs.edit { putBoolean("tutorialSentinelaVisto_$userId", value) }

    var shakeEnabled: Boolean
        get() = prefs.getBoolean("shakeEnabled", true)
        set(value) = prefs.edit { putBoolean("shakeEnabled", value) }

    /** Tema de la app: MODE_NIGHT_NO (claro), MODE_NIGHT_YES (oscuro) o MODE_NIGHT_FOLLOW_SYSTEM (por defecto). */
    var themeMode: Int
        get() = prefs.getInt("themeMode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = prefs.edit { putInt("themeMode", value) }

    var mapDarkTheme: Boolean
        get() = prefs.getBoolean("mapDarkTheme", false)
        set(value) = prefs.edit { putBoolean("mapDarkTheme", value) }

    fun mapDarkThemeFor(context: Context): Boolean {
        if (prefs.contains("mapDarkTheme")) return mapDarkTheme
        val nightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /** Plan actual: "FREE", "PRO" o "FAMILIA" */
    var planSuscripcion: String
        get() = prefs.getString("planSuscripcion", "FREE") ?: "FREE"
        set(value) = prefs.edit { putString("planSuscripcion", value) }

    var serverIp: String
        get() = prefs.getString("serverIp", "192.168.1.34") ?: "192.168.1.34"
        set(value) = prefs.edit { putString("serverIp", value) }

    var ultimoServidorConectado: String
        get() = prefs.getString("ultimoServidorConectado", "") ?: ""
        set(value) = prefs.edit { putString("ultimoServidorConectado", value) }

    private fun obtenerFechaHoy(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    /** Fecha del último registro de consultas diarias (formato YYYY-MM-DD) */
    private var fechaUltimaConsultaIa: String
        get() = prefs.getString("fechaUltimaConsultaIa", "") ?: ""
        set(value) = prefs.edit { putString("fechaUltimaConsultaIa", value) }

    /** Cantidad de consultas a Sentinela IA realizadas hoy en el plan Free */
    var consultasIaHoy: Int
        get() {
            val hoy = obtenerFechaHoy()
            if (fechaUltimaConsultaIa != hoy) {
                // Nuevo día: reinicio automático a 0
                prefs.edit {
                    putString("fechaUltimaConsultaIa", hoy)
                    putInt("consultasIaHoy", 0)
                }
                return 0
            }
            return prefs.getInt("consultasIaHoy", 0)
        }
        set(value) = prefs.edit {
            putString("fechaUltimaConsultaIa", obtenerFechaHoy())
            putInt("consultasIaHoy", value)
        }

    /** Retrocompatibilidad con consultasIaUsadas */
    var consultasIaUsadas: Int
        get() = consultasIaHoy
        set(value) {
            consultasIaHoy = value
        }

    fun esPro(): Boolean = planSuscripcion == "PRO" || planSuscripcion == "FAMILIA"

    fun esFamilia(): Boolean = planSuscripcion == "FAMILIA"

    fun maxContactos(): Int = if (esPro()) 999 else 2

    fun puedeAgregarContacto(contactosActuales: Int): Boolean = contactosActuales < maxContactos()

    /** Límite de IA en el modelo Free: 4 consultas al día con reinicio diario */
    fun maxConsultasIaDia(): Int = if (esPro()) 9999 else 4

    fun maxConsultasIaMes(): Int = if (esPro()) 9999 else 120

    fun puedeUsarIa(): Boolean = esPro() || consultasIaHoy < maxConsultasIaDia()

    fun consultasIaRestantesDia(): Int = if (esPro()) 9999 else (maxConsultasIaDia() - consultasIaHoy).coerceAtLeast(0)

    fun registrarConsultaIa() {
        if (!esPro()) {
            val actuales = consultasIaHoy
            consultasIaHoy = actuales + 1
        }
    }

    fun actualizarPlan(nuevoPlan: String) {
        planSuscripcion = nuevoPlan
    }

    fun cerrarSesion() {
        // Al leerlos se migran a almacenamiento cifrado antes de retirar la sesión.
        if (userId > 0) {
            pinSeguridad
            pinCoaccion
        }
        prefs.edit {
            remove("userToken")
            remove("userId")
            remove("pinsConfigured")
        }
    }
}
