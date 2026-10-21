package com.cibertec.sentinela.views

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.cibertec.sentinela.R
import com.cibertec.sentinela.databinding.ActivityFamilyBinding
import com.cibertec.sentinela.databinding.ItemFamilyMemberBinding
import com.cibertec.sentinela.models.CreateFamilyGroupRequest
import com.cibertec.sentinela.models.FamilyGroupDto
import com.cibertec.sentinela.models.FamilyMemberDto
import com.cibertec.sentinela.models.JoinFamilyGroupRequest
import com.cibertec.sentinela.services.ApiClient
import com.cibertec.sentinela.services.realizarPeticion
import com.cibertec.sentinela.utils.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class FamilyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFamilyBinding
    private lateinit var session: SessionManager
    private var grupoActual: FamilyGroupDto? = null
    private val adapter = MiembrosAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFamilyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)

        binding.rvMiembrosFamilia.adapter = adapter

        setupListeners()
        cargarCirculoFamiliar()
    }

    override fun onResume() {
        super.onResume()
        if (grupoActual == null) {
            cargarCirculoFamiliar()
        }
    }

    private fun setupListeners() {
        binding.btnVolverFamilia.setOnClickListener { finish() }

        binding.btnRecargarFamilia.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            it.animate().rotationBy(360f).setDuration(500).start()
            cargarCirculoFamiliar()
        }

        binding.btnCrearGrupo.setOnClickListener {
            if (!session.esFamilia()) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("👑 Función Exclusiva de Sentinela Familia")
                    .setMessage("Crear y administrar un Círculo Familiar para proteger a tus seres queridos y monitorear hasta 5 celulares en tiempo real requiere el Plan Sentinela Familia (S/. 24.90 / mes).\n\n¿Deseas activar el plan ahora para ser el titular de tu grupo?")
                    .setPositiveButton("Ver Planes") { _, _ ->
                        startActivity(Intent(this, SuscripcionActivity::class.java))
                    }
                    .setNegativeButton("Entendido", null)
                    .show()
                return@setOnClickListener
            }

            val nombre = binding.txtNombreGrupo.text?.toString()?.trim()
            crearGrupo(nombre.orEmpty())
        }

        binding.btnUnirmeGrupo.setOnClickListener {
            val codigo = binding.txtCodigoInvitacion.text?.toString()?.trim()?.uppercase()
            if (codigo.isNullOrEmpty()) {
                Toast.makeText(this, "Por favor ingresa un código de invitación", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            unirmeGrupo(codigo)
        }

        binding.btnCopiarCodigo.setOnClickListener {
            val codigo = grupoActual?.groupCode ?: return@setOnClickListener
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Código Sentinela Familia", codigo)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Código $codigo copiado al portapapeles", Toast.LENGTH_SHORT).show()
        }

        binding.btnCompartirWhatsapp.setOnClickListener {
            val codigo = grupoActual?.groupCode ?: return@setOnClickListener
            val nombreGrupo = grupoActual?.groupName ?: "Círculo Familiar"
            val mensaje = """
                🛡️ *Sentinela - Protección Familiar en Vivo*
                
                Te invito a unirte a nuestro Círculo Familiar (*$nombreGrupo*) para ver nuestra ubicación en vivo, nivel de batería y mantenernos seguros.
                
                🔑 *Código de Invitación:* $codigo
                
                Descarga la app Sentinela, selecciona 'Unirme con Código' e ingresa: $codigo
            """.trimIndent()

            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?text=" + Uri.encode(mensaje))
            }
            startActivity(intent)
        }

        binding.btnVerEnMapa.setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }

        binding.btnSalirGrupo.setOnClickListener {
            confirmarSalirGrupo()
        }
    }

    private fun cargarCirculoFamiliar() {
        val userId = session.userId.toLong()
        if (userId == 0L) {
            mostrarEstadoSinGrupo()
            return
        }

        binding.progressBarFamilia.visibility = View.VISIBLE

        lifecycleScope.launch {
            realizarPeticion { ApiClient.family.getMyGroup(userId) }
                .onSuccess { grupo ->
                    binding.progressBarFamilia.visibility = View.GONE
                    mostrarEstadoConGrupo(grupo)
                }
                .onFailure {
                    binding.progressBarFamilia.visibility = View.GONE
                    mostrarEstadoSinGrupo()
                }
        }
    }

    private fun crearGrupo(nombre: String) {
        val userId = session.userId.toLong()
        binding.progressBarFamilia.visibility = View.VISIBLE

        lifecycleScope.launch {
            realizarPeticion {
                ApiClient.family.createGroup(CreateFamilyGroupRequest(userId = userId, groupName = nombre))
            }.onSuccess { grupo ->
                binding.progressBarFamilia.visibility = View.GONE
                Toast.makeText(this@FamilyActivity, "¡Círculo Familiar creado con éxito!", Toast.LENGTH_LONG).show()
                mostrarEstadoConGrupo(grupo)
            }.onFailure { err ->
                binding.progressBarFamilia.visibility = View.GONE
                MaterialAlertDialogBuilder(this@FamilyActivity)
                    .setTitle("Error al crear grupo")
                    .setMessage(err.localizedMessage ?: "No se pudo conectar con el servidor.")
                    .setPositiveButton("Aceptar", null)
                    .show()
            }
        }
    }

    private fun unirmeGrupo(codigo: String) {
        val userId = session.userId.toLong()
        binding.progressBarFamilia.visibility = View.VISIBLE

        lifecycleScope.launch {
            realizarPeticion {
                ApiClient.family.joinGroup(JoinFamilyGroupRequest(userId = userId, groupCode = codigo))
            }.onSuccess { grupo ->
                binding.progressBarFamilia.visibility = View.GONE
                Toast.makeText(this@FamilyActivity, "¡Te has unido al Círculo Familiar!", Toast.LENGTH_LONG).show()
                mostrarEstadoConGrupo(grupo)
            }.onFailure { err ->
                binding.progressBarFamilia.visibility = View.GONE
                MaterialAlertDialogBuilder(this@FamilyActivity)
                    .setTitle("Código Inválido")
                    .setMessage(err.localizedMessage ?: "Verifica que el código sea correcto.")
                    .setPositiveButton("Aceptar", null)
                    .show()
            }
        }
    }

    private fun confirmarSalirGrupo() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Salir del Círculo Familiar")
            .setMessage("¿Estás seguro de que deseas salir de este grupo? Dejarás de compartir tu ubicación y ver a tus familiares.")
            .setPositiveButton("Salir") { _, _ ->
                val userId = session.userId.toLong()
                binding.progressBarFamilia.visibility = View.VISIBLE
                lifecycleScope.launch {
                    realizarPeticion { ApiClient.family.leaveGroup(userId) }
                    binding.progressBarFamilia.visibility = View.GONE
                    grupoActual = null
                    mostrarEstadoSinGrupo()
                    Toast.makeText(this@FamilyActivity, "Has salido del Círculo Familiar", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarEstadoSinGrupo() {
        grupoActual = null
        binding.layoutSinGrupo.visibility = View.VISIBLE
        binding.layoutConGrupo.visibility = View.GONE
    }

    private fun mostrarEstadoConGrupo(grupo: FamilyGroupDto) {
        grupoActual = grupo
        binding.layoutSinGrupo.visibility = View.GONE
        binding.layoutConGrupo.visibility = View.VISIBLE

        binding.lblNombreGrupoActivo.text = grupo.groupName
        binding.lblCodigoGrupo.text = grupo.groupCode
        binding.lblContadorMiembros.text = "Miembros Conectados (${grupo.members.size}/5)"

        adapter.actualizarLista(grupo.members)
    }

    private inner class MiembrosAdapter : RecyclerView.Adapter<MiembrosAdapter.MiembroViewHolder>() {

        private val items = mutableListOf<FamilyMemberDto>()

        fun actualizarLista(nuevos: List<FamilyMemberDto>) {
            items.clear()
            items.addAll(nuevos)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MiembroViewHolder {
            val binding = ItemFamilyMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return MiembroViewHolder(binding)
        }

        override fun onBindViewHolder(holder: MiembroViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class MiembroViewHolder(private val itemBinding: ItemFamilyMemberBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(item: FamilyMemberDto) {
                val nombre = item.fullName ?: "Familiar"
                itemBinding.lblNombreFamiliar.text = nombre
                itemBinding.lblAvatarInicial.text = nombre.firstOrNull()?.uppercase() ?: "F"

                // Badge de titular
                itemBinding.badgeTitular.visibility = if (item.isAdmin == true) View.VISIBLE else View.GONE

                // Batería
                val batLevel = item.batteryLevel ?: 100
                val icon = if (item.isCharging == true) "⚡🔋" else "🔋"
                itemBinding.lblBateriaFamiliar.text = "$icon $batLevel%"
                if (batLevel <= 15) {
                    itemBinding.lblBateriaFamiliar.setTextColor(Color.parseColor("#EF4444"))
                } else {
                    itemBinding.lblBateriaFamiliar.setTextColor(Color.parseColor("#064E3B"))
                }

                // Estado de emergencia o zona
                if (item.isEmergencyActive == true) {
                    itemBinding.lblEstadoFamiliar.text = "🚨 ¡EMERGENCIA SOS ACTIVA!"
                    itemBinding.lblEstadoFamiliar.setTextColor(Color.parseColor("#DC2626"))
                    itemBinding.dotEstadoConexion.background =
                        ContextCompat.getDrawable(itemView.context, R.drawable.bg_circle_red)
                } else {
                    itemBinding.lblEstadoFamiliar.text = item.currentStatus ?: "🟢 En Línea"
                    itemBinding.lblEstadoFamiliar.setTextColor(Color.parseColor("#10B981"))
                    itemBinding.dotEstadoConexion.background =
                        ContextCompat.getDrawable(itemView.context, R.drawable.bg_circle_green)
                }

                itemBinding.lblUltimoReporte.text = "Tel: ${item.phone ?: "No registrado"}"
            }
        }
    }
}
