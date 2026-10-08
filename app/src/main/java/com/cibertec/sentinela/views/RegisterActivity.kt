package com.cibertec.sentinela.views

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cibertec.sentinela.databinding.ActivityRegisterBinding
import com.cibertec.sentinela.models.RegisterRequest
import com.cibertec.sentinela.services.ApiClient
import com.cibertec.sentinela.services.realizarPeticion
import com.cibertec.sentinela.utils.ocultarTeclado
import com.cibertec.sentinela.utils.presentarAlerta
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.contenedor.setOnClickListener { ocultarTeclado() }
        binding.btnRegistrarse.setOnClickListener { crearConfir() }
        binding.btnRegresar.setOnClickListener {
            startActivity(
                Intent(this, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }

    private fun crearConfir() {
        val nombre = binding.nombreView.text.toString()
        val email = binding.correoView.text.toString()
        val telefono = binding.telefonoView.text.toString()
        val password = binding.contraView.text.toString()
        val confirm = binding.contraConfir.text.toString()

        if (nombre.isEmpty() || email.isEmpty() || telefono.isEmpty() ||
            password.isEmpty() || confirm.isEmpty()
        ) {
            presentarAlerta("Complete todos los campos")
            return
        }

        if (password != confirm) {
            presentarAlerta("Las contraseñas no coinciden")
            return
        }

        val nuevoUsuario = RegisterRequest(
            fullName = nombre,
            email = email,
            phone = telefono,
            password = password
        )

        lifecycleScope.launch {
            realizarPeticion { ApiClient.users.registrarUsuario(nuevoUsuario) }
                .onSuccess {
                    presentarAlerta("Cuenta creada correctamente") { finish() }
                }
                .onFailure { error ->
                    presentarAlerta("Error al registrar: ${error.localizedMessage}")
                }
        }
    }
}
