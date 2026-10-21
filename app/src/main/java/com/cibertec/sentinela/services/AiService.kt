package com.cibertec.sentinela.services

import com.cibertec.sentinela.models.AnalyzeRouteRequest
import com.cibertec.sentinela.models.AnalyzeRouteResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AiService {

    @POST("api/ai/analyze-route")
    suspend fun analizarRuta(@Body request: AnalyzeRouteRequest): AnalyzeRouteResponse
}
