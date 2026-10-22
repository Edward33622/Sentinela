package com.cibertec.sentinela.models

data class AnalyzeRouteRequest(
    val origin: String? = "Lima, Perú",
    val destination: String,
    val transport: String,
    val hour: String
)

data class AnalyzeRouteResponse(
    val riskLevel: String?,
    val suggestedMinutes: Int,
    val analysis: String?,
    val poweredByGemini: Boolean,
    val isFeasible: Boolean? = true,
    val estimatedDistance: String? = null
)
