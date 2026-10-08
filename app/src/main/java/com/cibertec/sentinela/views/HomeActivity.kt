package com.cibertec.sentinela.views

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
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
import com.cibertec.sentinela.services.ApiClient
import com.cibertec.sentinela.services.realizarPeticion
import com.cibertec.sentinela.utils.SessionManager
import com.cibertec.sentinela.utils.presentarAlerta
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
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

    var usuarioSesion: UserDetail? = null

    private var isOpen = true
    private var contactosGuardados: List<ContactoEntity> = emptyList()
    private val contactosAdapter = ContactosGuardadosAdapter()
    private var locationOverlay: MyLocationNewOverlay? = null

    private val permisosLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            mostrarUbicacionDelUsuario()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)

        @Suppress("DEPRECATION")
        usuarioSesion = intent.getSerializableExtra(EXTRA_USUARIO) as? UserDetail

        setupMap()
        setupCollectionView()
        actualizarEstiloModoDiscreto()
        cargarContactosLocales()

        binding.panelHandle.setOnClickListener { togglePanel() }
        binding.btnConfiguracion.setOnClickListener { funcionBtnConfiguracion() }
        binding.btnPerfil.setOnClickListener { funcionBtnPerfil() }
        binding.btnModoDiscreto.setOnClickListener { funcionBtnModoDiscreto() }
        binding.btnDesactivar.setOnClickListener { funcionBtnDesactivar() }
        binding.btnAgregarContacto.setOnClickListener { funcionBtnAgregarContacto() }

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
    }

    override fun onPause() {
        locationOverlay?.disableMyLocation()
        binding.mapView.onPause()
        super.onPause()
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
            val permisos = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permisos.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permisosLauncher.launch(permisos.toTypedArray())
        }
    }

    private fun tienePermisoUbicacion(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun mostrarUbicacionDelUsuario() {
        if (!tienePermisoUbicacion() || locationOverlay != null) return

        val overlay = MyLocationNewOverlay(GpsMyLocationProvider(this), binding.mapView)
        overlay.enableMyLocation()
        overlay.runOnFirstFix {
            runOnUiThread {
                overlay.myLocation?.let { binding.mapView.controller.animateTo(it) }
            }
        }
        binding.mapView.overlays.add(overlay)
        locationOverlay = overlay
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

    private fun funcionBtnDesactivar() {
        val activar = !EmergencyManager.isServiceActive.value
        EmergencyManager.isServiceActive.value = activar

        if (activar) {
            // Alarma + evento en el backend + alerta a contactos + rastreo de ubicación
            EmergencyService.iniciar(this)
        } else {
            // Detiene alarma y rastreo, y cierra el evento en el backend
            EmergencyService.detener(this)
        }
    }

    private fun actualizarUIBotonPrincipal(isServiceActive: Boolean) {
        if (isServiceActive) {
            binding.btnDesactivar.text = "DESACTIVAR"
            binding.btnDesactivar.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.system_red))
        } else {
            binding.btnDesactivar.text = "ACTIVAR"
            binding.btnDesactivar.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sentinela_green))
        }
    }

    // MARK: - Gestión de contactos

    private fun cargarContactosLocales() {
        lifecycleScope.launch {
            contactosGuardados = contactoDao.obtenerTodos()
            contactosAdapter.notifyDataSetChanged()
        }
    }

    private fun funcionBtnAgregarContacto() {
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
                .onSuccess { Log.d(TAG, "Contacto sincronizado en BD") }
                .onFailure { Log.e(TAG, "Error sincronizando contacto: $it") }
        }
    }

    private fun confirmarQuitarContacto(contacto: ContactoEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Quitar contacto")
            .setMessage("¿Deseas quitar a ${contacto.nombre ?: "este contacto"} de tu red de seguridad?")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    if (eliminarContactoRemoto(contacto.telefono.orEmpty())) {
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

    /** Busca en el backend el contacto por teléfono y lo elimina por ID. */
    private suspend fun eliminarContactoRemoto(telefono: String): Boolean {
        val userId = session.userId
        val token = session.userToken
        if (userId == 0 || token == null) return false
        val bearer = ApiClient.bearer(token)

        val contactosBD = realizarPeticion { ApiClient.contacts.obtenerContactosPorUsuario(userId, bearer) }
            .getOrNull() ?: return false

        val objetivo = contactosBD.firstOrNull {
            normalizarNumero(it.phoneNumber.orEmpty()) == normalizarNumero(telefono)
        } ?: return false

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
