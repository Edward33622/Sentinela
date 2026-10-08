package com.cibertec.sentinela.models

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val token: String,
    val userId: Int,
    val email: String
)

data class RegisterRequest(
    val fullName: String,
    val email: String,
    val phone: String,
    val password: String
)
