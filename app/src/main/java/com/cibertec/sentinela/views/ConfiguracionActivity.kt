package com.cibertec.sentinela.views

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.transition.TransitionManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.CompoundButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cibertec.sentinela.databinding.ActivityConfiguracionBinding
import com.cibertec.sentinela.utils.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.function.Consumer

class ConfiguracionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "Configuracion"
    }

    private lateinit var binding: ActivityConfiguracionBinding
    private lateinit var session: SessionManager
    private val handler = Handler(Looper.getMainLooper())

    private var isMonitoringScreen = false
    private var isDropdownOpen = false
    private var isCaptured = false

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

    // Android 15+: el sistema avisa cuando la app está siendo grabada
    private val screenRecordingCallback = Consumer<Int> { estado ->
        isCaptured = estado == WindowManager.SCREEN_RECORDING_STATE_VISIBLE
        updateScreenRecordingStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfiguracionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)

        setupDropdown()
        loadSavedSettings()
        setupSwitches()

        binding.cardPerfil.setOnClickListener { profileTapped() }
    }

    override fun onStart() {
        super.onStart()
        setupScreenCaptureMonitoring()
    }

    override fun onResume() {
        super.onResume()
        updateLocationStatus()
        updateScreenRecordingStatus()
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            windowManager.removeScreenRecordingCallback(screenRecordingCallback)
        }
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // MARK: - Setup Methods

    private fun setupSwitches() {
        binding.locationSwitch.setOnCheckedChangeListener(locationSwitchListener)
        binding.recordingSwitch.setOnCheckedChangeListener { _, isOn ->
            if (isOn) enableScreenRecordingProtection() else disableScreenRecordingProtection()
            saveSettings()
        }
    }

    private fun setupDropdown() {
        binding.dropdownButton.setOnClickListener { toggleDropdown() }
        binding.audioOptionButton.setOnClickListener { selectOption("audio", "🎤 Audio") }
        binding.photoOptionButton.setOnClickListener { selectOption("photo", "📸 Foto") }

        binding.dropdownPanel.visibility = View.GONE
        loadSelectedType()
    }

    // MARK: - Dropdown Actions

    private fun toggleDropdown() {
        isDropdownOpen = !isDropdownOpen

        TransitionManager.beginDelayedTransition(binding.contenedor)
        binding.dropdownPanel.visibility = if (isDropdownOpen) View.VISIBLE else View.GONE
        binding.dropdownChevron.animate().rotation(if (isDropdownOpen) 180f else 0f).setDuration(300).start()
    }

    private fun selectOption(type: String, displayText: String) {
        binding.dropdownTitle.text = displayText

        session.selectedType = type
        session.selectedTypeDisplay = displayText

        toggleDropdown()

        showSuccessMessage("Tipo seleccionado: $displayText")
    }

    private fun loadSelectedType() {
        session.selectedTypeDisplay?.let { binding.dropdownTitle.text = it }
    }

    private fun setupScreenCaptureMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val estado = windowManager.addScreenRecordingCallback(mainExecutor, screenRecordingCallback)
            isCaptured = estado == WindowManager.SCREEN_RECORDING_STATE_VISIBLE
        }
    }

    private fun loadSavedSettings() {
        binding.locationSwitch.isChecked = session.locationEnabled
        binding.recordingSwitch.isChecked = session.recordingEnabled
        isMonitoringScreen = session.recordingEnabled
    }

    private fun saveSettings() {
        session.locationEnabled = binding.locationSwitch.isChecked
        session.recordingEnabled = binding.recordingSwitch.isChecked
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

    // MARK: - Screen Recording Protection Methods

    private fun enableScreenRecordingProtection() {
        isMonitoringScreen = true
        checkCurrentScreenRecordingStatus()
        Log.d(TAG, "Protección contra grabación activada")
        showSuccessMessage("Protección de privacidad activada")
    }

    private fun disableScreenRecordingProtection() {
        isMonitoringScreen = false
        Log.d(TAG, "Protección contra grabación desactivada")
        showWarningMessage("Protección de privacidad desactivada")
    }

    private fun updateScreenRecordingStatus() {
        if (binding.recordingSwitch.isChecked && isCaptured) {
            showScreenRecordingDetectedAlert()
        }
    }

    private fun checkCurrentScreenRecordingStatus() {
        if (isCaptured) {
            showScreenRecordingDetectedAlert()
        }
    }

    private fun showScreenRecordingDetectedAlert() {
        if (isFinishing || isDestroyed) return
        MaterialAlertDialogBuilder(this)
            .setTitle("🔴 Grabación de Pantalla Detectada")
            .setMessage("Se ha detectado que estás grabando o compartiendo tu pantalla. Por tu seguridad, algunas funciones de Sentinela pueden estar limitadas.\n\nDetén la grabación para usar todas las funciones.")
            .setPositiveButton("Entendido", null)
            .setNeutralButton("Cómo detenerla") { _, _ -> showHowToStopRecordingInstructions() }
            .show()
    }

    private fun showHowToStopRecordingInstructions() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Cómo detener la grabación")
            .setMessage(
                """
                Para detener la grabación de pantalla:

                1. Desliza hacia abajo desde la parte superior de la pantalla
                2. Busca la notificación de grabación (ícono rojo)
                3. Toca "Detener" para finalizar la grabación

                También puedes tocar el indicador rojo en la barra de estado.
                """.trimIndent()
            )
            .setPositiveButton("Entendido", null)
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
