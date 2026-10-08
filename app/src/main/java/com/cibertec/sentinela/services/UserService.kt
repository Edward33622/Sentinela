package com.cibertec.sentinela.services

import com.cibertec.sentinela.models.RegisterRequest
import com.cibertec.sentinela.models.UpdateUserRequest
import com.cibertec.sentinela.models.UserDetail
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface UserService {

    @GET("api/users/{id}")
    suspend fun obtenerUsuario(
        @Path("id") id: Int,
        @Header("Authorization") token: String
    ): UserDetail

    @POST("api/users")
    suspend fun registrarUsuario(@Body datos: RegisterRequest): UserDetail

    @PUT("api/users/{id}")
    suspend fun actualizarUsuario(
        @Path("id") id: Int,
        @Header("Authorization") token: String,
        @Body datos: UpdateUserRequest
    ): UserDetail
}
