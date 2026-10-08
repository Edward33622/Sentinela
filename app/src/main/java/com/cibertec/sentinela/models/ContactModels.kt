package com.cibertec.sentinela.models

data class ContactResponse(
    val id: Int,
    val userId: Int,
    val fullName: String,
    val phoneNumber: String?,
    val email: String,
    val enableWhatsapp: Boolean?,
    val emergencyContact: Boolean?
)

data class CreateContactRequest(
    val userId: Int,
    val fullName: String,
    val phoneNumber: String?,
    val email: String,
    val enableWhatsapp: Boolean?
)

/** Contacto leído de la agenda del teléfono (equivalente a CNContact). */
data class ContactoTelefono(
    val id: String,
    val nombre: String,
    val telefono: String
) {
    val primerNombre: String
        get() = nombre.trim().split(" ").firstOrNull { it.isNotEmpty() } ?: nombre
}
