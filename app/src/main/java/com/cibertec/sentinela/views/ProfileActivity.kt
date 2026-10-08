package com.cibertec.sentinela.views

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.cibertec.sentinela.utils.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
        }

        // Tap para cambiar imagen de perfil
        binding.profileImageView.setOnClickListener { changeProfileImage() }

        // Cargar imagen guardada localmente si existe
        loadProfileImage()?.let { binding.profileImageView.setImageBitmap(it) }

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

    private fun changeProfileImage() {
        pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun imagenSeleccionada(uri: Uri) {
        lifecycleScope.launch {
            val imagen = withContext(Dispatchers.IO) {
                decodificarImagen(uri)?.also { saveProfileImage(it) }
            }
            if (imagen != null) binding.profileImageView.setImageBitmap(imagen)
        }
    }

    /** Lee la imagen reduciéndola para no cargar fotos enormes en memoria. */
    private fun decodificarImagen(uri: Uri): Bitmap? {
        return try {
            val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }

            var muestra = 1
            while (limites.outWidth / muestra > 1024 || limites.outHeight / muestra > 1024) muestra *= 2

            val opciones = BitmapFactory.Options().apply { inSampleSize = muestra }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opciones) }
        } catch (e: Exception) {
            null
        }
    }

    private fun saveProfileImage(image: Bitmap): Boolean {
        return try {
            File(filesDir, "profile.jpg").outputStream().use {
                image.compress(Bitmap.CompressFormat.JPEG, 90, it)
            }
            session.profileImageFilename = "profile.jpg"
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun loadProfileImage(): Bitmap? {
        val archivo = File(filesDir, session.profileImageFilename)
        return if (archivo.exists()) BitmapFactory.decodeFile(archivo.path) else null
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
                }
        }
    }
}
