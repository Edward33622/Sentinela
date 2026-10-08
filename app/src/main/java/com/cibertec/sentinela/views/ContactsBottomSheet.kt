package com.cibertec.sentinela.views

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.cibertec.sentinela.R
import com.cibertec.sentinela.databinding.FragmentContactsBinding
import com.cibertec.sentinela.databinding.ItemContactoBinding
import com.cibertec.sentinela.models.ContactoTelefono
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hoja "Mis Contactos" (equivalente a ContactsViewController presentado como pageSheet).
 * Devuelve el contacto elegido al Home mediante setFragmentResult.
 */
class ContactsBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val REQUEST_KEY = "contactoSeleccionado"
        const val KEY_NOMBRE = "nombre"
        const val KEY_TELEFONO = "telefono"
        const val KEY_ID = "id"
        const val KEY_EMAIL = "email"
    }

    private var _binding: FragmentContactsBinding? = null
    private val binding get() = _binding!!

    private var contactos: List<ContactoTelefono> = emptyList()
    private val adapter = ContactosAdapter()

    private val permisoLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido) fetchContacts() else mostrarAlertaPermisos()
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentContactsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.contactosTable.adapter = adapter
        requestContactsAccess()
    }

    override fun onStart() {
        super.onStart()
        // La hoja ocupa toda la altura disponible
        val hoja = (dialog as? BottomSheetDialog)
            ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        hoja.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        BottomSheetBehavior.from(hoja).apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // MARK: - Selección (la funcionalidad de agregar)

    private fun didSelectRow(contacto: ContactoTelefono) {
        val alerta = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Agregar Contacto")
            .setMessage("¿Deseas agregar a ${contacto.primerNombre} a tu red de seguridad?")
            .setPositiveButton("Sí, agregar") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val email = withContext(Dispatchers.IO) { obtenerEmail(requireContext(), contacto.id) }
                    // 1. Avisamos al HomeActivity
                    setFragmentResult(
                        REQUEST_KEY,
                        bundleOf(
                            KEY_NOMBRE to contacto.nombre,
                            KEY_TELEFONO to contacto.telefono,
                            KEY_ID to contacto.id,
                            KEY_EMAIL to email
                        )
                    )
                    // 2. Cerramos la pantalla
                    dismiss()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()

        // Color verde para la acción positiva (Estilo Sentinela)
        alerta.getButton(AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(ContextCompat.getColor(requireContext(), R.color.sentinela_green))
    }

    // MARK: - Acceso a Contactos

    private fun requestContactsAccess() {
        val estado = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
        if (estado == PackageManager.PERMISSION_GRANTED) {
            fetchContacts()
        } else {
            permisoLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun fetchContacts() {
        viewLifecycleOwner.lifecycleScope.launch {
            contactos = withContext(Dispatchers.IO) { leerContactos(requireContext().applicationContext) }
            adapter.notifyDataSetChanged()
        }
    }

    /** Solo contactos con número de teléfono; se toma el primer número de cada uno. */
    private fun leerContactos(context: Context): List<ContactoTelefono> {
        val resultado = LinkedHashMap<String, ContactoTelefono>()
        val columnas = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            columnas, null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                if (resultado.containsKey(id)) continue
                resultado[id] = ContactoTelefono(
                    id = id,
                    nombre = cursor.getString(1).orEmpty(),
                    telefono = cursor.getString(2).orEmpty()
                )
            }
        }
        return resultado.values.toList()
    }

    private fun obtenerEmail(context: Context, contactId: String): String? {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
            arrayOf(contactId), null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    private fun mostrarAlertaPermisos() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Permiso de Contactos")
            .setMessage("Necesitamos acceso a tus contactos para añadirlos a tu red de seguridad. Puedes permitirlo en Ajustes.")
            .setPositiveButton("Abrir Ajustes") { _, _ ->
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", requireContext().packageName, null)
                    )
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // MARK: - Lista

    private inner class ContactosAdapter : RecyclerView.Adapter<ContactosAdapter.ContactoViewHolder>() {

        inner class ContactoViewHolder(val celda: ItemContactoBinding) : RecyclerView.ViewHolder(celda.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactoViewHolder {
            return ContactoViewHolder(ItemContactoBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun getItemCount() = contactos.size

        override fun onBindViewHolder(holder: ContactoViewHolder, position: Int) {
            val contacto = contactos[position]

            // 1. Iniciales (nombre + apellido)
            val palabras = contacto.nombre.trim().split(" ").filter { it.isNotEmpty() }
            val letras = palabras.take(2).joinToString("") { it.take(1) }.uppercase()
            holder.celda.lblIniciales.text = letras.ifEmpty { "?" }

            // 2. Nombre
            holder.celda.lblNombre.text = contacto.nombre

            // 3. Teléfono
            holder.celda.lblTelefono.text = contacto.telefono.ifEmpty { "Sin número disponible" }

            holder.celda.root.setOnClickListener { didSelectRow(contacto) }
        }
    }
}
