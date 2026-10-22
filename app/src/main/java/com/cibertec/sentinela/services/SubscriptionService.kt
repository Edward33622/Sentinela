package com.cibertec.sentinela.services

import com.cibertec.sentinela.models.SubscribeRequest
import com.cibertec.sentinela.models.SubscriptionPlan
import com.cibertec.sentinela.models.SubscriptionStatus
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface SubscriptionService {

    @GET("api/subscriptions/plans")
    suspend fun obtenerPlanes(): List<SubscriptionPlan>

    @GET("api/subscriptions/user/{userId}")
    suspend fun obtenerEstadoSuscripcion(@Path("userId") userId: Int): SubscriptionStatus

    @POST("api/subscriptions/subscribe")
    suspend fun suscribirse(@Body request: SubscribeRequest): SubscriptionStatus
}
