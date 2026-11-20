package com.cibertec.sentinela.views

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cibertec.sentinela.R
import com.cibertec.sentinela.databinding.ActivityCopilotoIaBinding
import com.cibertec.sentinela.models.AnalyzeRouteRequest
import com.cibertec.sentinela.services.ApiClient
import com.cibertec.sentinela.services.realizarPeticion
import com.cibertec.sentinela.utils.MapStyleUtils
import com.cibertec.sentinela.utils.RutaSegura
import com.cibertec.sentinela.utils.SentinelaLocationOverlay
import com.cibertec.sentinela.utils.SentinelaToast
import com.cibertec.sentinela.utils.SessionManager
import com.cibertec.sentinela.utils.ocultarTeclado
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Copiloto IA (CU15): planificación del trayecto, análisis de riesgo con Google Gemini y ruta en el mapa.
 * Si el usuario inicia el trayecto seguro, devuelve los datos al inicio, que dibuja la ruta y activa el temporizador.
 */
class CopilotoIaActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ORIGEN = "copiloto_origen"
        const val EXTRA_DESTINO = "copiloto_destino"
        const val EXTRA_MEDIO = "copiloto_medio"
        const val EXTRA_MINUTOS = "copiloto_minutos"
        const val EXTRA_RIESGO = "copiloto_riesgo"

        private val MEDIOS = arrayOf(
            "A pie", "Taxi por aplicativo", "Transporte público", "Vehículo particular", "Bus interprovincial / Vuelo"
        )
        private val ATAJOS = linkedMapOf(
            "🎓 Cibertec" to "Instituto Cibertec (Sede Miraflores)",
            "🏠 Casa" to "Casa (Destino habitual)",
            "💼 Trabajo" to "Centro Laboral / Oficina",
            "🏬 C. Comercial" to "Centro Comercial"
        )
    }

    private lateinit var binding: ActivityCopilotoIaBinding
    private lateinit var session: SessionManager
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private var locationOverlay: MyLocationNewOverlay? = null
    private var rutaPolyline: Polyline? = null
    private val rutaMarkers = mutableListOf<Marker>()
    private var routeRequestGeneration = 0L

    // Último análisis mostrado (para iniciar el trayecto o avisar por WhatsApp)
    private var origenAnalizado = ""
    private var destinoAnalizado = ""
    private var medioAnalizado = ""
    private var nivelAnalizado = ""
    private var minutosAnalizados = 0
    private var tiempoAnalizado = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCopilotoIaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)

        binding.btnVolver.setOnClickListener { finish() }
        binding.btnAnalizar.setOnClickListener { ocultarTeclado(); analizar() }
        binding.btnCentrar.setOnClickListener { centrarEnUbicacion() }
        binding.btnIniciarTrayecto.setOnClickListener { iniciarTrayecto() }
        binding.btnWhatsApp.setOnClickListener {
            compartirRutaPorWhatsApp(origenAnalizado, destinoAnalizado, medioAnalizado, tiempoAnalizado, nivelAnalizado)
        }

        setupFormulario()
        setupMapa()
        actualizarCuota()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        locationOverlay?.enableMyLocation()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        locationOverlay?.disableMyLocation()
    }

    // MARK: - Formulario

    private fun setupFormulario() {
        binding.txtOrigen.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, RutaSegura.DISTRITOS_LIMA_CALLAO))
        binding.txtOrigen.setText(RutaSegura.DISTRITOS_LIMA_CALLAO.first(), false)
        binding.txtOrigen.setOnItemClickListener { _, _, posicion, _ ->
            val esOtra = RutaSegura.DISTRITOS_LIMA_CALLAO[posicion] == RutaSegura.OTRA_PROVINCIA
            binding.tilOrigenPersonalizado.visibility = if (esOtra) View.VISIBLE else View.GONE
        }

        val destinos = RutaSegura.DISTRITOS_LIMA_CALLAO.filter { it != RutaSegura.OTRA_PROVINCIA } +
                "Mercado Ciudad de Dios, San Juan de Miraflores"
        binding.txtDestino.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, destinos))

        binding.txtMedio.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, MEDIOS))
        binding.txtMedio.setText(MEDIOS.first(), false)

        val dp = resources.displayMetrics.density
        for ((etiqueta, lugar) in ATAJOS) {
            val chip = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = etiqueta
                textSize = 12f
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                insetTop = 0
                insetBottom = 0
                setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
                cornerRadius = (50 * dp).toInt()
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.chip_green_bg))
                strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.chip_green_stroke))
                setTextColor(ContextCompat.getColor(context, R.color.chip_green_text))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (8 * dp).toInt() }
                setOnClickListener {
                    binding.txtDestino.setText(lugar, false)
                    binding.txtDestino.setSelection(lugar.length)
                }
            }
            binding.layoutAtajos.addView(chip)
        }
    }

    private fun actualizarCuota() {
        binding.lblCuota.text = if (session.esPro()) "Pro · ilimitado" else "${session.consultasIaRestantesDia()}/4 hoy"
    }

    private fun origenElegido(): String {
        val seleccionado = binding.txtOrigen.text.toString()
        return if (seleccionado == RutaSegura.OTRA_PROVINCIA) {
            binding.txtOrigenPersonalizado.text.toString().trim().ifEmpty { "Provincia fuera de Lima" }
        } else {
            seleccionado
        }
    }

    // MARK: - Mapa

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMapa() {
        binding.mapView.apply {
            MapStyleUtils.aplicarEstiloModerno(this, session.mapDarkThemeFor(this@CopilotoIaActivity))
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            setMultiTouchControls(true)
            controller.setZoom(12.0)
            controller.setCenter(GeoPoint(-12.0464, -77.0428))
            // Dentro del scroll, el mapa conserva los gestos de arrastre y zoom
            setOnTouchListener { v, evento ->
                v.parent.requestDisallowInterceptTouchEvent(evento.action != MotionEvent.ACTION_UP)
                false
            }
        }

        if (!tienePermisoUbicacion()) return

        val provider = GpsMyLocationProvider(this).apply { addLocationSource(LocationManager.NETWORK_PROVIDER) }
        val overlay = SentinelaLocationOverlay(this, provider, binding.mapView)
        overlay.enableMyLocation()
        overlay.runOnFirstFix {
            runOnUiThread { overlay.myLocation?.let { autodetectarOrigen(it.latitude, it.longitude) } }
        }
        binding.mapView.overlays.add(overlay)
        locationOverlay = overlay

        fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
            if (loc != null) {
                autodetectarOrigen(loc.latitude, loc.longitude)
                binding.mapView.controller.setZoom(15.0)
                binding.mapView.controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
            }
        }
    }

    /** Selecciona como origen el distrito más cercano a la ubicación GPS. */
    private fun autodetectarOrigen(lat: Double, lng: Double) {
        if (rutaPolyline != null) return
        val (distrito, distancia) = RutaSegura.buscarDistritoMasCercano(lat, lng)
        if (distrito != null && distancia < 35_000) {
            binding.txtOrigen.setText(distrito, false)
            binding.tilOrigenPersonalizado.visibility = View.GONE
            binding.lblGps.text = "📍 GPS autodetectado: $distrito"
        }
    }

    private fun centrarEnUbicacion() {
        val gps = locationOverlay?.myLocation
        if (gps != null) {
            binding.mapView.controller.animateTo(gps)
            binding.mapView.controller.setZoom(16.0)
        } else {
            SentinelaToast.show(this, "Ubicación aún no disponible", SentinelaToast.Type.INFO)
        }
    }

    private fun tienePermisoUbicacion() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun trazarRuta(origen: String, destino: String, medio: String, nivelRiesgo: String, esInviable: Boolean) {
        rutaPolyline?.let { binding.mapView.overlays.remove(it) }
        rutaPolyline = null
        rutaMarkers.forEach { binding.mapView.overlays.remove(it) }
        rutaMarkers.clear()

        // Si el GPS está cerca del distrito de origen, la ruta parte de la posición real
        val gps = locationOverlay?.myLocation
        val coordsOrigen = RutaSegura.resolverCoordenadaLugar(origen)
        val pOrigen = if (gps != null) {
            val distancia = FloatArray(1)
            Location.distanceBetween(gps.latitude, gps.longitude, coordsOrigen.latitude, coordsOrigen.longitude, distancia)
            if (distancia[0] < 5000) gps else coordsOrigen
        } else {
            coordsOrigen
        }
        val pDestino = RutaSegura.resolverCoordenadaLugar(destino)
        val (colorHex, etiqueta, detalle) = RutaSegura.semaforo(nivelRiesgo, esInviable)

        val pinOrigen = Marker(binding.mapView).apply {
            position = pOrigen
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "🚩 Origen: $origen"
            icon = RutaSegura.crearIconoMarcador(this@CopilotoIaActivity, "🚩", Color.parseColor("#10B981"))
        }
        val pinDestino = Marker(binding.mapView).apply {
            position = pDestino
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "🏁 Destino: $destino"
            snippet = "$etiqueta\n$detalle"
            icon = RutaSegura.crearIconoMarcador(this@CopilotoIaActivity, "🏁", Color.parseColor(colorHex))
        }
        binding.mapView.overlays.add(pinOrigen)
        binding.mapView.overlays.add(pinDestino)
        rutaMarkers += listOf(pinOrigen, pinDestino)
        binding.lblEstadoRuta.text = "Consultando calles…"

        val generation = ++routeRequestGeneration
        lifecycleScope.launch {
            val ruta = RutaSegura.obtenerRutaCallesReal(pOrigen, pDestino, medio)
            if (generation != routeRequestGeneration || isFinishing || isDestroyed) return@launch

            rutaPolyline = Polyline(binding.mapView).apply {
                outlinePaint.apply {
                    color = Color.parseColor(colorHex)
                    strokeWidth = 6f * resources.displayMetrics.density
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                    isAntiAlias = true
                }
                setPoints(ruta.points)
                title = "$etiqueta: $detalle"
            }
            binding.mapView.overlays.add(0, rutaPolyline)
            binding.lblEstadoRuta.text = if (ruta.isApproximate) "Ruta aproximada" else etiqueta
            try {
                binding.mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(ruta.points), true, 80)
            } catch (_: Exception) {
                binding.mapView.controller.setCenter(pOrigen)
                binding.mapView.controller.setZoom(14.0)
            }
            binding.mapView.invalidate()
        }
    }

    // MARK: - Validaciones previas al análisis

    private fun analizar() {
        if (!session.puedeUsarIa()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("🤖 Límite Diario de IA Alcanzado (4/4)")
                .setMessage("Has completado tus 4 análisis de ruta gratuitos de hoy con Sentinela Free.\n\nTu cuota diaria se reinicia automáticamente cada medianoche (00:00 hrs). Actualiza a Sentinela Pro para disfrutar de consultas ilimitadas con Gemini AI y el Modo Acompañante Seguro.")
                .setPositiveButton("Ver Planes Pro") { _, _ -> startActivity(Intent(this, SuscripcionActivity::class.java)) }
                .setNegativeButton("Entendido", null)
                .show()
            return
        }

        val destinoEscrito = binding.txtDestino.text.toString().trim()
        if (destinoEscrito.isEmpty()) {
            binding.tilDestino.error = "Escribe un destino o elige un atajo"
            return
        }
        binding.tilDestino.error = null
        continuarConDestino(origenElegido(), destinoEscrito, binding.txtMedio.text.toString())
    }

    private fun continuarConDestino(origen: String, destino: String, medio: String) {
        val nombre = destino.trim().uppercase()
        val esCiudadDeDiosAmbiguo = nombre.contains("CIUDAD DE DIOS") && !nombre.contains("SAN JUAN") && !nombre.contains("MERCADO")
        if (!esCiudadDeDiosAmbiguo) {
            validarHorario(origen, destino, medio)
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Confirma el destino")
            .setMessage("Hay lugares con el nombre Ciudad de Dios. Elige el resultado de San Juan de Miraflores o vuelve para precisar otra localidad.")
            .setItems(arrayOf("Mercado Ciudad de Dios · San Juan de Miraflores", "Volver y precisar el destino")) { _, opcion ->
                if (opcion == 0) {
                    val precisado = "Mercado Ciudad de Dios, San Juan de Miraflores"
                    binding.txtDestino.setText(precisado, false)
                    validarHorario(origen, precisado, medio)
                } else {
                    binding.txtDestino.requestFocus()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** De 22:30 a 05:30 sugiere taxi en lugar de caminar. */
    private fun validarHorario(origen: String, destino: String, medio: String) {
        val cal = java.util.Calendar.getInstance()
        val hora = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val minuto = cal.get(java.util.Calendar.MINUTE)
        val esNochePeligrosa = hora > 22 || (hora == 22 && minuto >= 30) || hora < 5 || (hora == 5 && minuto <= 30)

        if (medio == "A pie" && esNochePeligrosa) {
            MaterialAlertDialogBuilder(this)
                .setTitle("⚠️ Sugerencia Automática de Seguridad")
                .setMessage("Son las ${String.format("%02d:%02d", hora, minuto)} hrs.\n\n⚠️ Por tu seguridad, a esta hora te recomendamos cambiar el medio a 'Taxi por aplicativo' para evitar caminar por paraderos oscuros y zonas solitarias en $origen.\n\n¿Deseas cambiar el medio de transporte o continuar a pie?")
                .setPositiveButton("🚕 Cambiar a Taxi por aplicativo") { _, _ ->
                    binding.txtMedio.setText("Taxi por aplicativo", false)
                    validarCobertura(origen, destino, "Taxi por aplicativo")
                }
                .setNegativeButton("Continuar A pie") { _, _ -> validarCobertura(origen, destino, "A pie") }
                .setNeutralButton("Cancelar", null)
                .show()
            return
        }
        validarCobertura(origen, destino, medio)
    }

    private fun validarCobertura(origen: String, destino: String, medio: String) {
        val origenEsProvincia = RutaSegura.PROVINCIAS_FUERA.any { origen.uppercase().contains(it) } ||
                origen.contains("fuera de Lima", ignoreCase = true)
        val destinoEsProvincia = RutaSegura.PROVINCIAS_FUERA.any { destino.uppercase().contains(it) }

        if (origenEsProvincia) {
            MaterialAlertDialogBuilder(this)
                .setTitle("⚠️ Origen Fuera de Rango Metropolitano")
                .setMessage("El punto de partida '$origen' se encuentra fuera de la cobertura urbana activa de Sentinela (Lima Metropolitana y Callao).\n\n¿Deseas consultar el análisis de riesgo de la IA de todas formas?")
                .setPositiveButton("Continuar con IA") { _, _ -> consultarIa(origen, destino, medio) }
                .setNegativeButton("Cambiar origen") { _, _ -> binding.txtOrigen.requestFocus() }
                .show()
            return
        }
        if (destinoEsProvincia && medio == "A pie") {
            MaterialAlertDialogBuilder(this)
                .setTitle("🚫 Destino Fuera de Rango e Inviable a Pie")
                .setMessage("El destino '$destino' se encuentra a cientos de kilómetros en otra provincia.\n\nEs físicamente imposible y de riesgo extremo viajar a pie. Consultaremos a Google Gemini para darte los datos oficiales de distancia y alternativas seguras.")
                .setPositiveButton("Consultar Informe IA") { _, _ -> consultarIa(origen, destino, medio) }
                .setNegativeButton("Cambiar Destino") { _, _ -> binding.txtDestino.requestFocus() }
                .show()
            return
        }
        consultarIa(origen, destino, medio)
    }

    // MARK: - Consulta a la IA y resultado

    private fun mostrarCargando(cargando: Boolean) {
        binding.progressAnalisis.visibility = if (cargando) View.VISIBLE else View.GONE
        binding.btnAnalizar.isEnabled = !cargando
        binding.btnAnalizar.text = if (cargando) "Consultando a Google Gemini…" else "Analizar con IA"
    }

    private fun consultarIa(origen: String, destino: String, medio: String) {
        val cal = java.util.Calendar.getInstance()
        val horaTexto = String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
        mostrarCargando(true)

        lifecycleScope.launch {
            val req = AnalyzeRouteRequest(origin = origen, destination = destino, transport = medio, hour = horaTexto)
            val resultado = realizarPeticion { ApiClient.ai.analizarRuta(req) }
            mostrarCargando(false)

            resultado.onSuccess { res ->
                session.registrarConsultaIa()
                actualizarCuota()

                val nivel = res.riskLevel ?: "🟡 RIESGO MODERADO"
                val minutos = res.suggestedMinutes
                val esInviable = res.isFeasible == false || minutos > 300
                val tiempo = when {
                    minutos >= 1440 -> "${minutos / 1440} días y ${(minutos % 1440) / 60} h"
                    minutos >= 60 -> "${minutos / 60} h ${minutos % 60} min"
                    else -> "$minutos min"
                }

                origenAnalizado = origen
                destinoAnalizado = destino
                medioAnalizado = medio
                nivelAnalizado = nivel
                minutosAnalizados = minutos
                tiempoAnalizado = tiempo

                trazarRuta(origen, destino, medio, nivel, esInviable)
                mostrarResultado(res.analysis, res.estimatedDistance, res.poweredByGemini, esInviable)
            }.onFailure { err ->
                MaterialAlertDialogBuilder(this@CopilotoIaActivity)
                    .setTitle("⚠️ Error al Conectar con Sentinela IA")
                    .setMessage("No se pudo obtener la evaluación en tiempo real de Google Gemini (${err.localizedMessage ?: "Tiempo de espera agotado"}).\n\nPor favor verifica tu conexión a internet o el servicio de Sentinela y vuelve a intentar.")
                    .setPositiveButton("Reintentar") { _, _ -> consultarIa(origen, destino, medio) }
                    .setNegativeButton("Cerrar", null)
                    .show()
            }
        }
    }

    private fun mostrarResultado(analisis: String?, distancia: String?, conGemini: Boolean, esInviable: Boolean) {
        val (colorHex, _, _) = RutaSegura.semaforo(nivelAnalizado, esInviable)
        val color = Color.parseColor(colorHex)
        binding.lblRiesgo.text = nivelAnalizado
        binding.lblRiesgo.setTextColor(color)
        binding.lblRiesgo.background.mutate().setTint(Color.argb(40, Color.red(color), Color.green(color), Color.blue(color)))

        binding.lblRutaResumen.text = "${origenAnalizado.substringBefore(" (")} → ${RutaSegura.nombreDestinoVisible(destinoAnalizado)}"
        binding.lblTiempo.text = "⏱️ $tiempoAnalizado · $medioAnalizado"
        binding.lblDistancia.visibility = if (distancia.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.lblDistancia.text = "📏 ${distancia.orEmpty()}"
        binding.lblAnalisis.text = analisis ?: "Sin información disponible."
        binding.lblFuente.text = if (conGemini) {
            "🟢 Análisis de Google Gemini · ruta vial y semáforo de riesgo en el mapa"
        } else {
            "⚠️ Modo local preventivo (sin conexión con Gemini)"
        }

        binding.btnIniciarTrayecto.isEnabled = !esInviable
        binding.btnIniciarTrayecto.text = if (esInviable) "Ruta no iniciable" else "Iniciar trayecto seguro ($minutosAnalizados min)"
        binding.btnWhatsApp.visibility = if (esInviable) View.GONE else View.VISIBLE

        binding.cardResultado.visibility = View.VISIBLE
        binding.scroll.post { binding.scroll.smoothScrollTo(0, binding.cardResultado.top) }
    }

    /** Devuelve el trayecto al inicio, que traza la ruta en su mapa y activa el temporizador. */
    private fun iniciarTrayecto() {
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(EXTRA_ORIGEN, origenAnalizado)
                .putExtra(EXTRA_DESTINO, destinoAnalizado)
                .putExtra(EXTRA_MEDIO, medioAnalizado)
                .putExtra(EXTRA_MINUTOS, minutosAnalizados)
                .putExtra(EXTRA_RIESGO, nivelAnalizado)
        )
        finish()
    }

    private fun compartirRutaPorWhatsApp(origen: String, destino: String, medio: String, tiempo: String, riesgo: String) {
        val mensaje = """
            🛡️ Sentinela - Trayecto Seguro en curso:
            • Origen: $origen
            • Destino: $destino
            • Transporte: $medio
            • Tiempo estimado por IA: $tiempo
            • Evaluación: $riesgo
            
            Mi desplazamiento está siendo supervisado por la app Sentinela.
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, mensaje)
            `package` = "com.whatsapp"
        }

        try {
            startActivity(intent)
        } catch (_: Exception) {
            val shareGeneral = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, mensaje)
            }
            startActivity(Intent.createChooser(shareGeneral, "Compartir ruta segura"))
        }
    }
}
