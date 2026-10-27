package com.cibertec.sentinela.emergency

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.cibertec.sentinela.R
import com.cibertec.sentinela.utils.SessionManager
import com.cibertec.sentinela.views.HomeActivity

/**
 * Servicio en primer plano para mantener activa la detección de sacudidas (Shake)
 * incluso cuando la aplicación está en segundo plano o el teléfono tiene la pantalla bloqueada.
 */
class ShakeService : Service() {

    companion object {
        private const val TAG = "ShakeService"
        const val ACTION_START = "com.cibertec.sentinela.SHAKE_START"
        const val ACTION_STOP = "com.cibertec.sentinela.SHAKE_STOP"
        private const val CANAL_ID = "sentinela_shake_canal"
        private const val NOTIF_ID = 2001

        fun iniciar(context: Context) {
            val intent = Intent(context, ShakeService::class.java).setAction(ACTION_START)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error iniciando ShakeService: $e")
            }
        }

        fun detener(context: Context) {
            val intent = Intent(context, ShakeService::class.java).setAction(ACTION_STOP)
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error deteniendo ShakeService: $e")
            }
        }
    }

    private lateinit var session: SessionManager
    private lateinit var sensorManager: SensorManager
    private var shakeDetector: ShakeDetector? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        session = SessionManager(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sentinela::ShakeLock")

        shakeDetector = ShakeDetector {
            onShakeDetected()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (session.shakeEnabled) {
                    iniciarDeteccion()
                } else {
                    detenerDeteccion()
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                detenerDeteccion()
                stopSelf()
            }
            else -> {
                if (session.shakeEnabled) {
                    iniciarDeteccion()
                } else {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun iniciarDeteccion() {
        if (isRunning) return
        isRunning = true

        mostrarNotificacion()

        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo adquirir wakeLock: $e")
        }

        shakeDetector?.start(sensorManager)
        Log.d(TAG, "ShakeService iniciado en segundo plano")
    }

    private fun detenerDeteccion() {
        if (!isRunning) return
        isRunning = false

        shakeDetector?.stop(sensorManager)

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error liberando wakeLock: $e")
        }

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Log.d(TAG, "ShakeService detenido")
    }

    private fun onShakeDetected() {
        if (!session.shakeEnabled) return
        if (EmergencyManager.isServiceActive.value) {
            Log.d(TAG, "Sacudida detectada pero la emergencia ya está activa")
            return
        }

        Log.w(TAG, "🚨 SACUDIDA DETECTADA EN SEGUNDO PLANO - INICIANDO EMERGENCIA")
        EmergencyManager.isServiceActive.value = true
        EmergencyService.iniciar(applicationContext)

        val openIntent = Intent(applicationContext, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(HomeActivity.EXTRA_TRIGGER_EMERGENCY, true)
            putExtra("EXTRA_REASON_SHAKE", true)
        }

        try {
            startActivity(openIntent)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo iniciar Activity en primer plano: $e")
        }
    }

    private fun mostrarNotificacion() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CANAL_ID,
                "Protección Antiasalto (Shake SOS)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitorea sacudidas del dispositivo para emergencias"
                setShowBadge(false)
            }
            manager?.createNotificationChannel(channel)
        }

        val abrirApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notificacion = NotificationCompat.Builder(this, CANAL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Sentinela: Protección Activa")
            .setContentText("Detección de sacudida (Shake SOS) activa en segundo plano.")
            .setOngoing(true)
            .setContentIntent(abrirApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val tipo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0

        try {
            ServiceCompat.startForeground(this, NOTIF_ID, notificacion, tipo)
        } catch (e: Exception) {
            Log.e(TAG, "Error al invocar startForeground en ShakeService: $e")
        }
    }

    override fun onDestroy() {
        detenerDeteccion()
        super.onDestroy()
    }
}
