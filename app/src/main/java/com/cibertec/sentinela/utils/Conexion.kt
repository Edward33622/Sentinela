package com.cibertec.sentinela.utils

object Conexion {

    // API local:
    // • En celular real conectado al mismo Wi-Fi: usa la IP de la PC (192.168.1.34).
    // • En emulador Android: 10.0.2.2 o 192.168.1.34.
    const val baseURL = "http://192.168.1.34:8080"

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
