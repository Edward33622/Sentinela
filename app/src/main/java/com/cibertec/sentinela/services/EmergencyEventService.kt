package com.cibertec.sentinela.services

import com.cibertec.sentinela.models.CreateEmergencyEventRequest
import com.cibertec.sentinela.models.EmergencyEventResponse
import com.cibertec.sentinela.models.EmergencyEventSummary
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface EmergencyEventService {

    // CREATE EVENT
    @POST("api/emergency-events")
    suspend fun crearEvento(
        @Header("Authorization") token: String,
        @Body datos: CreateEmergencyEventRequest
    ): EmergencyEventResponse

    // GET EVENT BY ID
    @GET("api/emergency-events/{id}")
    suspend fun obtenerEventoPorId(
        @Path("id") id: Int,
        @Header("Authorization") token: String
    ): EmergencyEventResponse

    // GET ALL EVENTS
    @GET("api/emergency-events")
    suspend fun obtenerTodosLosEventos(
        @Header("Authorization") token: String
    ): List<EmergencyEventResponse>

    // GET EVENTS BY USER
    @GET("api/emergency-events/user/{userId}")
    suspend fun obtenerEventosPorUsuario(
        @Path("userId") userId: Int,
        @Header("Authorization") token: String
    ): List<EmergencyEventResponse>

    // GET EVENTS BY USER (Resumen)
    @GET("api/emergency-events/user/{userId}")
    suspend fun obtenerEventosPorUsuarioResumen(
        @Path("userId") userId: Int,
        @Header("Authorization") token: String
    ): List<EmergencyEventSummary>

    // GET ACTIVE EVENTS
    @GET("api/emergency-events/active")
    suspend fun obtenerEventosActivos(
        @Header("Authorization") token: String
    ): List<EmergencyEventResponse>

    // GET RESOLVED EVENTS
    @GET("api/emergency-events/resolved")
    suspend fun obtenerEventosResueltos(
        @Header("Authorization") token: String
    ): List<EmergencyEventResponse>

    // RESOLVE EVENT
    @PATCH("api/emergency-events/{id}/resolve")
    suspend fun resolverEvento(
        @Path("id") id: Int,
        @Header("Authorization") token: String
    ): EmergencyEventResponse
}
