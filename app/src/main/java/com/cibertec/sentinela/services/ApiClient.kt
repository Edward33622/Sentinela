package com.cibertec.sentinela.services

import com.cibertec.sentinela.utils.Conexion
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Cliente HTTP compartido (equivalente a URLSession.shared + JSONEncoder/JSONDecoder). */
object ApiClient {

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(Conexion.baseURL + "/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val auth: AuthService = retrofit.create(AuthService::class.java)
    val users: UserService = retrofit.create(UserService::class.java)
    val contacts: ContactService = retrofit.create(ContactService::class.java)
    val emergencyEvents: EmergencyEventService = retrofit.create(EmergencyEventService::class.java)
    val emergencyLocations: EmergencyLocationService = retrofit.create(EmergencyLocationService::class.java)
    val emergencyMedia: EmergencyMediaService = retrofit.create(EmergencyMediaService::class.java)

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
        Result.failure(Exception("Fallo del servidor. Código: ${e.code()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
