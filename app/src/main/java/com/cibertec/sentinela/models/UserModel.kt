package com.cibertec.sentinela.models

import java.io.Serializable

data class UserDetail(
    val id: Int,
    val fullName: String,
    val email: String,
    val phone: String?,
    val status: String?,
    val pinsConfigured: Boolean = false
) : Serializable

data class SavePinsRequest(
    val pinSeguridad: String,
    val pinCoaccion: String
)

data class UpdateUserRequest(
    val fullName: String,
    val email: String,
    val phone: String
)
