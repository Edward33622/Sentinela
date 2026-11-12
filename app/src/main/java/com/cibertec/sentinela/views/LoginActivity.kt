package com.cibertec.sentinela.views

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cibertec.sentinela.databinding.ActivityLoginBinding
import com.cibertec.sentinela.models.LoginRequest
import com.cibertec.sentinela.services.ApiClient
import com.cibertec.sentinela.services.realizarPeticion
import com.cibertec.sentinela.utils.SessionManager
import com.cibertec.sentinela.utils.ocultarTeclado
import com.cibertec.sentinela.utils.presentarAlerta
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)

        binding.contenedor.setOnClickListener { ocultarTeclado() }
        binding.contenedor.setOnLongClickListener {
            mostrarDialogoConfigurarIp()
            true
        }

        binding.ingresar.setOnClickListener { ingresarAction() }
        binding.btnRegistrate.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun mostrarDialogoConfigurarIp() {
        val input = android.widget.EditText(this).apply {
            hint = "Ej: 192.168.1.34"
            setText(session.serverIp)
            setSelection(text.length)
            setPadding(48, 32, 48, 32)
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("⚙️ Servidor API")
            .setMessage("Servidor actual: ${com.cibertec.sentinela.utils.Conexion.baseURL}\n\nIngresa la IP de tu PC:")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val ip = input.text.toString().trim()
                if (ip.isNotEmpty()) {
                    session.serverIp = ip
                    ApiClient.actualizarBaseUrl("http://$ip:${com.cibertec.sentinela.utils.Conexion.DEFAULT_PORT}")
                    presentarAlerta("Servidor actualizado:\n${com.cibertec.sentinela.utils.Conexion.baseURL}")
                }
            }
            .setNeutralButton("Restablecer") { _, _ ->
                session.serverIp = com.cibertec.sentinela.utils.Conexion.DEFAULT_PC_IP
                ApiClient.actualizarBaseUrl(com.cibertec.sentinela.utils.Conexion.baseURL)
                presentarAlerta("Restablecido a:\n${com.cibertec.sentinela.utils.Conexion.baseURL}")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun ingresarAction() {
        val email = binding.correoView.text.toString()
        if (email.isEmpty()) {
            presentarAlerta("Introduzca su email")
            return
        }

        val password = binding.contraView.text.toString()
        if (password.isEmpty()) {
            presentarAlerta("Introduzca su contraseña")
            return
        }

        val datosLogin = LoginRequest(email = email, password = password)

        lifecycleScope.launch {
            realizarPeticion { ApiClient.auth.login(datosLogin) }
                .onSuccess { loginResp ->
                    session.userToken = loginResp.token
                    session.userId = loginResp.userId
                    obtenerDatosDeUsuario(id = loginResp.userId, token = loginResp.token)
                }
                .onFailure { error ->
                    val errMsg = error.localizedMessage.orEmpty()
                    if (errMsg.contains("Failed to connect", ignoreCase = true) ||
                        errMsg.contains("timed out", ignoreCase = true) ||
                        errMsg.contains("Unable to resolve host", ignoreCase = true)
                    ) {
                        presentarAlerta("No se pudo conectar al servidor en:\n${com.cibertec.sentinela.utils.Conexion.baseURL}\n\n1. Asegúrate de que tu celular y PC estén en el mismo Wi-Fi.\n2. Mantén presionado el fondo de la pantalla para cambiar o verificar la IP de tu PC.")
                    } else {
                        presentarAlerta("Credenciales incorrectas")
                    }
                }
        }
    }

    private suspend fun obtenerDatosDeUsuario(id: Int, token: String) {
        realizarPeticion { ApiClient.users.obtenerUsuario(id, ApiClient.bearer(token)) }
            .onSuccess { usuarioDetalle ->
                if (usuarioDetalle.status == "ACTIVE") {
                    Log.d("LoginActivity", "usuario: $usuarioDetalle")
                    session.pinsConfigured = usuarioDetalle.pinsConfigured
                    val destino = when {
                        !usuarioDetalle.pinsConfigured -> PinSetupActivity::class.java
                        !session.tutorialSentinelaVisto -> TutorialActivity::class.java
                        else -> HomeActivity::class.java
                    }
                    startActivity(
                        Intent(this, destino)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            .putExtra(HomeActivity.EXTRA_USUARIO, usuarioDetalle)
                    )
                } else {
                    presentarAlerta("Usuario inactivo")
                }
            }
            .onFailure { error ->
                presentarAlerta("Login correcto, pero error al obtener perfil: ${error.localizedMessage}")
            }
    }
}
