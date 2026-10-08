package com.cibertec.sentinela.emergency

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/** Estado global de la emergencia (isServiceActive / currentEventId del HomeViewController). */
object EmergencyManager {

    val isServiceActive = MutableStateFlow(false)

    @Volatile
    var currentEventId: Int? = null

    // Vive mientras viva la app: las peticiones de cierre no se cancelan al detener el servicio
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
}
