package com.cibertec.sentinela.views

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.CompoundButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cibertec.sentinela.databinding.ActivityConfiguracionBinding
import com.cibertec.sentinela.utils.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ConfiguracionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "Configuracion"
    }

    private lateinit var binding: ActivityConfiguracionBinding
    private lateinit var session: SessionManager
    private val handler = Handler(Looper.getMainLooper())

    private val locationSwitchListener = CompoundButton.OnCheckedChangeListener { _, isOn ->
        if (isOn) enableLocation() else disableLocation()
        saveSettings()
    }

    private val permisoUbicacionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultado ->
            if (resultado.values.any { it }) {
                Log.d(TAG, "Ubicación activada para Sentinela")
                showSuccessMessage("Ubicación activada para tu seguridad")
            } else {
                showLocationPermissionAlert()
                setLocationSwitch(false)
            }
            saveSettings()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfiguracionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)

        loadSavedSettings()
        setupSwitches()

        binding.cardPerfil.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        binding.cardHistorial.setOnClickListener {
            startActivity(Intent(this, EmergencyHistoryActivity::class.java))
        }

        binding.cardSuscripcion.setOnClickListener {
            startActivity(Intent(this, SuscripcionActivity::class.java))
        }

        binding.cardPinReal.setOnClickListener {
            mostrarDialogoCambiarPin(
                titulo = "Modificar PIN de Desactivación",
                mensaje = "Ingresa tu nuevo PIN de 4 dígitos para apagar la alarma:",
                esCoaccion = false
            )
        }

        binding.cardPinCoaccion.setOnClickListener {
            mostrarDialogoCambiarPin(
                titulo = "Modificar PIN de Coacción",
                mensaje = "Ingresa un PIN de 4 dígitos falso que active el rescate silencioso bajo amenaza:",
                esCoaccion = true
            )
        }
    }

    override fun onResume() {
        super.onResume()
        updateLocationStatus()
        actualizarEtiquetasPin()
        actualizarEtiquetaSuscripcion()
    }

    private fun actualizarEtiquetaSuscripcion() {
        val planTexto = when (session.planSuscripcion) {
            "PRO" -> "Plan Actual: Sentinela Pro ⭐ (Ilimitado)"
            "FAMILIA" -> "Plan Actual: Sentinela Familia 👨‍👩‍👧 (Total)"
            else -> "Plan Actual: Sentinela Free (Básico)"
        }
        binding.lblEstadoPlan.text = planTexto
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // MARK: - Setup Switches

    private fun setupSwitches() {
        binding.locationSwitch.setOnCheckedChangeListener(locationSwitchListener)
        binding.discretoSwitch.setOnCheckedChangeListener { _, isOn ->
            session.modoDiscreto = isOn
            val msg = if (isOn) "Modo discreto activado (alarma silenciosa)" else "Modo discreto desactivado"
            showSuccessMessage(msg)
        }
        binding.shakeSwitch.setOnCheckedChangeListener { _, isOn ->
            session.shakeEnabled = isOn
            val msg = if (isOn) "Sensor de sacudida (Shake) activado" else "Sensor de sacudida desactivado"
            showSuccessMessage(msg)
        }
    }

    private fun loadSavedSettings() {
        binding.locationSwitch.isChecked = session.locationEnabled
        binding.discretoSwitch.isChecked = session.modoDiscreto
        binding.shakeSwitch.isChecked = session.shakeEnabled
        actualizarEtiquetasPin()
    }

    private fun actualizarEtiquetasPin() {
        binding.lblPinRealResumen.text = "PIN actual configurado: **** (Seguridad)"
        binding.lblPinCoaccionResumen.text = "PIN de coacción configurado: **** (Rescate encubierto)"
    }

    private fun mostrarDialogoCambiarPin(titulo: String, mensaje: String, esCoaccion: Boolean) {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            maxLines = 1
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
            hint = "4 dígitos"
            setPadding(48, 32, 48, 32)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val nuevoPin = input.text.toString().trim()
                if (nuevoPin.length == 4) {
                    if (esCoaccion) {
                        if (nuevoPin == session.pinSeguridad) {
                            showWarningMessage("El PIN de coacción debe ser diferente al PIN normal")
                            return@setPositiveButton
                        }
                        session.pinCoaccion = nuevoPin
                        showSuccessMessage("PIN de coacción actualizado con éxito")
                    } else {
                        if (nuevoPin == session.pinCoaccion) {
                            showWarningMessage("El PIN normal debe ser diferente al PIN de coacción")
                            return@setPositiveButton
                        }
                        session.pinSeguridad = nuevoPin
                        showSuccessMessage("PIN de desactivación actualizado")
                    }
                    actualizarEtiquetasPin()
                } else {
                    showWarningMessage("El PIN debe tener exactamente 4 dígitos")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun saveSettings() {
        session.locationEnabled = binding.locationSwitch.isChecked
        session.modoDiscreto = binding.discretoSwitch.isChecked
        session.shakeEnabled = binding.shakeSwitch.isChecked
    }

    /** Cambia el switch sin disparar su listener. */
    private fun setLocationSwitch(isOn: Boolean) {
        binding.locationSwitch.setOnCheckedChangeListener(null)
        binding.locationSwitch.isChecked = isOn
        binding.locationSwitch.setOnCheckedChangeListener(locationSwitchListener)
    }

    // MARK: - Location Methods

    private fun tienePermisoUbicacion(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun enableLocation() {
        if (tienePermisoUbicacion()) {
            Log.d(TAG, "Ubicación activada para Sentinela")
            showSuccessMessage("Ubicación activada para tu seguridad")
        } else {
            permisoUbicacionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun disableLocation() {
        Log.d(TAG, "Ubicación desactivada en Sentinela")
        showWarningMessage("Ubicación desactivada - Funciones de seguridad limitadas")
    }

    private fun updateLocationStatus() {
        if (!tienePermisoUbicacion() && binding.locationSwitch.isChecked) {
            setLocationSwitch(false)
            saveSettings()
        }
    }

    private fun showLocationPermissionAlert() {
        MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ Permiso de Ubicación Requerido")
            .setMessage("Sentinela necesita acceso a tu ubicación para funciones de seguridad como alertas de emergencia y rastreo. Ve a Configuración para habilitarlo.")
            .setPositiveButton("Ir a Configuración") { _, _ ->
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null)
                    )
                )
            }
            .setNegativeButton("Cancelar") { _, _ ->
                setLocationSwitch(false)
                saveSettings()
            }
            .show()
    }

    // MARK: - Helper Methods

    private fun showSuccessMessage(message: String) = mostrarMensajeTemporal("✅ Éxito", message, 1500)

    private fun showWarningMessage(message: String) = mostrarMensajeTemporal("⚠️ Advertencia", message, 2000)

    private fun mostrarMensajeTemporal(titulo: String, message: String, duracionMs: Long) {
        if (isFinishing || isDestroyed) return
        val alert = MaterialAlertDialogBuilder(this)
            .setTitle(titulo)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()

        handler.postDelayed({
            if (alert.isShowing && !isFinishing && !isDestroyed) alert.dismiss()
        }, duracionMs)
    }
}
