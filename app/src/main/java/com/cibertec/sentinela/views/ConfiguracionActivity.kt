package com.cibertec.sentinela.views

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.CompoundButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import android.widget.LinearLayout
import com.cibertec.sentinela.databinding.ActivityConfiguracionBinding
import com.cibertec.sentinela.models.SavePinsRequest
import com.cibertec.sentinela.services.ApiClient
import com.cibertec.sentinela.services.realizarPeticion
import com.cibertec.sentinela.utils.SessionManager
import com.cibertec.sentinela.utils.SentinelaToast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class ConfiguracionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "Configuracion"
    }

    private lateinit var binding: ActivityConfiguracionBinding
    private lateinit var session: SessionManager

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

        binding.cardTutorialSentinela.setOnClickListener {
            startActivity(Intent(this, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_REPLAY, true))
        }

        binding.cardPinReal.setOnClickListener {
            iniciarCambioPinConBiometria(esCoaccion = false)
        }

        binding.cardPinCoaccion.setOnClickListener {
            iniciarCambioPinConBiometria(esCoaccion = true)
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

    // MARK: - Setup Switches

    private fun setupSwitches() {
        // Modo oscuro: se marca según el tema que se ve ahora y, al cambiarlo, se aplica a toda la app
        binding.themeSwitch.isChecked = modoOscuroActivo()
        binding.themeSwitch.setOnCheckedChangeListener { _, oscuro ->
            val modo = if (oscuro) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            session.themeMode = modo
            AppCompatDelegate.setDefaultNightMode(modo)
        }
        binding.locationSwitch.setOnCheckedChangeListener(locationSwitchListener)
        binding.discretoSwitch.setOnCheckedChangeListener { _, isOn ->
            session.modoDiscreto = isOn
            val msg = if (isOn) "Modo discreto activado (alarma silenciosa)" else "Modo discreto desactivado"
            showSuccessMessage(msg)
        }
        binding.shakeSwitch.setOnCheckedChangeListener { _, isOn ->
            session.shakeEnabled = isOn
            if (isOn) {
                com.cibertec.sentinela.emergency.ShakeService.iniciar(this)
            } else {
                com.cibertec.sentinela.emergency.ShakeService.detener(this)
            }
            val msg = if (isOn) "Sensor de sacudida (Shake) activado" else "Sensor de sacudida desactivado"
            showSuccessMessage(msg)
        }
    }

    private fun modoOscuroActivo(): Boolean {
        val modo = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return modo == Configuration.UI_MODE_NIGHT_YES
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

    private fun iniciarCambioPinConBiometria(esCoaccion: Boolean) {
        val biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

        val canAuth = biometricManager.canAuthenticate(authenticators)

        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(this)
            val biometricPrompt = BiometricPrompt(
                this,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        mostrarDialogoNuevoPin(esCoaccion)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                            errorCode != BiometricPrompt.ERROR_CANCELED
                        ) {
                            mostrarDialogoValidarPinActual(esCoaccion)
                        }
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Verifica tu Identidad")
                .setSubtitle(
                    if (esCoaccion) "Autentícate para modificar el PIN de Coacción"
                    else "Autentícate para modificar el PIN de Desactivación"
                )
                .setDescription("Sentinela requiere autenticación para cambiar tus claves de seguridad.")
                .setAllowedAuthenticators(authenticators)
                .build()

            biometricPrompt.authenticate(promptInfo)
        } else {
            // Si el dispositivo no tiene huella o no tiene bloqueo configurado
            mostrarDialogoValidarPinActual(esCoaccion)
        }
    }

    private fun mostrarDialogoValidarPinActual(esCoaccion: Boolean) {
        val pinActualGuardado = if (esCoaccion) session.pinCoaccion else session.pinSeguridad
        val nombrePin = if (esCoaccion) "PIN de Coacción" else "PIN de Desactivación"

        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            maxLines = 1
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
            hint = "PIN actual de 4 dígitos"
            setPadding(48, 32, 48, 32)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Confirmar $nombrePin Actual")
            .setMessage("Por seguridad, ingresa tu $nombrePin actual para poder cambiarlo:")
            .setView(input)
            .setPositiveButton("Continuar") { _, _ ->
                val pinIngresado = input.text.toString().trim()
                if (pinIngresado == pinActualGuardado) {
                    mostrarDialogoNuevoPin(esCoaccion)
                } else {
                    showWarningMessage("El PIN ingresado es incorrecto")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDialogoNuevoPin(esCoaccion: Boolean) {
        val titulo = if (esCoaccion) "Modificar PIN de Coacción" else "Modificar PIN de Desactivación"
        val desc = if (esCoaccion)
            "Ingresa tu nuevo PIN falso de 4 dígitos para rescate silencioso bajo amenaza:"
        else
            "Ingresa tu nuevo PIN de 4 dígitos para apagar la alarma:"

        val contenedor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
        }

        val inputNuevo = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            maxLines = 1
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
            hint = "Nuevo PIN (4 dígitos)"
            setPadding(24, 32, 24, 32)
        }

        val inputConfirm = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            maxLines = 1
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
            hint = "Confirmar Nuevo PIN (4 dígitos)"
            setPadding(24, 32, 24, 32)
        }

        contenedor.addView(inputNuevo)
        contenedor.addView(inputConfirm)

        MaterialAlertDialogBuilder(this)
            .setTitle(titulo)
            .setMessage(desc)
            .setView(contenedor)
            .setPositiveButton("Guardar") { _, _ ->
                val pin1 = inputNuevo.text.toString().trim()
                val pin2 = inputConfirm.text.toString().trim()

                if (pin1.length != 4 || pin2.length != 4) {
                    showWarningMessage("Ambos campos deben tener exactamente 4 dígitos")
                    return@setPositiveButton
                }

                if (pin1 != pin2) {
                    showWarningMessage("Los PINs no coinciden. Inténtalo de nuevo.")
                    return@setPositiveButton
                }

                if (esCoaccion) {
                    if (pin1 == session.pinSeguridad) {
                        showWarningMessage("El PIN de coacción debe ser diferente al PIN normal de seguridad")
                        return@setPositiveButton
                    }
                    guardarPinsEnServidor(session.pinSeguridad, pin1)
                } else {
                    if (pin1 == session.pinCoaccion) {
                        showWarningMessage("El PIN de desactivación debe ser diferente al PIN de coacción")
                        return@setPositiveButton
                    }
                    guardarPinsEnServidor(pin1, session.pinCoaccion)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun guardarPinsEnServidor(pinSeguridad: String, pinCoaccion: String) {
        val token = session.userToken
        if (token.isNullOrBlank() || session.userId == 0) {
            showWarningMessage("Tu sesión expiró. Inicia sesión nuevamente para actualizar los PINs.")
            return
        }

        lifecycleScope.launch {
            realizarPeticion {
                ApiClient.users.guardarPins(
                    session.userId,
                    ApiClient.bearer(token),
                    SavePinsRequest(pinSeguridad, pinCoaccion)
                )
            }.onSuccess { response ->
                session.pinSeguridad = pinSeguridad
                session.pinCoaccion = pinCoaccion
                session.pinsConfigured = response.pinsConfigured
                actualizarEtiquetasPin()
                showSuccessMessage("PINs actualizados y guardados de forma segura")
            }.onFailure { error ->
                showWarningMessage("No se guardaron los PINs. Revisa tu conexión e inténtalo de nuevo.")
                Log.w(TAG, "No se pudieron actualizar los PINs en el servidor", error)
            }
        }
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

    private fun showSuccessMessage(message: String) =
        SentinelaToast.show(this, message, SentinelaToast.Type.SUCCESS)

    private fun showWarningMessage(message: String) =
        SentinelaToast.show(this, message, SentinelaToast.Type.WARNING, SentinelaToast.Length.LONG)
}
