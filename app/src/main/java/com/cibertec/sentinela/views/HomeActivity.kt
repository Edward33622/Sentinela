package com.cibertec.sentinela.views

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.cibertec.sentinela.R
import com.cibertec.sentinela.data.ContactoEntity
import com.cibertec.sentinela.data.SentinelaDatabase
import com.cibertec.sentinela.databinding.ActivityHomeBinding
import com.cibertec.sentinela.databinding.ItemContactoGuardadoBinding
import com.cibertec.sentinela.emergency.EmergencyManager
import com.cibertec.sentinela.emergency.EmergencyService
import com.cibertec.sentinela.models.CreateContactRequest
import com.cibertec.sentinela.models.UserDetail
import com.cibertec.sentinela.models.FamilyMemberDto
import com.cibertec.sentinela.models.FamilyPingRequest
import com.cibertec.sentinela.services.ApiClient
import com.cibertec.sentinela.services.realizarPeticion
import com.cibertec.sentinela.utils.BatteryMonitor
import com.cibertec.sentinela.utils.RutaSegura
import com.cibertec.sentinela.utils.SessionManager
import com.cibertec.sentinela.utils.SentinelaToast
import com.cibertec.sentinela.utils.presentarAlerta
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class HomeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USUARIO = "usuarioSesion"
        const val EXTRA_TRIGGER_EMERGENCY = "TriggerSentinelaEmergency"
        private const val TAG = "HomeActivity"
    }

    private lateinit var binding: ActivityHomeBinding
    private lateinit var session: SessionManager
    private val contactoDao by lazy { SentinelaDatabase.get(this).contactoDao() }
    private val connectivityManager by lazy { getSystemService(ConnectivityManager::class.java) }
    private var networkCallbackRegistered = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = runOnUiThread { actualizarEstadoConectividad() }
        override fun onLost(network: Network) = runOnUiThread { actualizarEstadoConectividad() }
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) =
            runOnUiThread { actualizarEstadoConectividad() }
    }

    var usuarioSesion: UserDetail? = null

    private var isOpen = true
    private var contactosGuardados: List<ContactoEntity> = emptyList()
    private val contactosAdapter = ContactosGuardadosAdapter()
    private var locationOverlay: MyLocationNewOverlay? = null

    private val sensorManager by lazy { getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager }
    private var shakeDetector: com.cibertec.sentinela.emergency.ShakeDetector? = null
    private var timerTrayecto: android.os.CountDownTimer? = null

    // Superposiciones de ruta y detección inteligente
    private var rutaPolylineOverlay: Polyline? = null
    private val rutaMarkers: MutableList<Marker> = mutableListOf()
    private var routeRequestGeneration: Long = 0L
    private val familyMarkers: MutableList<Marker> = mutableListOf()
    private var securityOverlayHandles: com.cibertec.sentinela.utils.SecurityOverlayHandles? = null
    private var capaSeguridadActiva: Boolean = true
    private var rutaPuntosActuales: List<GeoPoint> = emptyList()
    private var timerVerificacion: android.os.CountDownTimer? = null
    private var dialogoVerificacionActivo: androidx.appcompat.app.AlertDialog? = null

    // Variables de seguimiento en Trayecto Seguro
    private var trayectoOrigenActual: String = ""
    private var trayectoDestinoActual: String = ""
    private var trayectoMedioActual: String = ""
    private var puntoOrigenTrayecto: GeoPoint? = null
    private var puntoDestinoTrayecto: GeoPoint? = null
    private var ultimaUbicacionTrayecto: GeoPoint? = null
    private var segundosInmovil: Int = 0
    private var desvioAlertaDisparada: Boolean = false
    private var inmovilAlertaDisparada: Boolean = false

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    // El Copiloto IA devuelve el trayecto elegido: se dibuja la ruta aquí y se activa el temporizador
    private val copilotoLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
            val datos = resultado.data
            if (resultado.resultCode != RESULT_OK || datos == null) return@registerForActivityResult
            val origen = datos.getStringExtra(CopilotoIaActivity.EXTRA_ORIGEN).orEmpty()
            val destino = datos.getStringExtra(CopilotoIaActivity.EXTRA_DESTINO).orEmpty()
            val medio = datos.getStringExtra(CopilotoIaActivity.EXTRA_MEDIO).orEmpty()
            val minutos = datos.getIntExtra(CopilotoIaActivity.EXTRA_MINUTOS, 15)
            val riesgo = datos.getStringExtra(CopilotoIaActivity.EXTRA_RIESGO).orEmpty()
            trazarRutaSemaforoEnMapa(origen, destino, medio, riesgo, esInviable = false)
            iniciarTimerTrayecto(minutos, origen, destino, medio)
        }

    private val permisosLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultado ->
            val concedidoUbicacion = resultado[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                            resultado[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                            tienePermisoUbicacion()
            if (concedidoUbicacion) {
                mostrarUbicacionDelUsuario()
                centrarEnUbicacionActual(animar = true)
            } else {
                actualizarEstadoUbicacion(null)
            }
            if (resultado[Manifest.permission.SEND_SMS] == false) {
                SentinelaToast.show(
                    this,
                    "Permiso de SMS no concedido: la alerta por SMS requiere este permiso.",
                    SentinelaToast.Type.WARNING,
                    SentinelaToast.Length.LONG
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)

        if (!session.pinsConfigured || session.pinSeguridad.isBlank() || session.pinCoaccion.isBlank()) {
            session.pinsConfigured = false
            startActivity(
                Intent(this, PinSetupActivity::class.java)
                    .putExtra(EXTRA_TRIGGER_EMERGENCY, intent.getBooleanExtra(EXTRA_TRIGGER_EMERGENCY, false))
            )
            finish()
            return
        }

        @Suppress("DEPRECATION")
        usuarioSesion = intent.getSerializableExtra(EXTRA_USUARIO) as? UserDetail
        if (usuarioSesion == null) {
            val token = session.userToken
            val userId = session.userId
            if (token != null && userId != 0) {
                lifecycleScope.launch {
                    realizarPeticion { ApiClient.users.obtenerUsuario(userId, ApiClient.bearer(token)) }
                        .onSuccess { usuarioSesion = it }
                }
            }
        }

        setupMap()
        setupCollectionView()
        actualizarEstiloModoDiscreto()
        cargarContactosLocales()
        setupShakeDetector()

        binding.panelHandle.setOnClickListener { togglePanel() }
        binding.btnConfiguracion.setOnClickListener { funcionBtnConfiguracion() }
        binding.btnPerfil.setOnClickListener { funcionBtnPerfil() }
        binding.btnModoDiscreto.setOnClickListener { funcionBtnModoDiscreto() }
        binding.btnDesactivar.setOnClickListener { it.feedbackHaptico(); funcionBtnDesactivar() }
        binding.btnAgregarContacto.setOnClickListener { funcionBtnAgregarContacto() }
        binding.btnHistorial.setOnClickListener { startActivity(Intent(this, EmergencyHistoryActivity::class.java)) }
        binding.btnAsistenteIa.setOnClickListener {
            it.feedbackHaptico()
            copilotoLauncher.launch(Intent(this, CopilotoIaActivity::class.java))
        }
        binding.btnTrayectoSeguro.setOnClickListener { it.feedbackHaptico(); mostrarDialogoTrayectoSeguro() }
        binding.btnFinalizarTrayecto.setOnClickListener { it.feedbackHaptico(); finalizarTrayectoSeguro() }
        binding.btnFamilia.setOnClickListener {
            it.feedbackHaptico()
            startActivity(Intent(this, FamilyActivity::class.java))
        }
        binding.btnLlamadaFalsa.setOnClickListener { it.feedbackHaptico(); mostrarDialogoLlamadaFalsa() }
        binding.btnSosCentrales.setOnClickListener { it.feedbackHaptico(); mostrarDialogoSosCentrales() }
        binding.btnMiUbicacion.setOnClickListener { it.feedbackHaptico(); centrarEnUbicacionActual(animar = true) }

        // Actualizar card de estado de seguridad y badge inicial
        actualizarCardEstadoSeguridad()
        actualizarBadgeHistorial()

        // El botón principal refleja el estado de la emergencia
        lifecycleScope.launch {
            EmergencyManager.isServiceActive.collect { actualizarUIBotonPrincipal(it) }
        }

        // Contacto elegido en la hoja de contactos
        supportFragmentManager.setFragmentResultListener(ContactsBottomSheet.REQUEST_KEY, this) { _, datos ->
            contactoSeleccionado(
                nombreCompleto = datos.getString(ContactsBottomSheet.KEY_NOMBRE).orEmpty(),
                telefono = datos.getString(ContactsBottomSheet.KEY_TELEFONO).orEmpty(),
                id = datos.getString(ContactsBottomSheet.KEY_ID).orEmpty(),
                email = datos.getString(ContactsBottomSheet.KEY_EMAIL)
            )
        }

        handleEmergencyShortcut(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleEmergencyShortcut(intent)
        if (intent.getBooleanExtra("EXTRA_REASON_SHAKE", false)) {
            intent.removeExtra("EXTRA_REASON_SHAKE")
            presentarAlerta("🚨 ¡ALERTA ACTIVADA POR SACUDIDA (Shake Detection)!")
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        locationOverlay?.enableMyLocation()
        if (locationOverlay?.myLocation != null) {
            actualizarEstadoUbicacionDisponible()
        } else {
            actualizarEstadoUbicacion(null)
        }
        actualizarEstadoConectividad()
        mostrarFotoPerfil()
        if (session.shakeEnabled) {
            com.cibertec.sentinela.emergency.ShakeService.iniciar(this)
        }
        actualizarCardEstadoSeguridad()
        actualizarBadgeHistorial()
        enviarPingFamiliarYActualizarMapa()
    }

    override fun onStart() {
        super.onStart()
        if (!networkCallbackRegistered) {
            try {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
                networkCallbackRegistered = true
            } catch (e: RuntimeException) {
                Log.w(TAG, "No se pudo registrar el estado de red", e)
            }
        }
        actualizarEstadoConectividad()
    }

    override fun onStop() {
        if (networkCallbackRegistered) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (e: RuntimeException) {
                Log.w(TAG, "No se pudo retirar el observador de red", e)
            }
            networkCallbackRegistered = false
        }
        super.onStop()
    }

    override fun onPause() {
        locationOverlay?.disableMyLocation()
        binding.mapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        timerTrayecto?.cancel()
        timerVerificacion?.cancel()
        dialogoVerificacionActivo?.dismiss()
        dialogoVerificacionActivo = null
        super.onDestroy()
    }

    /** Activación desde el acceso directo o el enlace sentinela://activate. */
    private fun handleEmergencyShortcut(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_TRIGGER_EMERGENCY, false) != true) return
        intent.removeExtra(EXTRA_TRIGGER_EMERGENCY)
        if (!EmergencyManager.isServiceActive.value) {
            funcionBtnDesactivar()
        }
    }

    // MARK: - Mapa

    private fun setupMap() {
        binding.mapView.apply {
            com.cibertec.sentinela.utils.MapStyleUtils.aplicarEstiloModerno(this, session.mapDarkThemeFor(this@HomeActivity))
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(-12.0464, -77.0428))
        }

        binding.btnEstiloMapa.setOnClickListener {
            session.mapDarkTheme = !session.mapDarkThemeFor(this)
            com.cibertec.sentinela.utils.MapStyleUtils.aplicarEstiloModerno(binding.mapView, session.mapDarkThemeFor(this))
            binding.mapView.invalidate()
            val modo = if (session.mapDarkThemeFor(this)) "Mapa nocturno activado" else "Mapa diurno activado"
            SentinelaToast.show(this, modo, SentinelaToast.Type.INFO)
        }

        // Capa de Prevención Activa: Puntos de Ayuda (Comisarías/Serenazgo) y Zonas de Riesgo
        alternarCapaSeguridad(activar = true)

        binding.btnCapaSeguridad.setOnClickListener {
            capaSeguridadActiva = !capaSeguridadActiva
            alternarCapaSeguridad(capaSeguridadActiva)
            val estado = if (capaSeguridadActiva) "Zonas de riesgo y comisarías visibles" else "Capa de prevención oculta"
            binding.btnCapaSeguridad.imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, if (capaSeguridadActiva) R.color.text_brand else R.color.text_muted)
            )
            SentinelaToast.show(this, estado, SentinelaToast.Type.INFO)
        }

        if (tienePermisoUbicacion()) {
            mostrarUbicacionDelUsuario()
        }
        verificarYSolicitarPermisos()
    }

    private fun alternarCapaSeguridad(activar: Boolean) {
        if (activar) {
            if (securityOverlayHandles == null) {
                securityOverlayHandles = com.cibertec.sentinela.utils.SecurityMapManager.cargarCapaSeguridad(
                    context = this,
                    mapView = binding.mapView,
                    onPuntoClick = { punto ->
                        com.cibertec.sentinela.utils.SecurityMapManager.mostrarDialogoPuntoAyuda(this, punto)
                    },
                    onZonaClick = { zona ->
                        com.cibertec.sentinela.utils.SecurityMapManager.mostrarDialogoZonaRiesgo(this, zona)
                    }
                )
            }
        } else {
            securityOverlayHandles?.let {
                com.cibertec.sentinela.utils.SecurityMapManager.removerCapaSeguridad(binding.mapView, it)
            }
            securityOverlayHandles = null
        }
    }

    private fun verificarYSolicitarPermisos() {
        val faltantes = mutableListOf<String>()
        if (!tienePermisoUbicacion()) {
            faltantes.add(Manifest.permission.ACCESS_FINE_LOCATION)
            faltantes.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            faltantes.add(Manifest.permission.SEND_SMS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                faltantes.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (faltantes.isNotEmpty()) {
            permisosLauncher.launch(faltantes.toTypedArray())
        }
    }

    private fun solicitarPermisosUbicacion() = verificarYSolicitarPermisos()

    private fun tienePermisoUbicacion(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun mostrarUbicacionDelUsuario() {
        if (!tienePermisoUbicacion()) {
            actualizarEstadoUbicacion(null)
            return
        }

        if (locationOverlay == null) {
            val provider = GpsMyLocationProvider(this).apply {
                // Agregar fuente de red para fijar ubicación al instante en interiores o emuladores
                addLocationSource(LocationManager.NETWORK_PROVIDER)
            }
            val overlay = com.cibertec.sentinela.utils.SentinelaLocationOverlay(this, provider, binding.mapView)
            overlay.enableMyLocation()
            overlay.runOnFirstFix {
                runOnUiThread {
                    overlay.myLocation?.let {
                        actualizarEstadoUbicacionDisponible()
                        binding.mapView.controller.setZoom(17.0)
                        binding.mapView.controller.animateTo(it)
                    }
                }
            }
            binding.mapView.overlays.add(overlay)
            locationOverlay = overlay
        } else {
            locationOverlay?.enableMyLocation()
        }

        centrarEnUbicacionActual(animar = false)
    }

    @SuppressLint("MissingPermission")
    private fun centrarEnUbicacionActual(animar: Boolean = true) {
        if (!tienePermisoUbicacion()) {
            actualizarEstadoUbicacion(null)
            solicitarPermisosUbicacion()
            return
        }

        // 1. Si el overlay ya tiene fix de posición, usarlo directamente
        val myLoc = locationOverlay?.myLocation
        if (myLoc != null) {
            actualizarEstadoUbicacionDisponible()
            binding.mapView.controller.setZoom(17.0)
            if (animar) {
                binding.mapView.controller.animateTo(myLoc)
            } else {
                binding.mapView.controller.setCenter(myLoc)
            }
            return
        }

        // 2. Si todavía no hay fix en el overlay, consultar FusedLocationProviderClient (inmediato)
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                EmergencyManager.lastKnownLocation = loc
                actualizarEstadoUbicacion(loc)
                val geoPoint = GeoPoint(loc.latitude, loc.longitude)
                binding.mapView.controller.setZoom(17.0)
                if (animar) {
                    binding.mapView.controller.animateTo(geoPoint)
                } else {
                    binding.mapView.controller.setCenter(geoPoint)
                }
            } else {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { freshLoc ->
                        if (freshLoc != null) {
                            EmergencyManager.lastKnownLocation = freshLoc
                            actualizarEstadoUbicacion(freshLoc)
                            val geoPoint = GeoPoint(freshLoc.latitude, freshLoc.longitude)
                            binding.mapView.controller.setZoom(17.0)
                            if (animar) {
                                binding.mapView.controller.animateTo(geoPoint)
                            } else {
                                binding.mapView.controller.setCenter(geoPoint)
                            }
                        }
                    }
            }
        }
    }

    private fun actualizarEstadoUbicacion(location: Location?) {
        if (!::binding.isInitialized) return
        binding.lblEstadoGps.text = when {
            !session.locationEnabled -> "Ubicación pausada en ajustes"
            !tienePermisoUbicacion() -> "Permiso de ubicación desactivado"
            location != null -> "Ubicación disponible"
            else -> "Buscando señal GPS…"
        }
        binding.lblEstadoGps.contentDescription = binding.lblEstadoGps.text
    }

    private fun actualizarEstadoUbicacionDisponible() {
        if (!::binding.isInitialized) return
        binding.lblEstadoGps.text = if (!session.locationEnabled) {
            "Ubicación pausada en ajustes"
        } else if (tienePermisoUbicacion()) {
            "Ubicación disponible"
        } else {
            "Permiso de ubicación desactivado"
        }
        binding.lblEstadoGps.contentDescription = binding.lblEstadoGps.text
    }

    private fun actualizarEstadoConectividad() {
        if (!::binding.isInitialized) return
        val capabilities = connectivityManager.activeNetwork?.let(connectivityManager::getNetworkCapabilities)
        val validada = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        binding.lblEstadoRed.text = if (validada) "En línea" else "Sin internet"
        binding.lblEstadoRed.contentDescription = if (validada) {
            "Conexión a internet disponible"
        } else {
            "Sin conexión a internet; algunas funciones pueden quedar pendientes"
        }
        if (EmergencyManager.isServiceActive.value) {
            actualizarEstadoRuta(
                if (validada) "Alerta activa · conexión disponible para compartir ubicación"
                else "Alerta activa · sin internet; la ubicación remota puede tardar"
            )
        }
    }

    private fun actualizarEstadoRuta(mensaje: String) {
        if (!::binding.isInitialized) return
        binding.lblEstadoRuta.text = mensaje
        binding.lblEstadoRuta.contentDescription = mensaje
    }

    // MARK: - Panel inferior

    private fun setupCollectionView() {
        binding.panelContactos.adapter = contactosAdapter
    }

    private fun togglePanel() {
        val desplazamiento = if (isOpen) 200 * resources.displayMetrics.density else 0f
        binding.bottomSheet.animate()
            .translationY(desplazamiento)
            .setDuration(240)
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
            .start()
        isOpen = !isOpen
    }

    private fun funcionBtnConfiguracion() {
        startActivity(Intent(this, ConfiguracionActivity::class.java))
    }

    private fun funcionBtnPerfil() {
        startActivity(
            Intent(this, ProfileActivity::class.java).putExtra(ProfileActivity.EXTRA_USUARIO, usuarioSesion)
        )
    }

    private fun funcionBtnModoDiscreto() {
        val nuevoEstado = !session.modoDiscreto
        session.modoDiscreto = nuevoEstado

        actualizarEstiloModoDiscreto()

        val mensaje = if (nuevoEstado) {
            "Modo Discreto ACTIVADO. La alarma será silenciosa."
        } else {
            "Modo Discreto DESACTIVADO. La alarma emitirá sonido."
        }
        presentarAlerta(mensaje)
    }

    /** Muestra en el botón de perfil la foto del usuario (circular) o el ícono por defecto. */
    private fun mostrarFotoPerfil() {
        val foto = com.cibertec.sentinela.utils.FotoPerfil.cargar(this, session.userId)
        if (foto != null) {
            val circular = androidx.core.graphics.drawable.RoundedBitmapDrawableFactory.create(resources, foto)
                .apply { isCircular = true }
            binding.btnPerfil.imageTintList = null
            binding.btnPerfil.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            binding.btnPerfil.setPadding(0, 0, 0, 0)
            binding.btnPerfil.setImageDrawable(circular)
        } else {
            binding.btnPerfil.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.text_brand))
            binding.btnPerfil.scaleType = android.widget.ImageView.ScaleType.CENTER
            binding.btnPerfil.setImageResource(R.drawable.ic_person)
        }
    }

    private fun actualizarEstiloModoDiscreto() {
        val activo = session.modoDiscreto
        val fondo = if (activo) R.color.theme_primary else R.color.chip_green_bg
        val texto = if (activo) R.color.theme_on_primary else R.color.chip_green_text
        binding.btnModoDiscreto.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, fondo))
        binding.btnModoDiscreto.setTextColor(ContextCompat.getColor(this, texto))
        binding.btnModoDiscreto.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.chip_green_stroke))
        binding.btnModoDiscreto.strokeWidth = if (activo) 0 else resources.getDimensionPixelSize(R.dimen.stroke_hairline)
        binding.btnModoDiscreto.text = if (activo) "Discreto activo" else "Modo Discreto"
    }

    // MARK: - Emergencia

    private fun setupShakeDetector() {
        if (session.shakeEnabled) {
            com.cibertec.sentinela.emergency.ShakeService.iniciar(this)
        }
    }

    private fun funcionBtnDesactivar() {
        val estaActivo = EmergencyManager.isServiceActive.value

        if (!estaActivo) {
            // Verificar si falta el permiso de SMS para enviar por Bitel
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                permisosLauncher.launch(arrayOf(Manifest.permission.SEND_SMS))
            }
            // Activación directa de emergencia
            EmergencyManager.isServiceActive.value = true
            EmergencyService.iniciar(this)
            presentarAlerta("🚨 ¡EMERGENCIA ACTIVADA!\nEnviando ubicación en vivo por WhatsApp y SMS a tus contactos de auxilio.")
        } else {
            // Desactivación protegida por PIN real o PIN de coacción (CU10 / CU11)
            mostrarDialogoPinDesactivacion()
        }
    }

    private fun mostrarDialogoPinDesactivacion() {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            maxLines = 1
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
            hint = "4 dígitos"
            setPadding(48, 32, 48, 32)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("🔒 Desactivar Emergencia")
            .setMessage("Ingresa tu PIN de seguridad para confirmar que te encuentras a salvo:")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Confirmar") { _, _ ->
                val pin = input.text.toString().trim()
                when (pin) {
                    session.pinSeguridad -> {
                        // Desactivación real legítima (CU10)
                        EmergencyManager.isServiceActive.value = false
                        EmergencyService.detener(this)
                        presentarAlerta("✅ Emergencia desactivada con éxito. Te encuentras a salvo.")
                    }
                    session.pinCoaccion -> {
                        // Falsa desactivación bajo coacción (CU11)
                        EmergencyManager.isServiceActive.value = false
                        EmergencyService.activarCoaccion(this)
                        presentarAlerta("Alarma desactivada.")
                    }
                    else -> {
                        presentarAlerta("❌ PIN incorrecto. La alerta de emergencia continúa activa.")
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // MARK: - Trayecto Seguro con Temporizador (CU05)

    private fun mostrarDialogoTrayectoSeguro() {
        val opciones = arrayOf("10 minutos", "15 minutos", "20 minutos", "30 minutos")
        val minutosArray = intArrayOf(10, 15, 20, 30)

        MaterialAlertDialogBuilder(this)
            .setTitle("⏱️ Modo Trayecto Seguro")
            .setMessage("Supervisión inteligente activa: Si el conductor se desvía más de 500m o se detiene más de 3 min, te consultaremos discretamente. Si no confirmas tu estado, se alertará a tus contactos.")
            .setItems(opciones) { _, which ->
                iniciarTimerTrayecto(
                    minutos = minutosArray[which],
                    origen = "Mi ubicación actual",
                    destino = "Destino habitual",
                    medio = "Taxi por aplicativo"
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun iniciarTimerTrayecto(
        minutos: Int,
        origen: String = "Ubicación actual",
        destino: String = "Destino seleccionado",
        medio: String = "Taxi por aplicativo"
    ) {
        timerTrayecto?.cancel()
        timerVerificacion?.cancel()
        dialogoVerificacionActivo?.dismiss()
        dialogoVerificacionActivo = null

        trayectoOrigenActual = origen
        trayectoDestinoActual = destino
        trayectoMedioActual = medio
        binding.lblDestinoTrayecto.text = "Destino: ${RutaSegura.nombreDestinoVisible(destino)}"
        binding.lblDestinoMapa.text = "Destino confirmado · ${RutaSegura.nombreDestinoVisible(destino)}"
        actualizarEstadoRuta("Trayecto activo · monitoreo en curso")
        puntoOrigenTrayecto = RutaSegura.resolverCoordenadaLugar(origen)
        puntoDestinoTrayecto = RutaSegura.resolverCoordenadaLugar(destino)
        ultimaUbicacionTrayecto = null
        segundosInmovil = 0
        desvioAlertaDisparada = false
        inmovilAlertaDisparada = false

        // Si no se había obtenido la ruta vial real por pistas, consultarla para el monitoreo
        if (puntoOrigenTrayecto != null && puntoDestinoTrayecto != null) {
            lifecycleScope.launch {
                val route = RutaSegura.obtenerRutaCallesReal(puntoOrigenTrayecto!!, puntoDestinoTrayecto!!, medio)
                rutaPuntosActuales = route.points
                if (timerTrayecto?.let { binding.bannerTrayecto.visibility == android.view.View.VISIBLE } == true) {
                    actualizarEstadoRuta(
                        if (route.isApproximate) "Ruta aproximada · el servicio de calles no respondió"
                        else "Ruta vial disponible · ${RutaSegura.nombreDestinoVisible(destino)}"
                    )
                }
            }
        }

        binding.bannerTrayecto.visibility = android.view.View.VISIBLE

        val totalMs = minutos * 60 * 1000L
        timerTrayecto = object : android.os.CountDownTimer(totalMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val min = (millisUntilFinished / 1000) / 60
                val sec = (millisUntilFinished / 1000) % 60
                binding.lblTimerTrayecto.text = String.format("Trayecto: %02d:%02d restante", min, sec)

                // D. Detección Inteligente de Desvíos e Inmovilidad (cada 10 segundos)
                val segundosRestantes = millisUntilFinished / 1000
                if (segundosRestantes % 10 == 0L) {
                    verificarSeguridadDesplazamiento()
                }
            }

            override fun onFinish() {
                binding.bannerTrayecto.visibility = android.view.View.GONE
                // Al expirar sin confirmación, activa la emergencia automáticamente
                EmergencyManager.isServiceActive.value = true
                EmergencyService.iniciar(this@HomeActivity)
                presentarAlerta("🚨 ¡TIEMPO DE TRAYECTO EXPIRADO! Alerta de emergencia activada automáticamente.")
            }
        }.start()

        presentarAlerta("⏱️ Trayecto Seguro iniciado ($minutos min). Monitoreo de ruta y desvíos activo.")
    }

    private fun finalizarTrayectoSeguro() {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            maxLines = 1
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
            hint = "4 dígitos"
            setPadding(48, 32, 48, 32)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("🏁 Confirmar Llegada a Salvo")
            .setMessage("Ingresa tu PIN de seguridad para finalizar el trayecto seguro:")
            .setView(input)
            .setPositiveButton("Finalizar") { _, _ ->
                val pin = input.text.toString().trim()
                if (pin == session.pinSeguridad) {
                    timerTrayecto?.cancel()
                    timerVerificacion?.cancel()
                    dialogoVerificacionActivo?.dismiss()
                    dialogoVerificacionActivo = null
                    binding.bannerTrayecto.visibility = android.view.View.GONE
                    limpiarRutaSeguraEnMapa()
                    presentarAlerta("✅ Has llegado a salvo. Monitoreo de trayecto finalizado con éxito.")
                } else if (pin == session.pinCoaccion) {
                    timerTrayecto?.cancel()
                    timerVerificacion?.cancel()
                    dialogoVerificacionActivo?.dismiss()
                    dialogoVerificacionActivo = null
                    binding.bannerTrayecto.visibility = android.view.View.GONE
                    limpiarRutaSeguraEnMapa()
                    EmergencyService.activarCoaccion(this)
                    presentarAlerta("Trayecto finalizado.")
                } else {
                    presentarAlerta("❌ PIN incorrecto. El temporizador continúa activo.")
                }
            }
            .setNegativeButton("Continuar viaje", null)
            .show()
    }

    private fun limpiarRutaSeguraEnMapa() {
        rutaPolylineOverlay?.let { binding.mapView.overlays.remove(it) }
        rutaPolylineOverlay = null
        rutaMarkers.forEach { binding.mapView.overlays.remove(it) }
        rutaMarkers.clear()
        rutaPuntosActuales = emptyList()
        puntoOrigenTrayecto = null
        puntoDestinoTrayecto = null
        routeRequestGeneration++
        binding.lblDestinoMapa.text = "Sin trayecto seleccionado"
        binding.lblDestinoTrayecto.text = "Destino seleccionado"
        actualizarEstadoRuta("Sin trayecto activo")
        binding.mapView.invalidate()
    }

    // MARK: - D. Detección Inteligente de Desvíos e Inmovilidad

    private fun ejecutarVibracionDiscreta() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val patron = longArrayOf(0, 300, 200, 300, 200, 600)
                vibrator?.vibrate(android.os.VibrationEffect.createWaveform(patron, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 300, 200, 300, 200, 600), -1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo emitir vibración discreta: ${e.message}")
        }
    }

    private fun dispararAlertaVerificacionSeguridad(motivo: String) {
        if (dialogoVerificacionActivo?.isShowing == true) return

        ejecutarVibracionDiscreta()

        var segundosRestantes = 45
        timerVerificacion?.cancel()

        val tvMensaje = android.widget.TextView(this).apply {
            textSize = 14f
            setPadding(48, 24, 48, 16)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        }

        fun actualizarTexto() {
            tvMensaje.text = "$motivo\n\n¿Todo en orden?\n\n⏳ Tienes $segundosRestantes segundos para responder antes de que Sentinela emita una alerta de emergencia a tus contactos con tu ubicación en vivo."
        }
        actualizarTexto()

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ Verificación de Seguridad en Ruta")
            .setView(tvMensaje)
            .setCancelable(false)
            .setPositiveButton("✅ Sí, todo en orden") { _, _ ->
                timerVerificacion?.cancel()
                dialogoVerificacionActivo = null
                segundosInmovil = 0
                presentarAlerta("👍 Verificación confirmada. Continúa tu viaje seguro.")
            }
            .setNegativeButton("🚨 ¡Necesito Ayuda!") { _, _ ->
                timerVerificacion?.cancel()
                dialogoVerificacionActivo = null
                EmergencyManager.isServiceActive.value = true
                EmergencyService.iniciar(this)
                presentarAlerta("🚨 Alerta SOS activada inmediatamente.")
            }
            .create()

        dialogoVerificacionActivo = dialog
        dialog.show()

        timerVerificacion = object : android.os.CountDownTimer(45_000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                segundosRestantes = (millisUntilFinished / 1000).toInt()
                actualizarTexto()
            }

            override fun onFinish() {
                if (dialog.isShowing) {
                    dialog.dismiss()
                    dialogoVerificacionActivo = null
                    // Al no haber respuesta, alerta a los contactos de emergencia
                    EmergencyManager.isServiceActive.value = true
                    EmergencyService.iniciar(this@HomeActivity)
                    presentarAlerta("🚨 ¡ALERTA SOS ENVIADA A TUS CONTACTOS! No se recibió confirmación tras anomalía en el viaje.")
                }
            }
        }.start()
    }

    private fun verificarSeguridadDesplazamiento() {
        if (dialogoVerificacionActivo?.isShowing == true) return
        if (!tienePermisoUbicacion()) return

        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc == null) return@addOnSuccessListener
            val currentGeo = GeoPoint(loc.latitude, loc.longitude)

            // 1. Verificación de inmovilidad (> 3 minutos = 180 segundos sin avance)
            val ultima = ultimaUbicacionTrayecto
            if (ultima != null) {
                val distArray = FloatArray(1)
                Location.distanceBetween(ultima.latitude, ultima.longitude, currentGeo.latitude, currentGeo.longitude, distArray)
                val distRecorrida = distArray[0]

                if (distRecorrida < 20.0) { // Menos de 20 metros en 10 seg
                    segundosInmovil += 10
                    if (segundosInmovil >= 180 && !inmovilAlertaDisparada) {
                        inmovilAlertaDisparada = true
                        dispararAlertaVerificacionSeguridad("🛑 Detención prolongada: El vehículo se ha quedado inmóvil más de 3 minutos en un lugar sospechoso.")
                        return@addOnSuccessListener
                    }
                } else {
                    segundosInmovil = 0
                    ultimaUbicacionTrayecto = currentGeo
                }
            } else {
                ultimaUbicacionTrayecto = currentGeo
            }

            // 2. Verificación de desvío (> 500 metros de las pistas o corredor vial)
            val rutaReal = rutaPuntosActuales
            if (rutaReal.size >= 2 && !desvioAlertaDisparada) {
                val desvioM = calcularDistanciaMinimaARutaMetros(currentGeo, rutaReal)
                if (desvioM > 500.0) {
                    desvioAlertaDisparada = true
                    dispararAlertaVerificacionSeguridad("⚠️ Desvío de ruta detectado: El vehículo se ha apartado ${desvioM.toInt()} metros de la ruta habitual hacia $trayectoDestinoActual.")
                }
            } else {
                val pOrig = puntoOrigenTrayecto
                val pDest = puntoDestinoTrayecto
                if (pOrig != null && pDest != null && !desvioAlertaDisparada) {
                    val desvioM = calcularDistanciaACorredorMetros(currentGeo, pOrig, pDest)
                    if (desvioM > 500.0) {
                        desvioAlertaDisparada = true
                        dispararAlertaVerificacionSeguridad("⚠️ Desvío de ruta detectado: El vehículo se ha apartado ${desvioM.toInt()} metros de la ruta habitual hacia $trayectoDestinoActual.")
                    }
                }
            }
        }
    }

    private fun calcularDistanciaACorredorMetros(punto: GeoPoint, inicio: GeoPoint, fin: GeoPoint): Double {
        val latMed = Math.toRadians((inicio.latitude + fin.latitude) / 2.0)
        val mPorGradoLat = 111132.954 - 559.822 * Math.cos(2 * latMed) + 1.175 * Math.cos(4 * latMed)
        val mPorGradoLon = 111412.84 * Math.cos(latMed) - 93.5 * Math.cos(3 * latMed)

        val ax = inicio.longitude * mPorGradoLon
        val ay = inicio.latitude * mPorGradoLat
        val bx = fin.longitude * mPorGradoLon
        val by = fin.latitude * mPorGradoLat
        val px = punto.longitude * mPorGradoLon
        val py = punto.latitude * mPorGradoLat

        val abx = bx - ax
        val aby = by - ay
        val apx = px - ax
        val apy = py - ay

        val abLenSq = abx * abx + aby * aby
        if (abLenSq < 1.0) {
            return Math.sqrt(apx * apx + apy * apy)
        }
        val t = Math.max(0.0, Math.min(1.0, (apx * abx + apy * aby) / abLenSq))
        val projX = ax + t * abx
        val projY = ay + t * aby
        val dx = px - projX
        val dy = py - projY
        return Math.sqrt(dx * dx + dy * dy)
    }

    // MARK: - A. Autodetección de Distrito y B. Trazado de Ruta en Mapa

    private fun calcularDistanciaMinimaARutaMetros(posicion: GeoPoint, ruta: List<GeoPoint>): Double {
        if (ruta.isEmpty()) return 0.0
        if (ruta.size == 1) {
            val res = FloatArray(1)
            Location.distanceBetween(posicion.latitude, posicion.longitude, ruta[0].latitude, ruta[0].longitude, res)
            return res[0].toDouble()
        }
        var minDist = Double.MAX_VALUE
        for (i in 0 until ruta.size - 1) {
            val d = calcularDistanciaACorredorMetros(posicion, ruta[i], ruta[i + 1])
            if (d < minDist) {
                minDist = d
            }
        }
        return minDist
    }

    private fun trazarRutaSemaforoEnMapa(
        origen: String,
        destino: String,
        medio: String,
        nivelRiesgo: String,
        esInviable: Boolean
    ) {
        binding.lblDestinoMapa.text = "Destino confirmado · ${RutaSegura.nombreDestinoVisible(destino)}"
        actualizarEstadoRuta("Consultando calles para ${RutaSegura.nombreDestinoVisible(destino)}…")

        // 1. Limpiar polilíneas y pines previos de rutas anteriores
        rutaPolylineOverlay?.let { binding.mapView.overlays.remove(it) }
        rutaPolylineOverlay = null
        rutaMarkers.forEach { binding.mapView.overlays.remove(it) }
        rutaMarkers.clear()

        // 2. Determinar punto de inicio
        val pOrigen = if (locationOverlay?.myLocation != null && (origen.contains("Lima", ignoreCase = true) || origen.contains("actual", ignoreCase = true))) {
            val distAlGps = FloatArray(1)
            val gps = locationOverlay!!.myLocation
            val coordsDistrito = RutaSegura.resolverCoordenadaLugar(origen)
            Location.distanceBetween(gps.latitude, gps.longitude, coordsDistrito.latitude, coordsDistrito.longitude, distAlGps)
            if (distAlGps[0] < 5000) gps else coordsDistrito
        } else {
            RutaSegura.resolverCoordenadaLugar(origen)
        }

        // 3. Determinar punto de destino
        val pDestino = RutaSegura.resolverCoordenadaLugar(destino)

        // 4. Semáforo de Riesgo (🟢 Verde, 🟡 Amarillo, 🔴 Rojo)
        val (colorHex, etiquetaSemaforo, detalleRiesgo) = RutaSegura.semaforo(nivelRiesgo, esInviable)

        // 5. Pines / Marcadores de inicio y llegada con semáforo y diseño personalizado
        val pinOrigen = Marker(binding.mapView).apply {
            position = pOrigen
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "🚩 Origen: $origen"
            snippet = "Punto de partida del viaje"
            icon = RutaSegura.crearIconoMarcador(this@HomeActivity, "🚩", Color.parseColor("#10B981"))
        }
        val pinDestino = Marker(binding.mapView).apply {
            position = pDestino
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "🏁 Destino: $destino"
            snippet = "$etiquetaSemaforo\n$detalleRiesgo"
            icon = RutaSegura.crearIconoMarcador(this@HomeActivity, "🏁", Color.parseColor(colorHex))
        }
        binding.mapView.overlays.add(pinOrigen)
        binding.mapView.overlays.add(pinDestino)
        rutaMarkers.add(pinOrigen)
        rutaMarkers.add(pinDestino)

        // 6. Consultar la ruta vial real por las pistas de OpenStreetMap (OSRM)
        val generation = ++routeRequestGeneration
        lifecycleScope.launch {
            val route = RutaSegura.obtenerRutaCallesReal(pOrigen, pDestino, medio)
            if (generation != routeRequestGeneration || isFinishing || isDestroyed) return@launch
            val puntosViales = route.points
            rutaPuntosActuales = puntosViales
            actualizarEstadoRuta(
                if (route.isApproximate) {
                    "Ruta aproximada · el servicio de calles no respondió"
                } else {
                    "Ruta vial disponible · destino confirmado"
                }
            )

            val polyline = Polyline(binding.mapView).apply {
                outlinePaint.apply {
                    color = Color.parseColor(colorHex)
                    strokeWidth = 6f * resources.displayMetrics.density
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                    isAntiAlias = true
                }
                setPoints(puntosViales)
                title = "$etiquetaSemaforo: $detalleRiesgo"
            }
            binding.mapView.overlays.add(0, polyline)
            rutaPolylineOverlay = polyline

            // 7. Auto-zoom para encuadrar la ruta vial real completa
            try {
                val boundingBox = BoundingBox.fromGeoPoints(puntosViales)
                binding.mapView.zoomToBoundingBox(boundingBox, true, 120)
            } catch (_: Exception) {
                binding.mapView.controller.setCenter(pOrigen)
                binding.mapView.controller.setZoom(14.0)
            }
            binding.mapView.invalidate()
        }

        SentinelaToast.show(
            this,
            "Ruta segura trazada en el mapa: $etiquetaSemaforo",
            SentinelaToast.Type.SUCCESS,
            SentinelaToast.Length.LONG
        )
    }

    // MARK: - Llamada Simulada (Fake Call) y SOS Perú

    private fun mostrarDialogoLlamadaFalsa() {
        val view = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 24, 50, 10)
        }

        val tvInfo = android.widget.TextView(this).apply {
            text = "Configura tu llamada simulada para un escape discreto ante situaciones incómodas o de riesgo."
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setPadding(0, 0, 0, 16)
        }
        view.addView(tvInfo)

        val tvLabelQuien = android.widget.TextView(this).apply {
            text = "Nombre de quien te llama:"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 8, 0, 4)
        }
        view.addView(tvLabelQuien)

        val txtNombre = android.widget.EditText(this).apply {
            setText("Mamá")
            setSingleLine(true)
            hint = "Ej. Mamá, Trabajo, Papá, Jefe"
        }
        view.addView(txtNombre)

        val tvLabelTiempo = android.widget.TextView(this).apply {
            text = "¿En cuánto tiempo debe sonar?"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 16, 0, 4)
        }
        view.addView(tvLabelTiempo)

        val spinnerTiempo = android.widget.Spinner(this)
        val opcionesTiempo = arrayOf(
            "Al instante (ahora)",
            "En 10 segundos",
            "En 30 segundos",
            "En 1 minuto"
        )
        spinnerTiempo.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opcionesTiempo)
        view.addView(spinnerTiempo)

        MaterialAlertDialogBuilder(this)
            .setTitle("📱 Llamada Simulada (Fake Call)")
            .setView(view)
            .setPositiveButton("Programar Escape") { _, _ ->
                val nombreLlamante = txtNombre.text.toString().trim().ifEmpty { "Mamá" }
                val delaySegundos = when (spinnerTiempo.selectedItemPosition) {
                    0 -> 0
                    1 -> 10
                    2 -> 30
                    3 -> 60
                    else -> 0
                }

                if (delaySegundos == 0) {
                    lanzarLlamadaFalsa(nombreLlamante)
                } else {
                    SentinelaToast.show(
                        this,
                        "Llamada de \"$nombreLlamante\" en $delaySegundos seg. Puedes bloquear la pantalla o minimizar.",
                        SentinelaToast.Type.INFO,
                        SentinelaToast.Length.LONG
                    )

                    Handler(Looper.getMainLooper()).postDelayed({
                        lanzarLlamadaFalsa(nombreLlamante)
                    }, delaySegundos * 1000L)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun lanzarLlamadaFalsa(callerName: String) {
        val intent = Intent(this, FakeCallActivity::class.java).apply {
            putExtra(FakeCallActivity.EXTRA_CALLER_NAME, callerName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun mostrarDialogoSosCentrales() {
        val opciones = arrayOf(
            "👮 105 - Policía Nacional del Perú (PNP)\nEmergencias de seguridad ciudadana y asaltos",
            "🚒 116 - Bomberos Voluntarios del Perú\nIncendios, rescates y accidentes vehiculares",
            "🚑 106 - SAMU - Urgencias Médicas\nAmbulancias y auxilio médico inmediato",
            "💜 Línea 100 - Centro Emergencia Mujer\nViolencia familiar, acoso y contención de crisis",
            "🛡️ 01 318-5050 - Serenazgo de Lima\nPatrullaje y seguridad municipal integrada"
        )
        val numeros = arrayOf("105", "116", "106", "100", "013185050")

        MaterialAlertDialogBuilder(this)
            .setTitle("🚨 Centrales de Emergencia de Perú")
            .setItems(opciones) { _, which ->
                val tel = numeros[which]
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel"))
                startActivity(intent)
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun actualizarUIBotonPrincipal(isServiceActive: Boolean) {
        if (isServiceActive) {
            binding.btnDesactivar.text = "DETENER SOS"
            binding.btnDesactivar.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            binding.btnDesactivar.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sentinela_emergency_dark))
            binding.btnDesactivar.contentDescription =
                "Abrir confirmación para desactivar la alerta activa mediante PIN"
            binding.btnDesactivar.scaleX = 1f
            binding.btnDesactivar.scaleY = 1f
            binding.dotEstadoSeguridad.background = ContextCompat.getDrawable(this, R.drawable.bg_circle_red)
            binding.lblEstadoSeguridad.text = "ALERTA ACTIVA"
            binding.lblEstadoSeguridad.setTextColor(ContextCompat.getColor(this, R.color.sentinela_emergency))
            actualizarEstadoRuta(
                if (binding.lblEstadoRed.text == "Sin internet") {
                    "Alerta activa · sin internet; la ubicación remota puede tardar"
                } else {
                    "Alerta activa · compartiendo ubicación"
                }
            )
        } else {
            binding.btnDesactivar.text = "ACTIVAR SOS"
            binding.btnDesactivar.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            binding.btnDesactivar.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sentinela_emergency))
            binding.btnDesactivar.contentDescription = "Activar alerta SOS de emergencia"
            binding.btnDesactivar.scaleX = 1f
            binding.btnDesactivar.scaleY = 1f
            binding.dotEstadoSeguridad.background = ContextCompat.getDrawable(this, R.drawable.bg_circle_green)
            binding.lblEstadoSeguridad.text = "Sentinela activo"
            binding.lblEstadoSeguridad.setTextColor(ContextCompat.getColor(this, R.color.text_brand))
            if (binding.lblDestinoMapa.text == "Sin trayecto seleccionado") {
                actualizarEstadoRuta("Sin trayecto activo")
            }
        }
    }

    private fun actualizarCardEstadoSeguridad() {
        if (session.esPro()) {
            binding.lblCuotaIa.text = "IA: Ilimitado Pro"
            binding.lblCuotaIa.setTextColor(ContextCompat.getColor(this, R.color.text_brand))
        } else {
            val restantes = (4 - session.consultasIaHoy).coerceAtLeast(0)
            binding.lblCuotaIa.text = "IA: $restantes/4 hoy"
            binding.lblCuotaIa.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun actualizarBadgeHistorial() {
        lifecycleScope.launch {
            val total = SentinelaDatabase.get(this@HomeActivity).historialDao().obtenerTodos().size
            runOnUiThread {
                if (total > 0) {
                    binding.badgeHistorial.visibility = android.view.View.VISIBLE
                    binding.badgeHistorial.text = if (total > 9) "9+" else total.toString()
                } else {
                    binding.badgeHistorial.visibility = android.view.View.GONE
                }
            }
        }
    }

    private fun android.view.View.feedbackHaptico() {
        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    }

    // MARK: - Círculo Familiar y Monitoreo en Vivo (CU Familiar)

    private fun enviarPingFamiliarYActualizarMapa() {
        val userId = session.userId.toLong()
        if (userId == 0L) return

        val batInfo = BatteryMonitor.obtenerInformacionBateria(this)
        val estaActivoEmergencia = EmergencyManager.isServiceActive.value

        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            val lat = loc?.latitude ?: locationOverlay?.myLocation?.latitude
            val lng = loc?.longitude ?: locationOverlay?.myLocation?.longitude

            lifecycleScope.launch {
                realizarPeticion {
                    ApiClient.family.sendPing(
                        FamilyPingRequest(
                            userId = userId,
                            latitude = lat,
                            longitude = lng,
                            batteryLevel = batInfo.level,
                            isCharging = batInfo.isCharging,
                            isEmergencyActive = estaActivoEmergencia,
                            statusZone = if (estaActivoEmergencia) "🚨 SOS ACTIVO" else "🟢 En Movimiento"
                        )
                    )
                }

                realizarPeticion { ApiClient.family.getMembers(userId) }
                    .onSuccess { members ->
                        mostrarFamiliaresEnMapa(members, userId)
                    }
            }
        }
    }

    private fun mostrarFamiliaresEnMapa(members: List<FamilyMemberDto>, miUserId: Long) {
        for (m in familyMarkers) {
            binding.mapView.overlays.remove(m)
        }
        familyMarkers.clear()

        for (familiar in members) {
            if (familiar.userId == miUserId) continue
            val lat = familiar.latitude ?: continue
            val lng = familiar.longitude ?: continue

            val punto = GeoPoint(lat, lng)
            val nombre = familiar.fullName ?: "Familiar"
            val bat = familiar.batteryLevel ?: 100
            val charging = familiar.isCharging == true
            val emergency = familiar.isEmergencyActive == true

            val marker = Marker(binding.mapView).apply {
                position = punto
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "👨‍👩‍👧‍👦 $nombre"
                snippet = "🔋 Batería: $bat%${if (charging) " (Cargando)" else ""}\nEstado: ${familiar.currentStatus ?: "Conectado"}\nTel: ${familiar.phone ?: "No registrado"}"
                icon = crearIconoFamiliar(nombre, bat, charging, emergency)
            }
            binding.mapView.overlays.add(marker)
            familyMarkers.add(marker)
        }
        binding.mapView.invalidate()
    }

    private fun crearIconoFamiliar(nombre: String, bateria: Int, isCharging: Boolean, isEmergency: Boolean): android.graphics.drawable.Drawable {
        val density = resources.displayMetrics.density
        val width = (64 * density).toInt()
        val height = (74 * density).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Sombra
        paint.color = Color.argb(60, 0, 0, 0)
        canvas.drawCircle(width / 2f, 24 * density + 2 * density, 20 * density, paint)

        // Círculo del avatar (Rojo si hay emergencia, Verde o Azul en normal)
        paint.color = if (isEmergency) Color.parseColor("#EF4444") else Color.parseColor("#0F766E")
        paint.style = Paint.Style.FILL
        canvas.drawCircle(width / 2f, 24 * density, 20 * density, paint)

        // Borde blanco
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * density
        canvas.drawCircle(width / 2f, 24 * density, 20 * density, paint)

        // Inicial del nombre en el centro
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 18 * density
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        val initial = nombre.firstOrNull()?.uppercase() ?: "F"
        val yPos = (24 * density - (paint.descent() + paint.ascent()) / 2)
        canvas.drawText(initial, width / 2f, yPos, paint)

        // Badge de batería debajo del avatar
        val batText = "${if (isCharging) "⚡" else ""}$bateria%"
        val rectF = android.graphics.RectF(
            width / 2f - 24 * density,
            46 * density,
            width / 2f + 24 * density,
            64 * density
        )
        paint.color = when {
            isEmergency -> Color.parseColor("#DC2626")
            bateria <= 15 -> Color.parseColor("#EF4444")
            else -> Color.parseColor("#064E3B")
        }
        canvas.drawRoundRect(rectF, 8 * density, 8 * density, paint)

        // Texto de batería
        paint.color = Color.WHITE
        paint.textSize = 10 * density
        val batY = (55 * density - (paint.descent() + paint.ascent()) / 2)
        canvas.drawText(batText, width / 2f, batY, paint)

        return BitmapDrawable(resources, bitmap)
    }

    // MARK: - Gestión de contactos

    private fun cargarContactosLocales() {
        lifecycleScope.launch {
            contactosGuardados = contactoDao.obtenerTodos()
            contactosAdapter.notifyDataSetChanged()

            // Sincronizar con el backend para que siempre refleje los contactos en el servidor
            val userId = session.userId
            val token = session.userToken
            if (userId != 0 && token != null) {
                realizarPeticion { ApiClient.contacts.obtenerContactosPorUsuario(userId, ApiClient.bearer(token)) }
                    .onSuccess { remotos ->
                        if (remotos.isNotEmpty()) {
                            contactoDao.vaciar()
                            for (r in remotos) {
                                val tel = r.phoneNumber.orEmpty().trim()
                                val id = r.id.toString()
                                if (tel.isNotEmpty()) {
                                    contactoDao.insertar(
                                        ContactoEntity(id = id, nombre = r.fullName ?: "Contacto", telefono = tel)
                                    )
                                }
                            }
                            contactosGuardados = contactoDao.obtenerTodos()
                            contactosAdapter.notifyDataSetChanged()
                        }
                    }
                    .onFailure { Log.w(TAG, "No se pudieron obtener contactos remotos: $it") }
            }
        }
    }

    private fun funcionBtnAgregarContacto() {
        if (!session.puedeAgregarContacto(contactosGuardados.size)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("⭐ Límite de Contactos (Plan Free)")
                .setMessage("El Plan Sentinela Free te permite registrar hasta 2 contactos de auxilio.\n\nActualiza a Sentinela Pro para tener contactos ILIMITADOS y alertas automáticas por SMS a toda tu red de auxilio.")
                .setPositiveButton("Ver Planes Pro") { _, _ ->
                    startActivity(Intent(this, SuscripcionActivity::class.java))
                }
                .setNegativeButton("Entendido", null)
                .show()
            return
        }
        ContactsBottomSheet().show(supportFragmentManager, "ContactsViewController")
    }

    private fun contactoSeleccionado(nombreCompleto: String, telefono: String, id: String, email: String?) {
        val emailValido = email?.trim()?.takeIf { it.isNotEmpty() } ?: "unknown@sentinela.local"

        lifecycleScope.launch {
            // Evitar duplicados por teléfono o id
            if (contactoDao.contar(telefono, id) > 0) {
                presentarAlerta("Este contacto ya está en tu red de seguridad.")
                return@launch
            }

            contactoDao.insertar(ContactoEntity(id = id, nombre = nombreCompleto, telefono = telefono))
            cargarContactosLocales()

            // Sincronizar con backend (BD) para mayor control
            val userId = session.userId
            val token = session.userToken
            if (userId == 0 || token == null) {
                Log.w(TAG, "No se pudo sincronizar contacto: faltan userId/token")
                return@launch
            }
            val req = CreateContactRequest(
                userId = userId,
                fullName = nombreCompleto,
                phoneNumber = telefono.ifEmpty { null },
                email = emailValido,
                enableWhatsapp = true
            )
            realizarPeticion { ApiClient.contacts.crearContacto(ApiClient.bearer(token), req) }
                .onSuccess {
                    Log.d(TAG, "Contacto sincronizado en BD con ID: ${it.id}")
                    // Recargar para sincronizar IDs
                    cargarContactosLocales()
                }
                .onFailure { Log.e(TAG, "Error sincronizando contacto: $it") }
        }
    }

    private fun confirmarQuitarContacto(contacto: ContactoEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Quitar contacto")
            .setMessage("¿Deseas quitar a ${contacto.nombre ?: "este contacto"} de tu red de seguridad?")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    if (eliminarContactoRemoto(contacto)) {
                        contactoDao.eliminar(contacto)
                        cargarContactosLocales()
                    } else {
                        presentarAlerta("No se pudo eliminar en el servidor. Inténtalo nuevamente.")
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
            .getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(ContextCompat.getColor(this, R.color.system_red))
    }

    /** Busca en el backend el contacto por ID o por teléfono y lo elimina. */
    private suspend fun eliminarContactoRemoto(contacto: ContactoEntity): Boolean {
        val userId = session.userId
        val token = session.userToken
        if (userId == 0 || token == null) return false
        val bearer = ApiClient.bearer(token)

        val contactosBD = realizarPeticion { ApiClient.contacts.obtenerContactosPorUsuario(userId, bearer) }
            .getOrNull() ?: return false

        val telefono = contacto.telefono.orEmpty()
        val objetivo = contactosBD.firstOrNull {
            it.id.toString() == contacto.id ||
            normalizarNumero(it.phoneNumber.orEmpty()) == normalizarNumero(telefono)
        } ?: return true // Si ya no existe en el backend, permite limpiar local

        return realizarPeticion { ApiClient.contacts.eliminarContacto(objetivo.id, bearer) }
            .getOrNull()?.isSuccessful == true
    }

    private fun normalizarNumero(numero: String): String {
        return numero.filter { it == '+' || it.isDigit() }
    }

    // MARK: - Lista horizontal de contactos (equivalente al UICollectionView)

    private inner class ContactosGuardadosAdapter :
        RecyclerView.Adapter<ContactosGuardadosAdapter.ContactoViewHolder>() {

        inner class ContactoViewHolder(val celda: ItemContactoGuardadoBinding) :
            RecyclerView.ViewHolder(celda.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactoViewHolder {
            return ContactoViewHolder(
                ItemContactoGuardadoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }

        override fun getItemCount() = contactosGuardados.size

        override fun onBindViewHolder(holder: ContactoViewHolder, position: Int) {
            val contacto = contactosGuardados[position]

            // Extraer iniciales del nombre completo
            val palabras = contacto.nombre.orEmpty().trim().split(" ").filter { it.isNotEmpty() }
            val iniciales = when {
                // Si hay nombre y apellido, la primera letra de cada uno
                palabras.size >= 2 -> palabras[0].take(1) + palabras[1].take(1)
                // Si solo hay una palabra, las dos primeras letras
                palabras.isNotEmpty() -> palabras[0].take(2)
                else -> ""
            }

            // Mostrar solo el primer nombre debajo del círculo
            holder.celda.lblNombre.text = palabras.firstOrNull() ?: contacto.nombre.orEmpty()
            holder.celda.lblLetrsNombre.text = iniciales.uppercase()
            holder.celda.root.setOnClickListener { confirmarQuitarContacto(contacto) }
        }
    }
}
