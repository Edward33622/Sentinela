package com.cibertec.sentinela.services

import com.cibertec.sentinela.utils.Conexion
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Cliente HTTP compartido (equivalente a URLSession.shared + JSONEncoder/JSONDecoder). */
object ApiClient {

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun buildRetrofit(url: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(if (url.endsWith("/")) url else "$url/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private var retrofit: Retrofit = buildRetrofit(Conexion.baseURL)

    var auth: AuthService = retrofit.create(AuthService::class.java)
        private set
    var users: UserService = retrofit.create(UserService::class.java)
        private set
    var contacts: ContactService = retrofit.create(ContactService::class.java)
        private set
    var emergencyEvents: EmergencyEventService = retrofit.create(EmergencyEventService::class.java)
        private set
    var emergencyLocations: EmergencyLocationService = retrofit.create(EmergencyLocationService::class.java)
        private set
    var emergencyMedia: EmergencyMediaService = retrofit.create(EmergencyMediaService::class.java)
        private set
    var ai: AiService = retrofit.create(AiService::class.java)
        private set
    var subscriptions: SubscriptionService = retrofit.create(SubscriptionService::class.java)
        private set
    var family: FamilyService = retrofit.create(FamilyService::class.java)
        private set

    fun actualizarBaseUrl(nuevaUrl: String) {
        val url = if (nuevaUrl.endsWith("/")) nuevaUrl else "$nuevaUrl/"
        Conexion.customBaseURL = if (nuevaUrl.endsWith("/")) nuevaUrl.dropLast(1) else nuevaUrl
        retrofit = buildRetrofit(url)
        auth = retrofit.create(AuthService::class.java)
        users = retrofit.create(UserService::class.java)
        contacts = retrofit.create(ContactService::class.java)
        emergencyEvents = retrofit.create(EmergencyEventService::class.java)
        emergencyLocations = retrofit.create(EmergencyLocationService::class.java)
        emergencyMedia = retrofit.create(EmergencyMediaService::class.java)
        ai = retrofit.create(AiService::class.java)
        subscriptions = retrofit.create(SubscriptionService::class.java)
        family = retrofit.create(FamilyService::class.java)
    }

    fun bearer(token: String) = "Bearer $token"
}

/**
 * Petición genérica: ejecuta la llamada y devuelve Result.success / Result.failure,
 * igual que los completion handlers Result<T, Error> de la versión iOS.
 */
suspend fun <T> realizarPeticion(peticion: suspend () -> T): Result<T> {
    return try {
        Result.success(peticion())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        val rawBody = try { e.response()?.errorBody()?.string().orEmpty() } catch (_: Exception) { "" }
        val mensajeFinal = when {
            e.code() == 409 || rawBody.contains("correo", ignoreCase = true) || rawBody.contains("email", ignoreCase = true) -> {
                "Este correo electrónico ya se encuentra registrado. Por favor inicia sesión o utiliza otro correo."
            }
            e.code() == 401 -> {
                "Credenciales incorrectas. Verifica tu correo y contraseña."
            }
            rawBody.isNotBlank() -> {
                try {
                    val json = JSONObject(rawBody)
                    json.optString("message", json.optString("error", "Fallo del servidor (Código ${e.code()})"))
                } catch (_: Exception) {
                    "Fallo del servidor. Código: ${e.code()}"
                }
            }
            else -> "Fallo del servidor. Código: ${e.code()}"
        }
        Result.failure(Exception(mensajeFinal))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
