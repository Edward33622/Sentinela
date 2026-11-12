package com.cibertec.sentinela.utils

import android.os.Build

object Conexion {

    const val CLOUD_URL = "https://sentinela-api-production-2a30.up.railway.app"
    const val DEFAULT_PC_IP = "192.168.1.34"
    const val DEFAULT_PORT = "8080"

    fun esEmulador(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK") ||
                Build.BOARD.contains("goldfish") ||
                Build.BOARD.contains("ranchu") ||
                Build.PRODUCT.contains("sdk") ||
                Build.PRODUCT.contains("emulator")
    }

    var customBaseURL: String? = CLOUD_URL

    val baseURL: String
        get() {
            return customBaseURL ?: CLOUD_URL
        }


    object Endpoints {

        // Auth
        const val login = "/auth/login"
        const val register = "/auth/register"

        // Users
        const val users = "/api/users"
        fun userById(id: Int) = "/api/users/$id"

        // Contacts
        const val contacts = "/api/contacts"
        fun contactsByUser(userId: Int) = "/api/users/$userId/contacts"

        // Emergency Events
        const val emergencyEvents = "/api/emergency-events"
        fun emergencyEventsByUser(userId: Int) = "/api/users/$userId/emergency-events"

        // Emergency Locations
        fun emergencyLocations(eventId: Int) = "/api/emergency-events/$eventId/locations"

        // Emergency Media
        fun emergencyMedia(eventId: Int) = "/api/emergency-events/$eventId/media"

        // Subscriptions
        const val subscriptionPlans = "/api/subscriptions/plans"
        const val subscribe = "/api/subscriptions/subscribe"
        fun userSubscription(userId: Int) = "/api/subscriptions/user/$userId"
    }
}
