package com.cibertec.sentinela.models

data class CreateEmergencyLocationRequest(
    val emergencyEventId: Int,
    val latitude: Double,
    val longitude: Double
)

data class EmergencyLocationResponse(
    val id: Int,
    val emergencyEventId: Int,
    val latitude: Double,
    val longitude: Double,
    val capturedAt: String?
)
