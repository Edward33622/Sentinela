package com.cibertec.sentinela.utils

object Conexion {

    // API local (Spring Boot en el puerto 8080):
    // • Emulador Android: 10.0.2.2 (es la PC vista desde el emulador). Es el valor por defecto del grupo.
    // • Celular real en el mismo Wi-Fi: usa la IP de tu PC (comando ipconfig) solo en tu copia local,
    //   sin subir ese cambio a GitHub, porque la IP es distinta en cada PC.
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

        // Subscriptions
        const val subscriptionPlans = "/api/subscriptions/plans"
        const val subscribe = "/api/subscriptions/subscribe"
        fun userSubscription(userId: Int) = "/api/subscriptions/user/$userId"
    }
}
