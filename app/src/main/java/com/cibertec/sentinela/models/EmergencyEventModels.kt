package com.cibertec.sentinela.models

import com.google.gson.annotations.SerializedName

// Request (lo que se envía)
data class CreateEmergencyEventRequest(
    val userId: Int,
    val status: String = "ACTIVE",
    val type: String = "TAP",
    val description: String = "Emergencia activada por usuario",
    val latitude: Double? = null,
    val longitude: Double? = null
)

// Response (lo que se recibe)
data class EmergencyEventResponse(
    val id: Int,
    val userId: Int,
    val status: String?,
    @SerializedName("activatedAt") val createdAt: String?,
    @SerializedName("closedAt") val resolvedAt: String?,
    val type: String?,
    val description: String?,
    val latitude: Double?,
    val longitude: Double?
)

// Modelo resumido que solo usa los campos necesarios en la vista de historial
data class EmergencyEventSummary(
    val id: Int,
    val userId: Int?,
    val status: String?,
    val activatedAt: String?,
    val closedAt: String?
)
