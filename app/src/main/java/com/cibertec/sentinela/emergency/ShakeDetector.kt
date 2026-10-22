package com.cibertec.sentinela.emergency

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Detector de sacudidas del dispositivo (Shake Detector) para activar la alerta
 * de emergencia sin encender la pantalla ni tocar el botón (CU07).
 */
class ShakeDetector(private val onShakeListener: () -> Unit) : SensorEventListener {

    companion object {
        private const val SHAKE_THRESHOLD_GRAVITY = 2.7f
        private const val SHAKE_SLOP_TIME_MS = 500
        private const val SHAKE_COUNT_RESET_TIME_MS = 3000
    }

    private var shakeTimestamp: Long = 0
    private var shakeCount: Int = 0

    fun start(sensorManager: SensorManager): Boolean {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
        return sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop(sensorManager: SensorManager) {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        // Fuerza g neta
        val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

        if (gForce > SHAKE_THRESHOLD_GRAVITY) {
            val now = System.currentTimeMillis()
            // Ignorar eventos muy juntos
            if (shakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                return
            }

            // Reiniciar contador si pasó mucho tiempo
            if (shakeTimestamp + SHAKE_COUNT_RESET_TIME_MS < now) {
                shakeCount = 0
            }

            shakeTimestamp = now
            shakeCount++

            // Requiere 2 sacudidas consecutivas firmes para evitar falsos positivos
            if (shakeCount >= 2) {
                shakeCount = 0
                onShakeListener()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No requerido
    }
}
