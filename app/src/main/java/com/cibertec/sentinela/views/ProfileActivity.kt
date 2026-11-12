package com.cibertec.sentinela.views

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cibertec.sentinela.R
import com.cibertec.sentinela.databinding.ActivityProfileBinding
import com.cibertec.sentinela.models.UserDetail
import com.cibertec.sentinela.services.ApiClient
import com.cibertec.sentinela.services.realizarPeticion
import com.cibertec.sentinela.utils.FotoPerfil
import com.cibertec.sentinela.utils.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Patterns
import com.cibertec.sentinela.models.UpdateUserRequest
import com.cibertec.sentinela.utils.ocultarTeclado
import com.cibertec.sentinela.utils.presentarAlerta

class ProfileActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USUARIO = "usuario"
    }

    private lateinit var binding: ActivityProfileBinding
    private lateinit var session: SessionManager

    var usuario: UserDetail? = null

    // Solo galería (selector de fotos del sistema)
    private val pickerLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) imagenSeleccionada(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)

        @Suppress("DEPRECATION")
        usuario = intent.getSerializableExtra(EXTRA_USUARIO) as? UserDetail
        usuario?.let {
            binding.nameTextField.setText(it.fullName)
            binding.emailTextField.setText(it.email)
            binding.phoneTextField.setText(it.phone.orEmpty())
        }

        // Tap en la foto o en el ícono de cámara para cambiarla
        binding.profileImageView.setOnClickListener { opcionesFoto() }
        binding.btnCambiarFoto.setOnClickListener { opcionesFoto() }

        // Cargar la foto guardada de esta cuenta, si existe
        FotoPerfil.cargar(this, session.userId)?.let { binding.profileImageView.setImageBitmap(it) }

        binding.btnGuardarPerfil.setOnClickListener { guardarPerfil() }
        binding.btnVerHistorial.setOnClickListener {
            startActivity(Intent(this, EmergencyHistoryActivity::class.java))
        }
        binding.btnCerrar.setOnClickListener { closeSessionButtonTapped() }

        cargarUsuario()
    }

    private fun closeSessionButtonTapped() {
        val alerta = MaterialAlertDialogBuilder(this)
            .setTitle("Cerrar sesión")
            .setMessage("¿Desea salir de su cuenta?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salir") { _, _ ->
                // Detener servicios de emergencia y sacudida
                com.cibertec.sentinela.emergency.ShakeService.detener(this)
                com.cibertec.sentinela.emergency.EmergencyService.detener(this)

                // Limpiar datos de usuario
                session.cerrarSesion()

                // Volver al Login como pantalla raíz
                startActivity(
                    Intent(this, LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
            }
            .show()
        alerta.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(ContextCompat.getColor(this, R.color.system_red))
    }

    // MARK: - Imagen de perfil

    private fun opcionesFoto() {
        if (FotoPerfil.cargar(this, session.userId) == null) {
            changeProfileImage()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Foto de perfil")
            .setItems(arrayOf("Elegir otra foto de la galería", "Quitar foto")) { _, opcion ->
                if (opcion == 0) {
                    changeProfileImage()
                } else {
                    FotoPerfil.borrar(this, session.userId)
                    binding.profileImageView.setImageResource(R.drawable.profile)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun changeProfileImage() {
        pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun imagenSeleccionada(uri: Uri) {
        lifecycleScope.launch {
            val userId = session.userId
            val imagen = withContext(Dispatchers.IO) {
                FotoPerfil.decodificar(this@ProfileActivity, uri)?.takeIf { FotoPerfil.guardar(this@ProfileActivity, userId, it) }
            }
            if (imagen != null) {
                binding.profileImageView.setImageBitmap(imagen)
            } else {
                presentarAlerta("No se pudo guardar la foto. Intenta con otra imagen.")
            }
        }
    }

    // MARK: - Datos del usuario

    private fun cargarUsuario() {
        val token = session.userToken
        val userId = session.userId
        if (token == null || userId == 0) return

        lifecycleScope.launch {
            realizarPeticion { ApiClient.users.obtenerUsuario(userId, ApiClient.bearer(token)) }
                .onSuccess { detalle ->
                    usuario = detalle
                    binding.nameTextField.setText(detalle.fullName)
                    binding.emailTextField.setText(detalle.email)
                    binding.phoneTextField.setText(detalle.phone.orEmpty())
                }
        }
    }

    private fun guardarPerfil() {
        ocultarTeclado()

        val token = session.userToken
        val userId = session.userId
        if (token == null || userId == 0) {
            presentarAlerta("No hay sesión activa")
            return
        }

        val nombre = binding.nameTextField.text.toString().trim()
        val email = binding.emailTextField.text.toString().trim()
        val telefono = binding.phoneTextField.text.toString().trim()

        if (nombre.isEmpty()) {
            presentarAlerta("El nombre no puede estar vacío")
            return
        }

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            presentarAlerta("Ingrese un correo electrónico válido")
            return
        }

        if (telefono.isEmpty()) {
            presentarAlerta("El número de teléfono es obligatorio")
            return
        }

        val request = UpdateUserRequest(
            fullName = nombre,
            email = email,
            phone = telefono
        )

        lifecycleScope.launch {
            realizarPeticion {
                ApiClient.users.actualizarUsuario(userId, ApiClient.bearer(token), request)
            }.onSuccess { detalleActualizado ->
                usuario = detalleActualizado
                binding.nameTextField.setText(detalleActualizado.fullName)
                binding.emailTextField.setText(detalleActualizado.email)
                binding.phoneTextField.setText(detalleActualizado.phone.orEmpty())
                presentarAlerta("Perfil actualizado correctamente")
            }.onFailure { error ->
                presentarAlerta("Error al actualizar el perfil: ${error.localizedMessage}")
            }
        }
    }
}
