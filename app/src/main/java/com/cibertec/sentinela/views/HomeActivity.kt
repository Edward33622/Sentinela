package com.cibertec.sentinela.views

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
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
import com.cibertec.sentinela.utils.SessionManager
import com.cibertec.sentinela.utils.presentarAlerta
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
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

        // Coordenadas céntricas de los distritos de Lima y Callao para autodetección por GPS y trazado vial
        val COORDENADAS_DISTRITOS = mapOf(
            "San Miguel (Lima)" to GeoPoint(-12.0772, -77.0863),
            "Miraflores (Lima)" to GeoPoint(-12.1211, -77.0297),
            "San Isidro (Lima)" to GeoPoint(-12.0984, -77.0353),
            "Santiago de Surco (Lima)" to GeoPoint(-12.1456, -76.9944),
            "San Borja (Lima)" to GeoPoint(-12.0911, -77.0019),
            "Cercado de Lima" to GeoPoint(-12.0464, -77.0428),
            "Los Olivos (Lima)" to GeoPoint(-11.9932, -77.0716),
            "Jesús María (Lima)" to GeoPoint(-12.0722, -77.0494),
            "Magdalena del Mar (Lima)" to GeoPoint(-12.0906, -77.0706),
            "Pueblo Libre (Lima)" to GeoPoint(-12.0769, -77.0658),
            "Lince (Lima)" to GeoPoint(-12.0831, -77.0344),
            "Surquillo (Lima)" to GeoPoint(-12.1122, -77.0142),
            "Barranco (Lima)" to GeoPoint(-12.1469, -77.0206),
            "Chorrillos (Lima)" to GeoPoint(-12.1764, -77.0147),
            "La Molina (Lima)" to GeoPoint(-12.0886, -76.9458),
            "Ate (Lima)" to GeoPoint(-12.0289, -76.9189),
            "San Juan de Lurigancho (Lima)" to GeoPoint(-11.9792, -77.0056),
            "San Juan de Miraflores (Lima)" to GeoPoint(-12.1625, -76.9633),
            "Villa El Salvador (Lima)" to GeoPoint(-12.2136, -76.9367),
            "Villa María del Triunfo (Lima)" to GeoPoint(-12.1611, -76.9389),
            "Comas (Lima)" to GeoPoint(-11.9369, -77.0519),
            "Independencia (Lima)" to GeoPoint(-11.9908, -77.0539),
            "San Martín de Porres (Lima)" to GeoPoint(-12.0167, -77.0833),
            "Carabayllo (Lima)" to GeoPoint(-11.8986, -77.0347),
            "Puente Piedra (Lima)" to GeoPoint(-11.8672, -77.0758),
            "Rímac (Lima)" to GeoPoint(-12.0328, -77.0272),
            "Breña (Lima)" to GeoPoint(-12.0578, -77.0514),
            "La Victoria (Lima)" to GeoPoint(-12.0658, -77.0144),
            "El Agustino (Lima)" to GeoPoint(-12.0517, -77.0017),
            "Santa Anita (Lima)" to GeoPoint(-12.0469, -76.9694),
            "Lurín (Lima)" to GeoPoint(-12.2756, -76.8711),
            "Pachacámac (Lima)" to GeoPoint(-12.2300, -76.8600),
            "Chaclacayo (Lima)" to GeoPoint(-11.9806, -76.7681),
            "Chosica / Lurigancho (Lima)" to GeoPoint(-11.9392, -76.7083),
            "Bellavista (Callao)" to GeoPoint(-12.0608, -77.1264),
            "Callao (Cercado)" to GeoPoint(-12.0565, -77.1181),
            "La Perla (Callao)" to GeoPoint(-12.0717, -77.1172),
            "La Punta (Callao)" to GeoPoint(-12.0728, -77.1611),
            "Carmen de la Legua (Callao)" to GeoPoint(-12.0442, -77.0944),
            "Ventanilla (Callao)" to GeoPoint(-11.8667, -77.1333),
            "Mi Perú (Callao)" to GeoPoint(-11.8542, -77.1189),
            "Ancón (Lima)" to GeoPoint(-11.7733, -77.1761),
            "Pucusana (Lima)" to GeoPoint(-12.4833, -76.7833),
            "Punta Hermosa (Lima)" to GeoPoint(-12.3333, -76.8167),
            "Punta Negra (Lima)" to GeoPoint(-12.3667, -76.7833),
            "San Bartolo (Lima)" to GeoPoint(-12.3833, -76.7667),
            "Santa María del Mar (Lima)" to GeoPoint(-12.4000, -76.7667),
            "Santa Rosa (Lima)" to GeoPoint(-11.8000, -77.1667),
            "Cieneguilla (Lima)" to GeoPoint(-12.0833, -76.7667),
            "San Luis (Lima)" to GeoPoint(-12.0750, -77.0000)
        )
    }

    private lateinit var binding: ActivityHomeBinding
    private lateinit var session: SessionManager
    private val contactoDao by lazy { SentinelaDatabase.get(this).contactoDao() }

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
    private val familyMarkers: MutableList<Marker> = mutableListOf()
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
    private var pulseAnimator: ObjectAnimator? = null

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private val permisosLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultado ->
            val concedido = resultado[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                            resultado[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (concedido) {
                mostrarUbicacionDelUsuario()
                centrarEnUbicacionActual(animar = true)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)

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
        binding.btnAsistenteIa.setOnClickListener { it.feedbackHaptico(); mostrarDialogoAsistenteIa() }
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
        handleEmergencyShortcut(intent)
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        locationOverlay?.enableMyLocation()
        if (session.shakeEnabled) {
            shakeDetector?.start(sensorManager)
        }
        actualizarCardEstadoSeguridad()
        actualizarBadgeHistorial()
        enviarPingFamiliarYActualizarMapa()
    }

    override fun onPause() {
        shakeDetector?.stop(sensorManager)
        locationOverlay?.disableMyLocation()
        binding.mapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        detenerAnimacionPulso()
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
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(-12.0464, -77.0428))
        }

        if (tienePermisoUbicacion()) {
            mostrarUbicacionDelUsuario()
        } else {
            solicitarPermisosUbicacion()
        }
    }

    private fun solicitarPermisosUbicacion() {
        val permisos = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permisos.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permisosLauncher.launch(permisos.toTypedArray())
    }

    private fun tienePermisoUbicacion(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun mostrarUbicacionDelUsuario() {
        if (!tienePermisoUbicacion()) return

        if (locationOverlay == null) {
            val provider = GpsMyLocationProvider(this).apply {
                // Agregar fuente de red para fijar ubicación al instante en interiores o emuladores
                addLocationSource(LocationManager.NETWORK_PROVIDER)
            }
            val overlay = MyLocationNewOverlay(provider, binding.mapView)
            overlay.enableMyLocation()
            overlay.setDrawAccuracyEnabled(true)
            overlay.runOnFirstFix {
                runOnUiThread {
                    overlay.myLocation?.let {
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
            solicitarPermisosUbicacion()
            return
        }

        // 1. Si el overlay ya tiene fix de posición, usarlo directamente
        val myLoc = locationOverlay?.myLocation
        if (myLoc != null) {
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

    // MARK: - Panel inferior

    private fun setupCollectionView() {
        binding.panelContactos.adapter = contactosAdapter
    }

    private fun togglePanel() {
        val desplazamiento = if (isOpen) 200 * resources.displayMetrics.density else 0f
        binding.bottomSheet.animate().translationY(desplazamiento).setDuration(300).start()
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

    private fun actualizarEstiloModoDiscreto() {
        binding.btnModoDiscreto.alpha = if (session.modoDiscreto) 1.0f else 0.5f
    }

    // MARK: - Emergencia

    private fun setupShakeDetector() {
        shakeDetector = com.cibertec.sentinela.emergency.ShakeDetector {
            if (!EmergencyManager.isServiceActive.value && session.shakeEnabled) {
                runOnUiThread {
                    EmergencyManager.isServiceActive.value = true
                    EmergencyService.iniciar(this)
                    presentarAlerta("🚨 ¡ALERTA ACTIVADA POR SACUDIDA (Shake Detection)!")
                }
            }
        }
    }

    private fun funcionBtnDesactivar() {
        val estaActivo = EmergencyManager.isServiceActive.value

        if (!estaActivo) {
            // Activación directa de emergencia
            EmergencyManager.isServiceActive.value = true
            EmergencyService.iniciar(this)
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
        puntoOrigenTrayecto = resolverCoordenadaLugar(origen)
        puntoDestinoTrayecto = resolverCoordenadaLugar(destino)
        ultimaUbicacionTrayecto = null
        segundosInmovil = 0
        desvioAlertaDisparada = false
        inmovilAlertaDisparada = false

        // Si no se había obtenido la ruta vial real por pistas, consultarla para el monitoreo
        if (puntoOrigenTrayecto != null && puntoDestinoTrayecto != null) {
            lifecycleScope.launch {
                rutaPuntosActuales = obtenerRutaCallesReal(puntoOrigenTrayecto!!, puntoDestinoTrayecto!!, medio)
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
                    presentarAlerta("✅ Has llegado a salvo. Monitoreo de trayecto finalizado con éxito.")
                } else if (pin == session.pinCoaccion) {
                    timerTrayecto?.cancel()
                    timerVerificacion?.cancel()
                    dialogoVerificacionActivo?.dismiss()
                    dialogoVerificacionActivo = null
                    binding.bannerTrayecto.visibility = android.view.View.GONE
                    EmergencyService.activarCoaccion(this)
                    presentarAlerta("Trayecto finalizado.")
                } else {
                    presentarAlerta("❌ PIN incorrecto. El temporizador continúa activo.")
                }
            }
            .setNegativeButton("Continuar viaje", null)
            .show()
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

    private fun buscarDistritoMasCercano(lat: Double, lng: Double): Pair<String?, Float> {
        var masCercano: String? = null
        var menorDistancia = Float.MAX_VALUE
        val results = FloatArray(1)

        for ((distrito, geo) in COORDENADAS_DISTRITOS) {
            Location.distanceBetween(lat, lng, geo.latitude, geo.longitude, results)
            val d = results[0]
            if (d < menorDistancia) {
                menorDistancia = d
                masCercano = distrito
            }
        }
        return Pair(masCercano, menorDistancia)
    }

    private fun resolverCoordenadaLugar(lugar: String): GeoPoint {
        val lugarUpper = lugar.uppercase().trim()

        // 1. Coincidencia directa en distritos
        COORDENADAS_DISTRITOS[lugar]?.let { return it }

        // 2. Coincidencia parcial con distritos
        for ((distrito, geo) in COORDENADAS_DISTRITOS) {
            val nombreBase = distrito.substringBefore(" (").uppercase()
            if (lugarUpper.contains(nombreBase)) {
                return geo
            }
        }

        // 3. Atajos conocidos
        when {
            lugarUpper.contains("CIBERTEC") -> return GeoPoint(-12.1221, -77.0305) // Cibertec Miraflores
            lugarUpper.contains("CASA") -> return GeoPoint(-12.0984, -77.0353) // San Isidro
            lugarUpper.contains("TRABAJO") || lugarUpper.contains("OFICINA") -> return GeoPoint(-12.0911, -77.0019) // San Borja
            lugarUpper.contains("COMERCIAL") || lugarUpper.contains("MALL") -> return GeoPoint(-12.0860, -77.0030)
            // Provincias principales del Perú
            lugarUpper.contains("TUMBES") -> return GeoPoint(-3.5669, -80.4515)
            lugarUpper.contains("PIURA") -> return GeoPoint(-5.1945, -80.6328)
            lugarUpper.contains("CHICLAYO") || lugarUpper.contains("LAMBAYEQUE") -> return GeoPoint(-6.7714, -79.8409)
            lugarUpper.contains("TRUJILLO") || lugarUpper.contains("LIBERTAD") -> return GeoPoint(-8.1117, -79.0287)
            lugarUpper.contains("CHIMBOTE") || lugarUpper.contains("ANCASH") -> return GeoPoint(-9.0744, -78.5936)
            lugarUpper.contains("HUARAZ") -> return GeoPoint(-9.5261, -77.5289)
            lugarUpper.contains("ICA") || lugarUpper.contains("CHINCHA") || lugarUpper.contains("PISCO") -> return GeoPoint(-14.0678, -75.7286)
            lugarUpper.contains("AREQUIPA") -> return GeoPoint(-16.4090, -71.5375)
            lugarUpper.contains("CUSCO") || lugarUpper.contains("CUZCO") -> return GeoPoint(-13.5319, -71.9675)
            lugarUpper.contains("TACNA") -> return GeoPoint(-18.0146, -70.2536)
            lugarUpper.contains("PUNO") || lugarUpper.contains("JULIACA") -> return GeoPoint(-15.8422, -70.0199)
            lugarUpper.contains("HUANCAYO") || lugarUpper.contains("JUNIN") -> return GeoPoint(-12.0651, -75.2049)
            lugarUpper.contains("AYACUCHO") -> return GeoPoint(-13.1588, -74.2239)
            lugarUpper.contains("TARAPOTO") || lugarUpper.contains("SAN MARTIN") -> return GeoPoint(-6.4854, -76.3686)
            lugarUpper.contains("IQUITOS") || lugarUpper.contains("LORETO") -> return GeoPoint(-3.7491, -73.2538)
        }

        // 4. Default: Centro de Lima
        return GeoPoint(-12.0464, -77.0428)
    }

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

    private suspend fun obtenerRutaCallesReal(inicio: GeoPoint, fin: GeoPoint, medio: String): List<GeoPoint> = withContext(Dispatchers.IO) {
        val modo = if (medio.contains("pie", ignoreCase = true)) "foot" else "driving"
        val urlStr = "https://router.project-osrm.org/route/v1/$modo/${inicio.longitude},${inicio.latitude};${fin.longitude},${fin.latitude}?overview=full&geometries=geojson"

        try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "SentinelaApp/1.0 (Android; OpenStreetMap)")

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()

                val root = JSONObject(sb.toString())
                val routes = root.optJSONArray("routes")
                if (routes != null && routes.length() > 0) {
                    val geometry = routes.getJSONObject(0).optJSONObject("geometry")
                    val coords = geometry?.optJSONArray("coordinates")
                    if (coords != null && coords.length() > 0) {
                        val puntosViales = mutableListOf<GeoPoint>()
                        for (i in 0 until coords.length()) {
                            val par = coords.getJSONArray(i)
                            val lon = par.getDouble(0)
                            val lat = par.getDouble(1)
                            puntosViales.add(GeoPoint(lat, lon))
                        }
                        if (puntosViales.size >= 2) {
                            return@withContext puntosViales
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo consultar enrutador OSRM en tiempo real: ${e.message}")
        }

        return@withContext generarPuntosCorredor(inicio, fin)
    }

    private fun generarPuntosCorredor(inicio: GeoPoint, fin: GeoPoint): List<GeoPoint> {
        val puntos = mutableListOf<GeoPoint>()
        puntos.add(inicio)

        val results = FloatArray(1)
        Location.distanceBetween(inicio.latitude, inicio.longitude, fin.latitude, fin.longitude, results)
        val distMetros = results[0]

        if (distMetros > 80_000) {
            val pasos = 8
            for (i in 1 until pasos) {
                val frac = i.toDouble() / pasos
                val lat = inicio.latitude + (fin.latitude - inicio.latitude) * frac
                val lon = inicio.longitude + (fin.longitude - inicio.longitude) * frac
                puntos.add(GeoPoint(lat, lon))
            }
        } else {
            val pasos = 5
            for (i in 1 until pasos) {
                val frac = i.toDouble() / pasos
                val lat = inicio.latitude + (fin.latitude - inicio.latitude) * frac
                val lon = inicio.longitude + (fin.longitude - inicio.longitude) * frac
                puntos.add(GeoPoint(lat, lon))
            }
        }

        puntos.add(fin)
        return puntos
    }

    private fun trazarRutaSemaforoEnMapa(
        origen: String,
        destino: String,
        medio: String,
        nivelRiesgo: String,
        esInviable: Boolean
    ) {
        // 1. Limpiar polilíneas y pines previos de rutas anteriores
        rutaPolylineOverlay?.let { binding.mapView.overlays.remove(it) }
        rutaPolylineOverlay = null
        rutaMarkers.forEach { binding.mapView.overlays.remove(it) }
        rutaMarkers.clear()

        // 2. Determinar punto de inicio
        val pOrigen = if (locationOverlay?.myLocation != null && (origen.contains("Lima", ignoreCase = true) || origen.contains("actual", ignoreCase = true))) {
            val distAlGps = FloatArray(1)
            val gps = locationOverlay!!.myLocation
            val coordsDistrito = resolverCoordenadaLugar(origen)
            Location.distanceBetween(gps.latitude, gps.longitude, coordsDistrito.latitude, coordsDistrito.longitude, distAlGps)
            if (distAlGps[0] < 5000) gps else coordsDistrito
        } else {
            resolverCoordenadaLugar(origen)
        }

        // 3. Determinar punto de destino
        val pDestino = resolverCoordenadaLugar(destino)

        // 4. Semáforo de Riesgo (🟢 Verde, 🟡 Amarillo, 🔴 Rojo)
        val (colorHex, etiquetaSemaforo, detalleRiesgo) = when {
            esInviable || nivelRiesgo.contains("ALTO", ignoreCase = true) ||
            nivelRiesgo.contains("EXTREMO", ignoreCase = true) ||
            nivelRiesgo.contains("ROJO", ignoreCase = true) -> {
                Triple("#EF4444", "🔴 Tramo de Alto Riesgo", "Zonas desoladas no recomendadas de noche o inviables")
            }
            nivelRiesgo.contains("MODERADO", ignoreCase = true) ||
            nivelRiesgo.contains("MEDIO", ignoreCase = true) ||
            nivelRiesgo.contains("AMARILLO", ignoreCase = true) -> {
                Triple("#F59E0B", "🟡 Tramo con Precaución", "Congestión vehicular y paraderos con arrebatos")
            }
            else -> {
                Triple("#10B981", "🟢 Vía Segura Iluminada", "Avenidas principales con cámaras e iluminación activa")
            }
        }

        // 5. Pines / Marcadores de inicio y llegada con semáforo y diseño personalizado
        val pinOrigen = Marker(binding.mapView).apply {
            position = pOrigen
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "🚩 Origen: $origen"
            snippet = "Punto de partida del viaje"
            icon = crearIconoMarcador("🚩", Color.parseColor("#10B981"))
        }
        val pinDestino = Marker(binding.mapView).apply {
            position = pDestino
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "🏁 Destino: $destino"
            snippet = "$etiquetaSemaforo\n$detalleRiesgo"
            icon = crearIconoMarcador("🏁", Color.parseColor(colorHex))
        }
        binding.mapView.overlays.add(pinOrigen)
        binding.mapView.overlays.add(pinDestino)
        rutaMarkers.add(pinOrigen)
        rutaMarkers.add(pinDestino)

        // 6. Consultar la ruta vial real por las pistas de OpenStreetMap (OSRM)
        lifecycleScope.launch {
            val puntosViales = obtenerRutaCallesReal(pOrigen, pDestino, medio)
            rutaPuntosActuales = puntosViales

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

        android.widget.Toast.makeText(
            this,
            "🚦 Ruta segura por pistas trazada en el mapa: $etiquetaSemaforo",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }

    // MARK: - Asistente IA de Evaluación de Riesgo (CU15)

    private fun mostrarDialogoAsistenteIa() {
        if (!session.puedeUsarIa()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("🤖 Límite Diario de IA Alcanzado (4/4)")
                .setMessage("Has completado tus 4 análisis de ruta gratuitos de hoy con Sentinela Free.\n\nTu cuota diaria se reinicia automáticamente cada medianoche (00:00 hrs). Actualiza a Sentinela Pro para disfrutar de consultas ilimitadas con Gemini AI y el Modo Acompañante Seguro.")
                .setPositiveButton("Ver Planes Pro") { _, _ ->
                    startActivity(Intent(this, SuscripcionActivity::class.java))
                }
                .setNegativeButton("Entendido", null)
                .show()
            return
        }

        val distritosLimaCallao = arrayOf(
            "San Miguel (Lima)", "Miraflores (Lima)", "San Isidro (Lima)", "Santiago de Surco (Lima)",
            "San Borja (Lima)", "Cercado de Lima", "Los Olivos (Lima)", "Jesús María (Lima)",
            "Magdalena del Mar (Lima)", "Pueblo Libre (Lima)", "Lince (Lima)", "Surquillo (Lima)",
            "Barranco (Lima)", "Chorrillos (Lima)", "La Molina (Lima)", "Ate (Lima)",
            "San Juan de Lurigancho (Lima)", "San Juan de Miraflores (Lima)", "Villa El Salvador (Lima)",
            "Villa María del Triunfo (Lima)", "Comas (Lima)", "Independencia (Lima)",
            "San Martín de Porres (Lima)", "Carabayllo (Lima)", "Puente Piedra (Lima)",
            "Rímac (Lima)", "Breña (Lima)", "La Victoria (Lima)", "El Agustino (Lima)",
            "Santa Anita (Lima)", "Lurín (Lima)", "Pachacámac (Lima)", "Chaclacayo (Lima)",
            "Chosica / Lurigancho (Lima)", "Bellavista (Callao)", "Callao (Cercado)",
            "La Perla (Callao)", "La Punta (Callao)", "Carmen de la Legua (Callao)",
            "Ventanilla (Callao)", "Mi Perú (Callao)", "Ancón (Lima)", "Pucusana (Lima)",
            "Punta Hermosa (Lima)", "Punta Negra (Lima)", "San Bartolo (Lima)",
            "Santa María del Mar (Lima)", "Santa Rosa (Lima)", "Cieneguilla (Lima)",
            "San Luis (Lima)", "📍 Otra provincia o región..."
        )

        val contenedor = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
        }

        val cuotaTexto = if (session.esPro()) {
            "✨ Plan Pro: Consultas ilimitadas con Gemini"
        } else {
            "⚡ Plan Free: Te quedan ${session.consultasIaRestantesDia()}/4 consultas hoy"
        }

        val tvInfoCobertura = android.widget.TextView(this).apply {
            text = "📍 Cobertura activa: Lima Metropolitana y Callao\n$cuotaTexto"
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.sentinela_green))
            setPadding(0, 0, 0, 8)
        }
        contenedor.addView(tvInfoCobertura)

        val tvLabelOrigen = android.widget.TextView(this).apply {
            text = "Punto de partida (Distrito de Lima / Callao):"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 4, 0, 4)
        }
        contenedor.addView(tvLabelOrigen)

        val spinnerOrigen = android.widget.Spinner(this)
        spinnerOrigen.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, distritosLimaCallao)
        contenedor.addView(spinnerOrigen)

        // A. Autodetección del Distrito por GPS
        fun aplicarAutodeteccionGps(lat: Double, lng: Double) {
            val (distritoCercano, distanciaM) = buscarDistritoMasCercano(lat, lng)
            if (distritoCercano != null && distanciaM < 35_000) {
                val pos = distritosLimaCallao.indexOf(distritoCercano)
                if (pos >= 0) {
                    spinnerOrigen.setSelection(pos)
                    tvInfoCobertura.text = "📍 GPS Autodetectado: $distritoCercano\n$cuotaTexto"
                    tvInfoCobertura.setTextColor(Color.parseColor("#10B981"))
                }
            }
        }

        // Intento 1: Posición inmediata del overlay en pantalla
        val locActual = locationOverlay?.myLocation
        if (locActual != null) {
            aplicarAutodeteccionGps(locActual.latitude, locActual.longitude)
        } else if (tienePermisoUbicacion()) {
            // Intento 2: FusedLocationProviderClient
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    aplicarAutodeteccionGps(loc.latitude, loc.longitude)
                }
            }
        }

        val txtOrigenPersonalizado = android.widget.EditText(this).apply {
            hint = "Escribe la provincia (ej. Tumbes, Cusco, Arequipa)"
            visibility = android.view.View.GONE
            setSingleLine(true)
        }
        contenedor.addView(txtOrigenPersonalizado)

        spinnerOrigen.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (position == distritosLimaCallao.size - 1) {
                    txtOrigenPersonalizado.visibility = android.view.View.VISIBLE
                } else {
                    txtOrigenPersonalizado.visibility = android.view.View.GONE
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        val tvLabelDestino = android.widget.TextView(this).apply {
            text = "¿A dónde te diriges? (Destino):"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 14, 0, 2)
        }
        contenedor.addView(tvLabelDestino)

        val txtDestino = android.widget.AutoCompleteTextView(this).apply {
            val adapter = android.widget.ArrayAdapter(this@HomeActivity, android.R.layout.simple_dropdown_item_1line, distritosLimaCallao)
            setAdapter(adapter)
            threshold = 1
            hint = "Ej. Miraflores, Av. Javier Prado, etc."
            setSingleLine(true)
        }
        contenedor.addView(txtDestino)

        // Atajos rápidos de destino
        val layoutChips = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 6)
        }
        val atajos = listOf("🎓 Cibertec", "🏠 Casa", "💼 Trabajo", "🏬 C. Comercial")
        for (atajo in atajos) {
            val btnChip = com.google.android.material.button.MaterialButton(
                this, null, com.google.android.material.R.attr.borderlessButtonStyle
            ).apply {
                text = atajo
                textSize = 10f
                setPadding(8, 0, 8, 0)
                setOnClickListener {
                    val lugar = when (atajo) {
                        "🎓 Cibertec" -> "Instituto Cibertec (Sede Miraflores)"
                        "🏠 Casa" -> "Casa (Destino habitual)"
                        "💼 Trabajo" -> "Centro Laboral / Oficina"
                        else -> "Centro Comercial"
                    }
                    txtDestino.setText(lugar)
                }
            }
            layoutChips.addView(btnChip)
        }
        contenedor.addView(layoutChips)

        val tvLabelMedio = android.widget.TextView(this).apply {
            text = "Medio de transporte:"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 8, 0, 4)
        }
        contenedor.addView(tvLabelMedio)

        val spinnerMedio = android.widget.Spinner(this)
        val opcionesMedio = arrayOf("A pie", "Taxi por aplicativo", "Transporte público", "Vehículo particular", "Bus interprovincial / Vuelo")
        spinnerMedio.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opcionesMedio)
        contenedor.addView(spinnerMedio)

        MaterialAlertDialogBuilder(this)
            .setTitle("🤖 Sentinela AI Copilot")
            .setMessage("Consulta con Google Gemini la evaluación de riesgo, trazado vial y semáforo de seguridad:")
            .setView(contenedor)
            .setPositiveButton("Analizar con IA") { _, _ ->
                val seleccionado = spinnerOrigen.selectedItem.toString()
                val origen = if (seleccionado.startsWith("📍 Otra provincia")) {
                    txtOrigenPersonalizado.text.toString().trim().ifEmpty { "Provincia fuera de Lima" }
                } else {
                    seleccionado
                }
                val destino = txtDestino.text.toString().trim()
                val medio = spinnerMedio.selectedItem.toString()
                validarYProcesarRuta(origen, if (destino.isEmpty()) "Destino habitual" else destino, medio)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // MARK: - C. Sugerencia Automática de Modo de Transporte según la Hora

    private fun validarYProcesarRuta(origen: String, destino: String, medio: String) {
        val cal = java.util.Calendar.getInstance()
        val hora = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val minuto = cal.get(java.util.Calendar.MINUTE)
        // Pasadas las 10:30 PM (22:30) hasta las 5:30 AM
        val esNochePeligrosa = (hora > 22 || (hora == 22 && minuto >= 30) || hora < 5 || (hora == 5 && minuto <= 30))

        if (medio == "A pie" && esNochePeligrosa) {
            val horaFormateada = String.format("%02d:%02d", hora, minuto)
            MaterialAlertDialogBuilder(this)
                .setTitle("⚠️ Sugerencia Automática de Seguridad")
                .setMessage("Son las $horaFormateada hrs.\n\n⚠️ Por tu seguridad, a esta hora te recomendamos cambiar el medio a 'Taxi por aplicativo' para evitar caminar por paraderos oscuros y zonas solitarias en $origen.\n\n¿Deseas cambiar el medio de transporte o continuar a pie?")
                .setPositiveButton("🚕 Cambiar a Taxi por aplicativo") { _, _ ->
                    ejecutarValidacionDestinoYProcesar(origen, destino, "Taxi por aplicativo")
                }
                .setNegativeButton("Continuar A pie") { _, _ ->
                    ejecutarValidacionDestinoYProcesar(origen, destino, "A pie")
                }
                .setNeutralButton("Cancelar", null)
                .show()
            return
        }

        ejecutarValidacionDestinoYProcesar(origen, destino, medio)
    }

    private fun ejecutarValidacionDestinoYProcesar(origen: String, destino: String, medio: String) {
        val provinciasFuera = listOf(
            "TUMBES", "PIURA", "LAMBAYEQUE", "CHICLAYO", "TRUJILLO", "LA LIBERTAD",
            "CHIMBOTE", "HUARAZ", "ANCASH", "ICA", "CHINCHA", "PISCO", "NAZCA",
            "AREQUIPA", "MOQUEGUA", "ILO", "TACNA", "CAJAMARCA", "AMAZONAS",
            "TARAPOTO", "LORETO", "IQUITOS", "UCAYALI", "PUCALLPA", "MADRE DE DIOS",
            "CUSCO", "CUZCO", "PUNO", "JULIACA", "HUANUCO", "PASCO", "JUNIN", "HUANCAYO",
            "AYACUCHO", "APURIMAC"
        )

        val origenUpper = origen.uppercase()
        val destinoUpper = destino.uppercase()

        val origenEsProvincia = provinciasFuera.any { origenUpper.contains(it) } || origen.contains("fuera de Lima", ignoreCase = true)
        val destinoEsProvincia = provinciasFuera.any { destinoUpper.contains(it) }

        if (origenEsProvincia) {
            MaterialAlertDialogBuilder(this)
                .setTitle("⚠️ Origen Fuera de Rango Metropolitano")
                .setMessage("El punto de partida '$origen' se encuentra fuera de la cobertura urbana activa de Sentinela (Lima Metropolitana y Callao).\n\n¿Deseas consultar el análisis de riesgo de la IA de todas formas?")
                .setPositiveButton("Continuar con IA") { _, _ ->
                    procesarConsultaIa(origen, destino, medio)
                }
                .setNegativeButton("Cambiar origen") { _, _ ->
                    mostrarDialogoAsistenteIa()
                }
                .show()
            return
        }

        if (destinoEsProvincia && medio == "A pie") {
            MaterialAlertDialogBuilder(this)
                .setTitle("🚫 Destino Fuera de Rango e Inviable a Pie")
                .setMessage("El destino '$destino' se encuentra a cientos de kilómetros en otra provincia.\n\nEs físicamente imposible y de riesgo extremo viajar a pie. Consultaremos a Google Gemini para darte los datos oficiales de distancia y alternativas seguras.")
                .setPositiveButton("Consultar Informe IA") { _, _ ->
                    procesarConsultaIa(origen, destino, medio)
                }
                .setNegativeButton("Cambiar Destino") { _, _ ->
                    mostrarDialogoAsistenteIa()
                }
                .show()
            return
        }

        procesarConsultaIa(origen, destino, medio)
    }

    private fun procesarConsultaIa(origen: String, destino: String, medio: String) {
        val horaActual = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val minutoActual = java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE)
        val horaTexto = String.format("%02d:%02d", horaActual, minutoActual)

        val dialogoCarga = MaterialAlertDialogBuilder(this)
            .setTitle("🤖 Consultando Google Gemini AI...")
            .setMessage("Evaluando coherencia geográfica, horario ($horaTexto) y ruta hacia $destino...")
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            val req = com.cibertec.sentinela.models.AnalyzeRouteRequest(
                origin = origen,
                destination = destino,
                transport = medio,
                hour = horaTexto
            )

            val resultado = realizarPeticion { ApiClient.ai.analizarRuta(req) }
            dialogoCarga.dismiss()

            resultado.onSuccess { res ->
                session.registrarConsultaIa()
                actualizarCardEstadoSeguridad()
                val nivelRiesgo = res.riskLevel ?: "🟡 RIESGO MODERADO"
                val titulo = "✨ Sentinela IA con Google Gemini ($nivelRiesgo)"
                val textoAnalisis = res.analysis ?: "Sin información disponible."
                val minutos = res.suggestedMinutes
                val esInviable = res.isFeasible == false || minutos > 300

                // B. Trazado del Semáforo de Riesgo sobre pistas reales en el Mapa de Sentinela
                trazarRutaSemaforoEnMapa(origen, destino, medio, nivelRiesgo, esInviable)

                val tiempoFormateado = when {
                    minutos >= 1440 -> "${minutos / 1440} días y ${(minutos % 1440) / 60} horas (~${minutos} min)"
                    minutos >= 60 -> "${minutos / 60} h ${minutos % 60} min (${minutos} min)"
                    else -> "$minutos minutos"
                }

                val distanciaTexto = if (!res.estimatedDistance.isNullOrBlank()) "\n📏 Distancia calculada: ${res.estimatedDistance}" else ""
                val tiempoTexto = "\n⏱️ Tiempo estimado: $tiempoFormateado"
                val badgeIa = if (res.poweredByGemini) "\n\n🟢 Análisis oficial impulsado por Google Gemini AI (Modelo Flash)\n🚦 Trazado vial seguro sobre avenidas reales y semáforo de riesgo activos en el mapa." else "\n\n⚠️ Modo Local Preventivo (Sin conexión con Gemini AI)"

                val mensajeCompleto = "$textoAnalisis\n$distanciaTexto$tiempoTexto$badgeIa"

                val dialog = MaterialAlertDialogBuilder(this@HomeActivity)
                    .setTitle(titulo)
                    .setMessage(mensajeCompleto)

                if (esInviable) {
                    dialog.setPositiveButton("Entendido (Ruta no iniciable)", null)
                } else {
                    dialog.setPositiveButton("Iniciar Trayecto Seguro ($minutos min)") { _, _ ->
                        iniciarTimerTrayecto(minutos, origen, destino, medio)
                    }
                    dialog.setNeutralButton("📲 Avisar por WhatsApp") { _, _ ->
                        compartirRutaPorWhatsApp(origen, destino, medio, tiempoFormateado, nivelRiesgo)
                    }
                    dialog.setNegativeButton("Ver en Mapa", null)
                }
                dialog.show()
            }.onFailure { err ->
                MaterialAlertDialogBuilder(this@HomeActivity)
                    .setTitle("⚠️ Error al Conectar con Sentinela IA")
                    .setMessage("No se pudo obtener la evaluación en tiempo real de Google Gemini (${err.localizedMessage ?: "Tiempo de espera agotado"}).\n\nPor favor verifica tu conexión a internet o el servicio de Sentinela y vuelve a intentar.")
                    .setPositiveButton("Reintentar") { _, _ ->
                        procesarConsultaIa(origen, destino, medio)
                    }
                    .setNegativeButton("Cerrar", null)
                    .show()
            }
        }
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
                    android.widget.Toast.makeText(
                        this,
                        "⏳ Llamada de \"$nombreLlamante\" en $delaySegundos seg. Puedes bloquear la pantalla o minimizar.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()

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
            binding.btnDesactivar.text = "DESACTIVAR"
            binding.btnDesactivar.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sentinela_red))
            iniciarAnimacionPulso(urgente = true)
            binding.dotEstadoSeguridad.background = ContextCompat.getDrawable(this, R.drawable.bg_circle_red)
            binding.lblEstadoSeguridad.text = "🚨 ALERTA ACTIVA"
            binding.lblEstadoSeguridad.setTextColor(Color.parseColor("#DC2626"))
        } else {
            binding.btnDesactivar.text = "ACTIVAR"
            binding.btnDesactivar.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sentinela_green))
            iniciarAnimacionPulso(urgente = false)
            binding.dotEstadoSeguridad.background = ContextCompat.getDrawable(this, R.drawable.bg_circle_green)
            binding.lblEstadoSeguridad.text = "🛡️ Sentinela Activo"
            binding.lblEstadoSeguridad.setTextColor(Color.parseColor("#064E3B"))
        }
    }

    private fun iniciarAnimacionPulso(urgente: Boolean) {
        pulseAnimator?.cancel()
        val escalaMax = if (urgente) 1.08f else 1.03f
        val duracion = if (urgente) 450L else 1400L

        val scaleX = PropertyValuesHolder.ofFloat(android.view.View.SCALE_X, 1.0f, escalaMax)
        val scaleY = PropertyValuesHolder.ofFloat(android.view.View.SCALE_Y, 1.0f, escalaMax)

        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(binding.btnDesactivar, scaleX, scaleY).apply {
            duration = duracion
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun detenerAnimacionPulso() {
        pulseAnimator?.cancel()
        binding.btnDesactivar.scaleX = 1.0f
        binding.btnDesactivar.scaleY = 1.0f
    }

    private fun actualizarCardEstadoSeguridad() {
        if (session.esPro()) {
            binding.lblCuotaIa.text = "✨ IA: Ilimitado Pro"
            binding.lblCuotaIa.setTextColor(Color.parseColor("#7C3AED"))
        } else {
            val restantes = (4 - session.consultasIaHoy).coerceAtLeast(0)
            binding.lblCuotaIa.text = "🤖 IA: $restantes/4 hoy"
            binding.lblCuotaIa.setTextColor(Color.parseColor("#475569"))
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

    private fun crearIconoMarcador(icono: String, colorFondo: Int): android.graphics.drawable.Drawable {
        val density = resources.displayMetrics.density
        val size = (42 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, (size * 1.25f).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Sombra suave inferior
        paint.color = Color.argb(50, 0, 0, 0)
        canvas.drawCircle(size / 2f, size / 2f + 2 * density, size / 2f - 2 * density, paint)

        // Círculo principal con color de riesgo
        paint.color = colorFondo
        paint.style = Paint.Style.FILL
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3 * density, paint)

        // Borde blanco moderno
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * density
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3 * density, paint)

        // Puntero / triángulo inferior hacia la calle
        paint.style = Paint.Style.FILL
        paint.color = colorFondo
        val path = Path().apply {
            moveTo(size / 2f - 6 * density, size / 2f + 11 * density)
            lineTo(size / 2f + 6 * density, size / 2f + 11 * density)
            lineTo(size / 2f, size * 1.2f)
            close()
        }
        canvas.drawPath(path, paint)

        // Emoji o símbolo interior
        paint.color = Color.WHITE
        paint.textSize = 17 * density
        paint.textAlign = Paint.Align.CENTER
        val yPos = (size / 2f - (paint.descent() + paint.ascent()) / 2)
        canvas.drawText(icono, size / 2f, yPos, paint)

        return BitmapDrawable(resources, bitmap)
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
                        var huboCambios = false
                        for (r in remotos) {
                            val tel = r.phoneNumber.orEmpty()
                            val id = r.id.toString()
                            if (contactoDao.contar(tel, id) == 0) {
                                contactoDao.insertar(
                                    ContactoEntity(id = id, nombre = r.fullName, telefono = tel)
                                )
                                huboCambios = true
                            }
                        }
                        if (huboCambios) {
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
                .setMessage("El Plan Sentinela Free te permite registrar hasta 2 contactos de auxilio.\n\nActualiza a Sentinela Pro para tener contactos ILIMITADOS y alertas SMS de respaldo con Twilio.")
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
