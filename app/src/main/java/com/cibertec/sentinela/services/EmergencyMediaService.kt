package com.cibertec.sentinela.services

import com.cibertec.sentinela.models.CreateEmergencyMediaRequest
import com.cibertec.sentinela.models.EmergencyMediaResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface EmergencyMediaService {

    // CREATE MEDIA
    @POST("api/emergency-media")
    suspend fun crearMediaEmergencia(
        @Header("Authorization") token: String,
        @Body datos: CreateEmergencyMediaRequest
    ): EmergencyMediaResponse

    // GET MEDIA BY ID
    @GET("api/emergency-media/{id}")
    suspend fun obtenerMediaPorId(
        @Path("id") id: Int,
        @Header("Authorization") token: String
    ): EmergencyMediaResponse

    // GET MEDIA BY EVENT
    @GET("api/emergency-media/event/{eventId}")
    suspend fun obtenerMediaPorEvento(
        @Path("eventId") eventId: Int,
        @Header("Authorization") token: String
    ): List<EmergencyMediaResponse>

    // GET MEDIA BY TYPE
    @GET("api/emergency-media/type/{mediaType}")
    suspend fun obtenerMediaPorTipo(
        @Path("mediaType") mediaType: String,
        @Header("Authorization") token: String
    ): List<EmergencyMediaResponse>

    // DELETE MEDIA
    @DELETE("api/emergency-media/{id}")
    suspend fun eliminarMedia(
        @Path("id") id: Int,
        @Header("Authorization") token: String
    ): Response<Unit>
}
