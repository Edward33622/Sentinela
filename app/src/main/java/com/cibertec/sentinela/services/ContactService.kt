package com.cibertec.sentinela.services

import com.cibertec.sentinela.models.ContactResponse
import com.cibertec.sentinela.models.CreateContactRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ContactService {

    // CREATE CONTACT
    @POST("api/contacts")
    suspend fun crearContacto(
        @Header("Authorization") token: String,
        @Body datos: CreateContactRequest
    ): ContactResponse

    // GET CONTACT BY ID
    @GET("api/contacts/{id}")
    suspend fun obtenerContactoPorId(
        @Path("id") id: Int,
        @Header("Authorization") token: String
    ): ContactResponse

    // GET ALL CONTACTS
    @GET("api/contacts")
    suspend fun obtenerTodosLosContactos(
        @Header("Authorization") token: String
    ): List<ContactResponse>

    // GET CONTACTS BY USER
    @GET("api/contacts/user/{userId}")
    suspend fun obtenerContactosPorUsuario(
        @Path("userId") userId: Int,
        @Header("Authorization") token: String
    ): List<ContactResponse>

    // DELETE CONTACT
    @DELETE("api/contacts/{id}")
    suspend fun eliminarContacto(
        @Path("id") id: Int,
        @Header("Authorization") token: String
    ): Response<Unit>

    // ALERTA POR WHATSAPP A LOS CONTACTOS DE EMERGENCIA
    @POST("api/contacts/emergency/alert")
    suspend fun enviarAlertaEmergencia(
        @Query("location") location: String,
        @Query("userId") userId: Int,
        @Header("Authorization") token: String
    ): Response<ResponseBody>
}
