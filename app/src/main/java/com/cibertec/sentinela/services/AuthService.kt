package com.cibertec.sentinela.services

import com.cibertec.sentinela.models.LoginRequest
import com.cibertec.sentinela.models.LoginResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthService {

    @POST("auth/login")
    suspend fun login(@Body req: LoginRequest): LoginResponse
}
