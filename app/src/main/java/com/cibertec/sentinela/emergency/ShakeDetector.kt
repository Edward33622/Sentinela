package com.cibertec.sentinela.emergency

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * Detector de sacudidas del dispositivo (Shake Detector) para activar la alerta
 * de emergencia sin encender la pantalla ni tocar el botón (CU07).
 */
class ShakeDetector(private val onShakeListener: () -> Unit) : SensorEventListener {

    companion object {
        // Aceleración lineal en m/s², después de retirar la gravedad del dispositivo.
        private const val SHAKE_THRESHOLD_MPS2 = 13.0f
        private const val MIN_PEAK_INTERVAL_MS = 130L
        private const val MAX_PEAK_INTERVAL_MS = 650L
        private const val MAX_SEQUENCE_DURATION_MS = 1800L
        // Cuatro picos alternos representan dos vaivenes deliberados.
        private const val REQUIRED_ALTERNATING_PEAKS = 4
        private const val GRAVITY_FILTER_ALPHA = 0.8f
    }

    private var registeredSensorType: Int? = null
    private val gravity = FloatArray(3)
    private var gravityInitialized = false
    private var firstPeakAt = 0L
    private var lastPeakAt = 0L
    private var lastPeakAxis = -1
    private var lastPeakDirection = 0
    private var alternatingPeakCount = 0

    fun start(sensorManager: SensorManager): Boolean {
        resetDetection()
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return false
        registeredSensorType = sensor.type
        return sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop(sensorManager: SensorManager) {
        sensorManager.unregisterListener(this)
        registeredSensorType = null
        resetDetection()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != registeredSensorType) return

        val linearAcceleration = if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            event.values
        } else {
            quitarGravedad(event.values)
        }

        val magnitude = sqrt(
            (linearAcceleration[0] * linearAcceleration[0] +
                    linearAcceleration[1] * linearAcceleration[1] +
                    linearAcceleration[2] * linearAcceleration[2]).toDouble()
        ).toFloat()
        if (magnitude < SHAKE_THRESHOLD_MPS2) return

        val axis = ejeDominante(linearAcceleration)
        val direction = if (linearAcceleration[axis] >= 0f) 1 else -1
        registrarPico(SystemClock.elapsedRealtime(), axis, direction)
    }

    /** Usa el sensor lineal cuando existe; en equipos antiguos, estima gravedad con un filtro pasa-bajo. */
    private fun quitarGravedad(values: FloatArray): FloatArray {
        if (!gravityInitialized) {
            for (axis in 0..2) gravity[axis] = values[axis]
            gravityInitialized = true
            return FloatArray(3)
        }

        return FloatArray(3) { axis ->
            gravity[axis] = GRAVITY_FILTER_ALPHA * gravity[axis] +
                    (1f - GRAVITY_FILTER_ALPHA) * values[axis]
            values[axis] - gravity[axis]
        }
    }

    private fun ejeDominante(values: FloatArray): Int {
        var dominantAxis = 0
        for (axis in 1..2) {
            if (kotlin.math.abs(values[axis]) > kotlin.math.abs(values[dominantAxis])) {
                dominantAxis = axis
            }
        }
        return dominantAxis
    }

    private fun registrarPico(now: Long, axis: Int, direction: Int) {
        if (lastPeakAt != 0L && now - lastPeakAt < MIN_PEAK_INTERVAL_MS) return

        val sequenceExpired = lastPeakAt == 0L ||
                now - lastPeakAt > MAX_PEAK_INTERVAL_MS ||
                now - firstPeakAt > MAX_SEQUENCE_DURATION_MS
        if (sequenceExpired) {
            iniciarSecuencia(now, axis, direction)
            return
        }

        if (axis == lastPeakAxis && direction != lastPeakDirection) {
            alternatingPeakCount++
        } else {
            // Un pico en otro eje o en la misma dirección inicia una secuencia nueva.
            iniciarSecuencia(now, axis, direction)
            return
        }

        lastPeakAt = now
        lastPeakAxis = axis
        lastPeakDirection = direction

        if (alternatingPeakCount >= REQUIRED_ALTERNATING_PEAKS) {
            resetSequence()
            onShakeListener()
        }
    }

    private fun iniciarSecuencia(now: Long, axis: Int, direction: Int) {
        firstPeakAt = now
        lastPeakAt = now
        lastPeakAxis = axis
        lastPeakDirection = direction
        alternatingPeakCount = 1
    }

    private fun resetSequence() {
        firstPeakAt = 0L
        lastPeakAt = 0L
        lastPeakAxis = -1
        lastPeakDirection = 0
        alternatingPeakCount = 0
    }

    private fun resetDetection() {
        gravity.fill(0f)
        gravityInitialized = false
        resetSequence()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No requerido
    }
}
