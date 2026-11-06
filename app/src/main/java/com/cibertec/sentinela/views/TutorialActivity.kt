package com.cibertec.sentinela.views

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cibertec.sentinela.R
import com.cibertec.sentinela.databinding.ActivityTutorialBinding
import com.cibertec.sentinela.utils.SessionManager

/** Recorrido breve y repetible por las funciones principales de seguridad. */
class TutorialActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REPLAY = "tutorialSentinelaReplay"
    }

    private data class Paso(val icono: Int, val titulo: String, val descripcion: String, val consejo: String)

    private lateinit var binding: ActivityTutorialBinding
    private lateinit var session: SessionManager
    private var pasoActual = 0

    private val pasos = listOf(
        Paso(
            R.drawable.ic_my_location, "Tu mapa y ubicación",
            "El mapa muestra tu ubicación cuando el GPS y el permiso están disponibles. Puedes centrarlo en tu posición y buscar un destino.",
            "Confirma el nombre y el punto elegido antes de iniciar un trayecto."
        ),
        Paso(
            R.drawable.ic_safe_trip, "Trayecto Seguro",
            "Elige destino y duración para compartir el seguimiento. Sentinela puede avisarte si detecta una desviación o una demora.",
            "Al llegar, pulsa «Llegué» e ingresa tu PIN normal para cerrar el seguimiento."
        ),
        Paso(
            R.drawable.ic_warning, "SOS y alertas",
            "El botón SOS inicia el flujo de emergencia y puede compartir tu ubicación con tus contactos, según los permisos y la conexión disponibles.",
            "Conoce también el acceso a los números de emergencia. No uses este recorrido para probar una alerta real."
        ),
        Paso(
            R.drawable.ic_shield, "Tus PINs de seguridad",
            "El PIN normal confirma acciones como finalizar un trayecto. El PIN de alerta permite activar la respuesta encubierta configurada por la app.",
            "Mantén ambos códigos distintos y memorízalos. Nunca los compartas ni los muestres en pantalla."
        ),
        Paso(
            R.drawable.ic_family, "Contactos y herramientas",
            "Agrega contactos de confianza y abre Familia, Llamada falsa o Sentinela IA desde el inicio. El Historial reúne las alertas; Configuración contiene privacidad, PINs y preferencias.",
            "Revisa permisos y contactos con anticipación. Puedes volver a ver este tutorial desde Configuración."
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTutorialBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)

        mostrarPaso()
        binding.btnTutorialOmitir.setOnClickListener { terminarRecorrido() }
        binding.btnTutorialAnterior.setOnClickListener {
            if (pasoActual > 0) {
                pasoActual--
                mostrarPaso()
            }
        }
        binding.btnTutorialSiguiente.setOnClickListener {
            if (pasoActual < pasos.lastIndex) {
                pasoActual++
                mostrarPaso()
            } else {
                terminarRecorrido()
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (intent.getBooleanExtra(EXTRA_REPLAY, false)) {
            finish()
        } else {
            terminarRecorrido()
        }
    }

    private fun mostrarPaso() {
        val paso = pasos[pasoActual]
        binding.txtTutorialIcono.setImageResource(paso.icono)
        binding.txtTutorialTitulo.text = paso.titulo
        binding.txtTutorialDescripcion.text = paso.descripcion
        binding.txtTutorialConsejo.text = paso.consejo
        binding.txtTutorialProgreso.text = "${pasoActual + 1} de ${pasos.size}"
        binding.txtTutorialIndicador.text = pasos.indices.joinToString("  ") {
            if (it == pasoActual) "●" else "○"
        }
        binding.btnTutorialSiguiente.text = if (pasoActual == pasos.lastIndex) "Ir al inicio" else "Siguiente"
        binding.btnTutorialOmitir.text = if (pasoActual == pasos.lastIndex) "Cerrar" else "Omitir"
        binding.btnTutorialAnterior.visibility = if (pasoActual == 0) android.view.View.INVISIBLE else android.view.View.VISIBLE
    }

    private fun terminarRecorrido() {
        session.tutorialSentinelaVisto = true
        startActivity(
            Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(
                    HomeActivity.EXTRA_TRIGGER_EMERGENCY,
                    intent.getBooleanExtra(HomeActivity.EXTRA_TRIGGER_EMERGENCY, false)
                )
        )
        finish()
    }
}
