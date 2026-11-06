package com.cibertec.sentinela.views

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cibertec.sentinela.databinding.ActivityRegisterBinding
import com.cibertec.sentinela.models.RegisterRequest
import com.cibertec.sentinela.services.ApiClient
import com.cibertec.sentinela.services.realizarPeticion
import com.cibertec.sentinela.utils.Conexion
import com.cibertec.sentinela.utils.SessionManager
import com.cibertec.sentinela.utils.SentinelaNotice
import com.cibertec.sentinela.utils.ocultarTeclado
import com.cibertec.sentinela.utils.presentarAlerta
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private var progressDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.contenedor.setOnClickListener { ocultarTeclado() }
        binding.contenedor.setOnLongClickListener {
            mostrarDialogoConfigurarIp()
            true
        }
        binding.btnRegistrarse.setOnClickListener { validarFormularioYRegistrar() }
        binding.btnRegresar.setOnClickListener {
            startActivity(
                Intent(this, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }

    private fun mostrarDialogoConfigurarIp() {
        val session = SessionManager(this)
        val input = EditText(this).apply {
            hint = "Ej: 192.168.1.34"
            setText(session.serverIp)
            setSelection(text.length)
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("⚙️ Servidor API")
            .setMessage("Servidor actual: ${Conexion.baseURL}\n\nIngresa la IP de tu PC:")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val ip = input.text.toString().trim()
                if (ip.isNotEmpty()) {
                    session.serverIp = ip
                    ApiClient.actualizarBaseUrl("http://$ip:${Conexion.DEFAULT_PORT}")
                    presentarAlerta("Servidor actualizado:\n${Conexion.baseURL}")
                }
            }
            .setNeutralButton("Restablecer") { _, _ ->
                session.serverIp = Conexion.DEFAULT_PC_IP
                ApiClient.actualizarBaseUrl(Conexion.baseURL)
                presentarAlerta("Restablecido a:\n${Conexion.baseURL}")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun formatearTelefonoInternacional(telefono: String): String {
        val limpio = telefono.replace("[^0-9+]".toRegex(), "")
        return when {
            limpio.startsWith("+") -> limpio
            limpio.length == 9 -> "+51$limpio" // Formato estándar de Perú (+51)
            else -> "+51$limpio"
        }
    }

    private fun validarFormularioYRegistrar() {
        ocultarTeclado()

        val nombre = binding.nombreView.text.toString().trim()
        val email = binding.correoView.text.toString().trim()
        val telefonoCrudo = binding.telefonoView.text.toString().trim()
        val password = binding.contraView.text.toString()
        val confirm = binding.contraConfir.text.toString()

        if (nombre.isEmpty() || email.isEmpty() || telefonoCrudo.isEmpty() ||
            password.isEmpty() || confirm.isEmpty()
        ) {
            presentarAlerta("Complete todos los campos")
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            presentarAlerta("Ingrese un correo electrónico válido")
            return
        }

        val soloNumeros = telefonoCrudo.replace("[^0-9]".toRegex(), "")
        if (soloNumeros.length < 9) {
            presentarAlerta("Ingrese un número de teléfono válido (mínimo 9 dígitos)")
            return
        }

        if (password.length < 6) {
            presentarAlerta("La contraseña debe tener al menos 6 caracteres")
            return
        }

        if (password != confirm) {
            presentarAlerta("Las contraseñas no coinciden")
            return
        }

        val telefonoInternacional = formatearTelefonoInternacional(telefonoCrudo)

        val nuevoUsuario = RegisterRequest(
            fullName = nombre,
            email = email,
            phone = telefonoInternacional,
            password = password
        )

        completarRegistro(nuevoUsuario)
    }

    private fun completarRegistro(nuevoUsuario: RegisterRequest) {
        mostrarCargando("Creando cuenta en Sentinela...")
        lifecycleScope.launch {
            realizarPeticion { ApiClient.users.registrarUsuario(nuevoUsuario) }
                .onSuccess {
                    ocultarCargando()
                    SentinelaNotice.show(
                        activity = this@RegisterActivity,
                        title = "¡Cuenta creada!",
                        message = "Registramos ${nuevoUsuario.email}. Ya puedes iniciar sesión y continuar con la configuración de seguridad.",
                        type = SentinelaNotice.Type.SUCCESS,
                        primaryText = "Ir al inicio de sesión",
                        onPrimary = { finish() }
                    )
                }
                .onFailure { error ->
                    ocultarCargando()
                    val errMsg = error.message.orEmpty()
                    if (errMsg.contains("correo", ignoreCase = true) || errMsg.contains("registrado", ignoreCase = true)) {
                        SentinelaNotice.show(
                            activity = this@RegisterActivity,
                            title = "Correo ya registrado",
                            message = "${nuevoUsuario.email} ya tiene una cuenta. Inicia sesión o utiliza otro correo.",
                            type = SentinelaNotice.Type.WARNING,
                            primaryText = "Entendido",
                            onPrimary = {},
                            secondaryText = "Ir a iniciar sesión",
                            onSecondary = { finish() }
                        )
                    } else if (errMsg.contains("Failed to connect", ignoreCase = true) ||
                        errMsg.contains("timed out", ignoreCase = true) ||
                        errMsg.contains("Unable to resolve host", ignoreCase = true)
                    ) {
                        presentarAlerta("No se pudo conectar al servidor en la nube de Sentinela.\nVerifica tu conexión a internet.")
                    } else {
                        presentarAlerta("Error al registrar: $errMsg")
                    }
                }
        }
    }

    private fun mostrarCargando(mensaje: String) {
        ocultarCargando()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(60, 40, 60, 40)
            gravity = Gravity.CENTER_VERTICAL
            addView(ProgressBar(this@RegisterActivity))
            addView(TextView(this@RegisterActivity).apply {
                text = mensaje
                setPadding(40, 0, 0, 0)
                textSize = 15f
            })
        }
        progressDialog = MaterialAlertDialogBuilder(this)
            .setView(layout)
            .setCancelable(false)
            .show()
    }

    private fun ocultarCargando() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    override fun onDestroy() {
        ocultarCargando()
        super.onDestroy()
    }
}
