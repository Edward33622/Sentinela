package com.cibertec.sentinela.views

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.cibertec.sentinela.databinding.ActivityEmergencyHistoryBinding
import com.cibertec.sentinela.databinding.ItemHistorialBinding
import com.cibertec.sentinela.models.EmergencyEventSummary
import com.cibertec.sentinela.services.ApiClient
import com.cibertec.sentinela.services.realizarPeticion
import com.cibertec.sentinela.utils.SessionManager
import com.cibertec.sentinela.utils.formatearFecha
import kotlinx.coroutines.launch

class EmergencyHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmergencyHistoryBinding
    private var eventos: List<EmergencyEventSummary> = emptyList()
    private val adapter = EventosAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmergencyHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.historyTableView.adapter = adapter
        binding.historyTableView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        cargarEventos()
    }

    private fun cargarEventos() {
        val session = SessionManager(this)
        val token = session.userToken
        val userId = session.userId
        if (token == null || userId == 0) return

        lifecycleScope.launch {
            realizarPeticion {
                ApiClient.emergencyEvents.obtenerEventosPorUsuarioResumen(userId, ApiClient.bearer(token))
            }.onSuccess { lista ->
                eventos = lista
                adapter.notifyDataSetChanged()
            }.onFailure { error ->
                Log.e("EmergencyHistory", "Error al cargar eventos: ${error.localizedMessage}")
            }
        }
    }

    private fun didSelectRow(evento: EmergencyEventSummary) {
        startActivity(
            Intent(this, EmergencyDetailActivity::class.java)
                .putExtra(EmergencyDetailActivity.EXTRA_EVENT_ID, evento.id)
                .putExtra(EmergencyDetailActivity.EXTRA_ACTIVATED_AT, evento.activatedAt)
        )
    }

    private inner class EventosAdapter : RecyclerView.Adapter<EventosAdapter.EventoViewHolder>() {

        inner class EventoViewHolder(val celda: ItemHistorialBinding) : RecyclerView.ViewHolder(celda.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventoViewHolder {
            return EventoViewHolder(ItemHistorialBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun getItemCount() = eventos.size

        override fun onBindViewHolder(holder: EventoViewHolder, position: Int) {
            val evt = eventos[position]
            val creadaTxt = formatearFecha(evt.activatedAt.orEmpty(), "dd/MM/yyyy HH:mm")
            val cerradaTxt = formatearFecha(evt.closedAt.orEmpty(), "dd/MM/yyyy HH:mm")

            holder.celda.textLabel.text = creadaTxt.ifEmpty { "—" }
            holder.celda.detailTextLabel.text = (evt.status ?: "—") + " • " + cerradaTxt.ifEmpty { "—" }
            holder.celda.root.setOnClickListener { didSelectRow(evt) }
        }
    }
}
