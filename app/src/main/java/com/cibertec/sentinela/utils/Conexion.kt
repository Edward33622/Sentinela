package com.cibertec.sentinela.utils

object Conexion {

    // API local: 10.0.2.2 es la PC vista desde el emulador de Android.
    // En un celular real usa la IP de tu PC en la red Wi-Fi (y agrégala en res/xml/network_security_config.xml).
    const val baseURL = "http://10.0.2.2:8080"

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
    }
}
