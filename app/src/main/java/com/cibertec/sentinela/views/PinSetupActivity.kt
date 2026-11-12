package com.cibertec.sentinela.views

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cibertec.sentinela.databinding.ActivityPinSetupBinding
import com.cibertec.sentinela.models.SavePinsRequest
import com.cibertec.sentinela.services.ApiClient
import com.cibertec.sentinela.services.realizarPeticion
import com.cibertec.sentinela.utils.SessionManager
import kotlinx.coroutines.launch

class PinSetupActivity : AppCompatActivity() {

    private enum class EstadoPin { INFO, LOADING, ERROR }

    private lateinit var binding: ActivityPinSetupBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)

        binding.btnSavePins.setOnClickListener { guardarPins() }
        comprobarConfiguracionActual()
    }

    private fun comprobarConfiguracionActual() {
        val token = session.userToken
        val userId = session.userId
        if (token.isNullOrBlank() || userId == 0) {
            actualizarEstado("Tu sesión expiró. Inicia sesión nuevamente para configurar tus PINs.", EstadoPin.ERROR)
            return
        }

        actualizarEstado("Verificando el estado de seguridad de tu cuenta…", EstadoPin.LOADING)
        lifecycleScope.launch {
            realizarPeticion {
                ApiClient.users.obtenerUsuario(userId, ApiClient.bearer(token))
            }.onSuccess { user ->
                val tieneCacheLocal = session.pinSeguridad.isNotBlank() && session.pinCoaccion.isNotBlank()
                if (user.pinsConfigured && tieneCacheLocal) {
                    session.pinsConfigured = true
                    abrirInicio()
                } else {
                    actualizarEstado(if (user.pinsConfigured) {
                        "Crea dos PINs para este dispositivo. Los PIN anteriores no se pueden recuperar."
                    } else {
                        "Crea tus dos PINs para continuar."
                    }, EstadoPin.INFO)
                    binding.btnSavePins.isEnabled = true
                }
            }.onFailure { error ->
                actualizarEstado(
                    "No pudimos verificar tu cuenta. Revisa tu conexión y vuelve a intentarlo.",
                    EstadoPin.ERROR
                )
                binding.btnSavePins.isEnabled = false
                android.util.Log.w("PinSetupActivity", "No se pudo consultar el estado de PINs", error)
            }
        }
    }

    private fun guardarPins() {
        val normal = binding.inputPinSeguridad.text.toString()
        val coaccion = binding.inputPinCoaccion.text.toString()
        if (!normal.matches(Regex("\\d{4}")) || !coaccion.matches(Regex("\\d{4}"))) {
            actualizarEstado("Cada PIN debe tener exactamente 4 dígitos.", EstadoPin.ERROR)
            return
        }
        if (normal == coaccion) {
            actualizarEstado("El PIN de alerta debe ser distinto del PIN normal.", EstadoPin.ERROR)
            return
        }

        val token = session.userToken ?: return
        val userId = session.userId
        binding.btnSavePins.isEnabled = false
        actualizarEstado("Guardando tus PINs de forma segura…", EstadoPin.LOADING)

        lifecycleScope.launch {
            realizarPeticion {
                ApiClient.users.guardarPins(
                    userId,
                    ApiClient.bearer(token),
                    SavePinsRequest(normal, coaccion)
                )
            }.onSuccess { result ->
                session.pinSeguridad = normal
                session.pinCoaccion = coaccion
                session.pinsConfigured = result.pinsConfigured
                abrirInicio()
            }.onFailure { error ->
                actualizarEstado(
                    "No se pudieron guardar los PINs. Tus cambios no se aplicaron; vuelve a intentarlo.",
                    EstadoPin.ERROR
                )
                binding.btnSavePins.isEnabled = true
                android.util.Log.w("PinSetupActivity", "No se pudieron guardar los PINs", error)
            }
        }
    }

    private fun actualizarEstado(mensaje: String, estado: EstadoPin) {
        binding.txtPinSetupStatus.apply {
            text = mensaje
            visibility = android.view.View.VISIBLE
            when (estado) {
                EstadoPin.INFO -> {
                    setTextColor(getColor(com.cibertec.sentinela.R.color.text_secondary))
                    setBackgroundResource(com.cibertec.sentinela.R.drawable.bg_status_info)
                }
                EstadoPin.LOADING -> {
                    setTextColor(getColor(com.cibertec.sentinela.R.color.text_brand))
                    setBackgroundResource(com.cibertec.sentinela.R.drawable.bg_status_info)
                }
                EstadoPin.ERROR -> {
                    setTextColor(getColor(com.cibertec.sentinela.R.color.text_error))
                    setBackgroundResource(com.cibertec.sentinela.R.drawable.bg_status_error)
                }
            }
        }
        binding.pinSetupProgress.visibility = if (estado == EstadoPin.LOADING) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }

    private fun abrirInicio() {
        val activarEmergencia = intent.getBooleanExtra(HomeActivity.EXTRA_TRIGGER_EMERGENCY, false)
        val destino = if (!session.tutorialSentinelaVisto && !activarEmergencia) {
            TutorialActivity::class.java
        } else {
            HomeActivity::class.java
        }
        startActivity(
            Intent(this, destino)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(
                    HomeActivity.EXTRA_TRIGGER_EMERGENCY,
                    activarEmergencia
                )
        )
        finish()
    }
}
