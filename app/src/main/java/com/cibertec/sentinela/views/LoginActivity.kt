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

        // Tocar fuera de los campos oculta el teclado
        binding.contenedor.setOnClickListener { ocultarTeclado() }

        binding.ingresar.setOnClickListener { ingresarAction() }
        binding.btnRegistrate.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
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
                .onFailure { presentarAlerta("Credenciales incorrectas") }
        }
    }

    private suspend fun obtenerDatosDeUsuario(id: Int, token: String) {
        realizarPeticion { ApiClient.users.obtenerUsuario(id, ApiClient.bearer(token)) }
            .onSuccess { usuarioDetalle ->
                if (usuarioDetalle.status == "ACTIVE") {
                    Log.d("LoginActivity", "usuario: $usuarioDetalle")
                    startActivity(
                        Intent(this, HomeActivity::class.java)
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
