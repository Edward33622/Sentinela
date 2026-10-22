package com.cibertec.sentinela.models

import java.io.Serializable

data class SubscriptionPlan(
    val id: String,
    val name: String,
    val displayName: String,
    val monthlyPrice: Double,
    val annualPrice: Double,
    val currency: String = "PEN",
    val badge: String? = null,
    val isPopular: Boolean = false,
    val features: List<String> = emptyList(),
    val maxContacts: Int? = null,
    val aiQueriesPerMonth: Int? = null,
    val hasSmsBackup: Boolean = false,
    val hasSafeTripMode: Boolean = false
) : Serializable

data class SubscribeRequest(
    val userId: Long,
    val planId: String,
    val billingCycle: String = "MONTHLY",
    val paymentMethod: String = "GOOGLE_PLAY"
)

data class SubscriptionStatus(
    val userId: Long,
    val planId: String,
    val planDisplayName: String,
    val status: String,
    val billingCycle: String?,
    val maxContacts: Int?,
    val aiQueriesPerMonth: Int?,
    val aiQueriesUsed: Int?,
    val hasSmsBackup: Boolean,
    val hasSafeTripMode: Boolean
) : Serializable
