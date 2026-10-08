package com.cibertec.sentinela.emergency

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.cibertec.sentinela.R
import com.cibertec.sentinela.data.HistorialEntity
import com.cibertec.sentinela.data.SentinelaDatabase
import com.cibertec.sentinela.models.CreateEmergencyEventRequest
import com.cibertec.sentinela.models.CreateEmergencyLocationRequest
import com.cibertec.sentinela.services.ApiClient
import com.cibertec.sentinela.services.realizarPeticion
import com.cibertec.sentinela.utils.SessionManager
import com.cibertec.sentinela.views.HomeActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Protocolo de emergencia: alarma, creación del evento, alerta a contactos y rastreo
 * de ubicación. Corre como servicio en primer plano para seguir enviando la ubicación
 * con la app en segundo plano (equivalente a UIBackgroundModes = location en iOS).
 */
class EmergencyService : Service() {

    companion object {
        private const val TAG = "EmergencyService"
        private const val ACTION_START = "com.cibertec.sentinela.START_EMERGENCY"
        private const val ACTION_STOP = "com.cibertec.sentinela.STOP_EMERGENCY"
        private const val CANAL_ID = "sentinela_emergencia"
        private const val NOTIF_ID = 1
        private const val MIN_SEND_INTERVAL_MS = 10_000L
        private const val MIN_DISTANCE_METERS = 10f

        fun iniciar(context: Context) {
            context.startService(Intent(context, EmergencyService::class.java).setAction(ACTION_START))
        }

        fun detener(context: Context) {
            context.startService(Intent(context, EmergencyService::class.java).setAction(ACTION_STOP))
        }
    }

    private lateinit var session: SessionManager
    private lateinit var fusedLocation: FusedLocationProviderClient
    private val scope = EmergencyManager.scope
    private val handler = Handler(Looper.getMainLooper())

    private var toneGenerator: ToneGenerator? = null
    private var lastSentAt = 0L
    private var lastSentLocation: Location? = null

    private val soundTimer = object : Runnable {
        override fun run() {
            reproducirSonidoYVibracion()
            handler.postDelayed(this, 2000)
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            if (EmergencyManager.isServiceActive.value && session.locationEnabled) {
                enviarUbicacionActual(location)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        session = SessionManager(this)
        fusedLocation = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> iniciarProtocolo()
            ACTION_STOP -> finalizarEmergencia()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        detenerAlarmaSistema()
        fusedLocation.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    // MARK: - Protocolo

    private fun iniciarProtocolo() {
        Log.d(TAG, "INICIANDO PROTOCOLO DE EMERGENCIA")
        lastSentAt = 0L
        lastSentLocation = null
        mostrarNotificacion()

        if (!session.modoDiscreto) iniciarAlarmaSistema()

        scope.launch {
            val location = obtenerUbicacion()
            launch { enviarMensajesWhatsApp(location) }
            activarEmergenciaAPI(location)
        }
    }

    private fun finalizarEmergencia() {
        Log.d(TAG, "FINALIZANDO EMERGENCIA")
        detenerAlarmaSistema()
        fusedLocation.removeLocationUpdates(locationCallback)

        val eventId = EmergencyManager.currentEventId
        EmergencyManager.currentEventId = null
        if (eventId != null) {
            scope.launch { cerrarEmergenciaAPI(eventId) }
        }

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun activarEmergenciaAPI(location: Location?) {
        val token = session.userToken
        val userId = session.userId
        if (token == null || userId == 0 || location == null) {
            Log.e(TAG, "Datos faltantes para API")
            return
        }

        val request = CreateEmergencyEventRequest(
            userId = userId,
            type = "TAP",
            description = "Emergencia activada por usuario",
            latitude = location.latitude,
            longitude = location.longitude
        )

        realizarPeticion { ApiClient.emergencyEvents.crearEvento(ApiClient.bearer(token), request) }
            .onSuccess { response ->
                Log.d(TAG, "Evento creado ID: ${response.id}")
                guardarHistorialLocal(response.id.toLong(), "Pánico", location)

                // Si el usuario desactivó mientras se creaba el evento, se cierra de inmediato
                if (!EmergencyManager.isServiceActive.value) {
                    cerrarEmergenciaAPI(response.id)
                    return
                }
                EmergencyManager.currentEventId = response.id
                iniciarRastreoUbicacion()
            }
            .onFailure { Log.e(TAG, "Error creando evento: $it") }
    }

    @SuppressLint("MissingPermission")
    private fun iniciarRastreoUbicacion() {
        fusedLocation.removeLocationUpdates(locationCallback)

        if (!session.locationEnabled) {
            Log.w(TAG, "Rastreo no iniciado: ubicación desactivada en ajustes de la app")
            return
        }
        if (!tienePermisoUbicacion()) {
            Log.w(TAG, "Rastreo no iniciado: permisos de ubicación no concedidos")
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, MIN_SEND_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_SEND_INTERVAL_MS)
            .build()
        fusedLocation.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun enviarUbicacionActual(location: Location) {
        val eventId = EmergencyManager.currentEventId ?: return
        val token = session.userToken ?: return

        val now = System.currentTimeMillis()
        if (lastSentAt != 0L && now - lastSentAt < MIN_SEND_INTERVAL_MS) return
        lastSentLocation?.let { if (it.distanceTo(location) < MIN_DISTANCE_METERS) return }

        val request = CreateEmergencyLocationRequest(
            emergencyEventId = eventId,
            latitude = location.latitude,
            longitude = location.longitude
        )

        scope.launch {
            realizarPeticion {
                ApiClient.emergencyLocations.crearUbicacionEmergencia(ApiClient.bearer(token), request)
            }.onSuccess {
                lastSentAt = now
                lastSentLocation = location
                Log.d(TAG, "Trace enviado")
            }.onFailure { Log.w(TAG, "Error enviando trace: $it") }
        }
    }

    private suspend fun cerrarEmergenciaAPI(eventId: Int) {
        val token = session.userToken ?: return
        realizarPeticion { ApiClient.emergencyEvents.resolverEvento(eventId, ApiClient.bearer(token)) }
            .onSuccess { Log.d(TAG, "Evento cerrado en servidor") }
            .onFailure { Log.e(TAG, "Error cerrando evento: $it") }
    }

    private suspend fun guardarHistorialLocal(id: Long, tipo: String, location: Location) {
        SentinelaDatabase.get(this).historialDao().insertar(
            HistorialEntity(
                eventId = id,
                fecha = System.currentTimeMillis(),
                latitude = location.latitude,
                longitude = location.longitude,
                tipo = tipo
            )
        )
    }

    private suspend fun enviarMensajesWhatsApp(location: Location?) {
        val token = session.userToken ?: return
        val userId = session.userId
        if (userId == 0) return

        val lat = location?.latitude ?: 0.0
        val lon = location?.longitude ?: 0.0
        val link = "https://maps.google.com/?q=$lat,$lon"

        // Backend: POST /api/contacts/emergency/alert?location=<link>&userId=<id>
        try {
            val response = ApiClient.contacts.enviarAlertaEmergencia(link, userId, ApiClient.bearer(token))
            if (response.isSuccessful) {
                Log.d(TAG, "Alerta WhatsApp solicitada al backend")
            } else {
                Log.w(TAG, "Respuesta no exitosa al enviar alerta (${response.code()})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando alerta: $e")
        }
    }

    // MARK: - Ubicación

    private fun tienePermisoUbicacion(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private suspend fun obtenerUbicacion(): Location? {
        if (!tienePermisoUbicacion()) return null
        return try {
            fusedLocation.lastLocation.await()
                ?: fusedLocation.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo obtener la ubicación: $e")
            null
        }
    }

    // MARK: - Alarma

    private fun iniciarAlarmaSistema() {
        handler.removeCallbacks(soundTimer)
        handler.post(soundTimer)
    }

    private fun detenerAlarmaSistema() {
        handler.removeCallbacks(soundTimer)
        toneGenerator?.release()
        toneGenerator = null
    }

    private fun reproducirSonidoYVibracion() {
        try {
            if (toneGenerator == null) toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1500)
        } catch (e: RuntimeException) {
            Log.w(TAG, "No se pudo reproducir la alarma: $e")
        }
        getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    // MARK: - Notificación de servicio en primer plano

    private fun mostrarNotificacion() {
        // Sin permiso de ubicación no se puede declarar el servicio como "location"
        if (!tienePermisoUbicacion()) return

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CANAL_ID, getString(R.string.canal_emergencia), NotificationManager.IMPORTANCE_LOW)
        )

        val abrirApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notificacion = NotificationCompat.Builder(this, CANAL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Emergencia activa")
            .setContentText("Sentinela está compartiendo tu ubicación con tu red de seguridad.")
            .setOngoing(true)
            .setContentIntent(abrirApp)
            .build()

        val tipo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else 0

        try {
            ServiceCompat.startForeground(this, NOTIF_ID, notificacion, tipo)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo iniciar en primer plano: $e")
        }
    }
}
