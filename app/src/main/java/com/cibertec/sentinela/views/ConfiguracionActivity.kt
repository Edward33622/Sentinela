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
        binding.locationSwitch.setOnCheckedChangeListener(locationSwitchListener)

        binding.cardPerfil.setOnClickListener { profileTapped() }
    }

    override fun onResume() {
        super.onResume()
        updateLocationStatus()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // MARK: - Ajustes guardados

    private fun loadSavedSettings() {
        binding.locationSwitch.isChecked = session.locationEnabled
    }

    private fun saveSettings() {
        session.locationEnabled = binding.locationSwitch.isChecked
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

    /** Alerta que se cierra sola después de unos segundos. */
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

    private fun profileTapped() {
        startActivity(Intent(this, ProfileActivity::class.java))
    }
}
