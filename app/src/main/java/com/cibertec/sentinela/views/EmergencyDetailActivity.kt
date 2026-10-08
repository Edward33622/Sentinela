package com.cibertec.sentinela.views

import android.graphics.Paint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cibertec.sentinela.R
import com.cibertec.sentinela.databinding.ActivityEmergencyDetailBinding
import com.cibertec.sentinela.models.EmergencyLocationResponse
import com.cibertec.sentinela.services.ApiClient
import com.cibertec.sentinela.services.realizarPeticion
import com.cibertec.sentinela.utils.SessionManager
import com.cibertec.sentinela.utils.formatearFecha
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.time.OffsetDateTime

class EmergencyDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EVENT_ID = "eventId"
        const val EXTRA_ACTIVATED_AT = "activatedAt"
    }

    private lateinit var binding: ActivityEmergencyDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmergencyDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.mapMapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(-12.0464, -77.0428))
        }

        configurarVista()
    }

    override fun onResume() {
        super.onResume()
        binding.mapMapView.onResume()
    }

    override fun onPause() {
        binding.mapMapView.onPause()
        super.onPause()
    }

    private fun configurarVista() {
        val eventId = intent.getIntExtra(EXTRA_EVENT_ID, 0)
        if (eventId == 0) return
        val activatedAt = intent.getStringExtra(EXTRA_ACTIVATED_AT).orEmpty()

        val fechaTexto = formatearFecha(activatedAt, "dd/MM/yyyy")
        binding.dayLabel.text = "Fecha: " + fechaTexto.ifEmpty { "—" }

        val horaTexto = formatearFecha(activatedAt, "HH:mm")
        binding.scheduleLabel.text = "Hora: " + horaTexto.ifEmpty { "—" }

        cargarUltimaUbicacion(eventId)
    }

    // MARK: - Ubicación del evento

    private fun cargarUltimaUbicacion(eventId: Int) {
        val token = SessionManager(this).userToken ?: return

        lifecycleScope.launch {
            val locations = realizarPeticion {
                ApiClient.emergencyLocations.obtenerUbicacionesPorEvento(eventId, ApiClient.bearer(token))
            }.getOrNull() ?: return@launch

            var datos = locations
            if (datos.isEmpty()) {
                // PRUEBA: datos vacíos -> usamos ubicaciones de ejemplo para validar recorrido
                val now = OffsetDateTime.now().toString()
                datos = listOf(
                    EmergencyLocationResponse(1, eventId, -12.04637, -77.04279, now),
                    EmergencyLocationResponse(2, eventId, -12.04845, -77.03199, now)
                )
                // FIN PRUEBA
            }

            val mapa = binding.mapMapView
            val coords = datos.map { GeoPoint(it.latitude, it.longitude) }

            // Pines de cada ubicación capturada
            datos.forEachIndexed { i, loc ->
                val pin = Marker(mapa)
                pin.position = coords[i]
                pin.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                pin.title = "Ubicación capturada"
                pin.snippet = formatearFecha(loc.capturedAt.orEmpty(), "dd/MM/yyyy HH:mm")
                mapa.overlays.add(pin)
            }

            // Dibujar recorrido como polilínea si hay al menos 2 puntos
            if (coords.size >= 2) {
                val ruta = Polyline(mapa)
                ruta.setPoints(coords)
                ruta.outlinePaint.apply {
                    color = ContextCompat.getColor(this@EmergencyDetailActivity, R.color.system_blue)
                    strokeWidth = 3 * resources.displayMetrics.density
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                }
                mapa.overlays.add(0, ruta)
            }

            // Centrar en la última ubicación (~500 m alrededor)
            coords.lastOrNull()?.let {
                mapa.controller.setZoom(17.0)
                mapa.controller.setCenter(it)
            }
            mapa.invalidate()
        }
    }
}
