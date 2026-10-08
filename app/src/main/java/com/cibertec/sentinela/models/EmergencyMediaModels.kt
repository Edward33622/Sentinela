package com.cibertec.sentinela.models

data class CreateEmergencyMediaRequest(
    val emergencyEventId: Int,
    val mediaType: String,   // PHOTO, VIDEO, AUDIO
    val storageUrl: String
)

data class EmergencyMediaResponse(
    val id: Int,
    val emergencyEventId: Int,
    val mediaType: String,
    val storageUrl: String
)
