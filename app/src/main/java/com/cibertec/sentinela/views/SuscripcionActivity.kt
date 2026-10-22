package com.cibertec.sentinela.views

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cibertec.sentinela.R
import com.cibertec.sentinela.databinding.ActivitySuscripcionBinding
import com.cibertec.sentinela.models.SubscribeRequest
import com.cibertec.sentinela.services.ApiClient
import com.cibertec.sentinela.services.realizarPeticion
import com.cibertec.sentinela.utils.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class SuscripcionActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySuscripcionBinding
    private lateinit var session: SessionManager
    private var esCicloAnual = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySuscripcionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        setupListeners()
        actualizarPrecios()
        actualizarBotonesSegunPlan()
        sincronizarConServidor()
    }

    private fun setupListeners() {
        binding.btnVolver.setOnClickListener { finish() }

        binding.toggleCiclo.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                esCicloAnual = (checkedId == R.id.btnAnual)
                actualizarPrecios()
            }
        }

        binding.btnAccionFree.setOnClickListener {
            if (session.planSuscripcion != "FREE") {
                confirmarBajarAPlanFree()
            }
        }

        binding.btnAccionPro.setOnClickListener {
            if (session.planSuscripcion != "PRO") {
                mostrarCheckoutPlan("PRO", "Sentinela Pro", if (esCicloAnual) "S/. 95.00 / año" else "S/. 9.90 / mes")
            }
        }

        binding.btnAccionFamilia.setOnClickListener {
            if (session.planSuscripcion != "FAMILIA") {
                mostrarCheckoutPlan("FAMILIA", "Sentinela Familia", if (esCicloAnual) "S/. 239.00 / año" else "S/. 24.90 / mes")
            }
        }
    }

    private fun actualizarPrecios() {
        if (esCicloAnual) {
            binding.lblPrecioPro.text = "S/. 95.00 / año"
            binding.lblSubtituloPro.text = "Equivale a S/. 7.90/mes • Ahorras S/. 23.80"

            binding.lblPrecioFamilia.text = "S/. 239.00 / año"
            binding.lblSubtituloFamilia.text = "Equivale a S/. 19.90/mes • Ahorras S/. 59.80"
        } else {
            binding.lblPrecioPro.text = "S/. 9.90 / mes"
            binding.lblSubtituloPro.text = "Facturado mensualmente • Cancela cuando quieras"

            binding.lblPrecioFamilia.text = "S/. 24.90 / mes"
            binding.lblSubtituloFamilia.text = "Para hasta 5 miembros familiares"
        }
    }

    private fun actualizarBotonesSegunPlan() {
        val plan = session.planSuscripcion

        when (plan) {
            "PRO" -> {
                binding.btnAccionFree.apply {
                    text = "Bajar a Plan Free"
                    isEnabled = true
                }
                binding.btnAccionPro.apply {
                    text = "✓ Plan Actual (Activo)"
                    isEnabled = false
                }
                binding.btnAccionFamilia.apply {
                    text = "Mejorar a Plan Familiar"
                    isEnabled = true
                }
            }
            "FAMILIA" -> {
                binding.btnAccionFree.apply {
                    text = "Bajar a Plan Free"
                    isEnabled = true
                }
                binding.btnAccionPro.apply {
                    text = "Cambiar a Plan Pro"
                    isEnabled = true
                }
                binding.btnAccionFamilia.apply {
                    text = "✓ Plan Actual (Activo)"
                    isEnabled = false
                }
            }
            else -> { // FREE
                binding.btnAccionFree.apply {
                    text = "✓ Plan Actual (Activo)"
                    isEnabled = false
                }
                binding.btnAccionPro.apply {
                    text = "Suscribirme a Sentinela Pro"
                    isEnabled = true
                }
                binding.btnAccionFamilia.apply {
                    text = "Suscribirme a Sentinela Familia"
                    isEnabled = true
                }
            }
        }
    }

    private fun sincronizarConServidor() {
        val userId = session.userId
        if (userId == 0) return

        lifecycleScope.launch {
            realizarPeticion { ApiClient.subscriptions.obtenerEstadoSuscripcion(userId) }
                .onSuccess { estado ->
                    if (!estado.planId.isNullOrBlank()) {
                        session.actualizarPlan(estado.planId)
                        actualizarBotonesSegunPlan()
                    }
                }
        }
    }

    private fun mostrarCheckoutPlan(planId: String, planNombre: String, precioTexto: String) {
        val contenedor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 24, 50, 10)
        }

        val tvResumen = TextView(this).apply {
            text = "Estás a un paso de activar tu suscripción con cobertura inmediata:\n\n" +
                    "• Plan: $planNombre\n" +
                    "• Costo: $precioTexto\n" +
                    "• Facturación: ${if (esCicloAnual) "Anual (con descuento)" else "Mensual"}\n\n" +
                    "Selecciona tu método de pago preferido:"
            textSize = 14f
            setTextColor(getColor(R.color.text_primary))
        }
        contenedor.addView(tvResumen)

        val spinnerMetodos = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SuscripcionActivity,
                android.R.layout.simple_spinner_dropdown_item,
                arrayOf(
                    "💳 Google Play Billing (Tarjetas / Saldo)",
                    "📱 Yape / Plin (QR instantáneo)",
                    "💳 Tarjeta Débito / Crédito (Visa / Mastercard)"
                )
            )
        }
        contenedor.addView(spinnerMetodos)

        MaterialAlertDialogBuilder(this)
            .setTitle("Confirmar Suscripción")
            .setView(contenedor)
            .setPositiveButton("Confirmar y Activar") { _, _ ->
                ejecutarSuscripcion(planId, planNombre, spinnerMetodos.selectedItem.toString())
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun ejecutarSuscripcion(planId: String, planNombre: String, metodo: String) {
        val ciclo = if (esCicloAnual) "ANNUAL" else "MONTHLY"
        val userId = session.userId.toLong().takeIf { it != 0L } ?: 1L

        val req = SubscribeRequest(
            userId = userId,
            planId = planId,
            billingCycle = ciclo,
            paymentMethod = metodo
        )

        lifecycleScope.launch {
            val resultado = realizarPeticion { ApiClient.subscriptions.suscribirse(req) }

            // Guardar localmente inmediatamente para asegurar persistencia
            session.actualizarPlan(planId)
            actualizarBotonesSegunPlan()

            val mensajeExito = "🎉 ¡Felicidades! Tu cuenta ahora cuenta con todos los beneficios de $planNombre.\n\n" +
                    "• Inteligencia artificial Gemini habilitada\n" +
                    "• Contactos y alertas sin límites\n" +
                    "• Facturación configurada mediante: $metodo"

            MaterialAlertDialogBuilder(this@SuscripcionActivity)
                .setTitle("¡Suscripción Activada con Éxito!")
                .setMessage(mensajeExito)
                .setPositiveButton("Comenzar a Disfrutar", null)
                .show()
        }
    }

    private fun confirmarBajarAPlanFree() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Bajar a Plan Free")
            .setMessage("¿Estás seguro de que deseas volver al Plan Free? Se limitarán los contactos de auxilio a 2 y las consultas de IA a 4 por día.")
            .setPositiveButton("Confirmar cambio") { _, _ ->
                val userId = session.userId.toLong().takeIf { it != 0L } ?: 1L
                val req = SubscribeRequest(
                    userId = userId,
                    planId = "FREE",
                    billingCycle = "NONE",
                    paymentMethod = "NONE"
                )
                lifecycleScope.launch {
                    realizarPeticion { ApiClient.subscriptions.suscribirse(req) }
                    session.actualizarPlan("FREE")
                    actualizarBotonesSegunPlan()
                    Toast.makeText(this@SuscripcionActivity, "Has cambiado a Sentinela Free", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
