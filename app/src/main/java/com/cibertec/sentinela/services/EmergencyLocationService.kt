package com.cibertec.sentinela.services

import com.cibertec.sentinela.models.CreateEmergencyLocationRequest
import com.cibertec.sentinela.models.EmergencyLocationResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface EmergencyLocationService {

    // CREATE LOCATION
    @POST("api/emergency-locations")
    suspend fun crearUbicacionEmergencia(
        @Header("Authorization") token: String,
        @Body datos: CreateEmergencyLocationRequest
    ): EmergencyLocationResponse

    @GET("emergency-locations/{eventId}")
    suspend fun obtenerRutaDelEvento(
        @Path("eventId") eventId: Int,
        @Header("Authorization") token: String
    ): List<EmergencyLocationResponse>

    // GET LOCATION BY ID
    @GET("api/emergency-locations/{id}")
    suspend fun obtenerUbicacionPorId(
        @Path("id") id: Int,
        @Header("Authorization") token: String
    ): EmergencyLocationResponse

    // GET ALL LOCATIONS
    @GET("api/emergency-locations")
    suspend fun obtenerTodasLasUbicaciones(
        @Header("Authorization") token: String
    ): List<EmergencyLocationResponse>

    // GET LOCATIONS BY EVENT
    @GET("api/emergency-locations/event/{eventId}")
    suspend fun obtenerUbicacionesPorEvento(
        @Path("eventId") eventId: Int,
        @Header("Authorization") token: String
    ): List<EmergencyLocationResponse>

    // DELETE LOCATION
    @DELETE("api/emergency-locations/{id}")
    suspend fun eliminarUbicacion(
        @Path("id") id: Int,
        @Header("Authorization") token: String
    ): Response<Unit>
}
