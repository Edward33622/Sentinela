package com.cibertec.sentinela.models

import java.io.Serializable

data class UserDetail(
    val id: Int,
    val fullName: String,
    val email: String,
    val phone: String?,
    val status: String?
) : Serializable

data class UpdateUserRequest(
    val fullName: String,
    val email: String,
    val phone: String
)
