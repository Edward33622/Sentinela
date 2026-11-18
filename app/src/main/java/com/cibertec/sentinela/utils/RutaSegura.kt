package com.cibertec.sentinela.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lógica de rutas compartida por el inicio (trayecto seguro) y el Copiloto IA:
 * distritos de Lima y Callao, coordenadas de lugares, ruta vial (OSRM) y semáforo de riesgo.
 */
object RutaSegura {

    private const val TAG = "RutaSegura"

    data class RouteResult(val points: List<GeoPoint>, val isApproximate: Boolean)

    // Coordenadas céntricas de los distritos de Lima y Callao para autodetección por GPS y trazado vial
    val COORDENADAS_DISTRITOS = mapOf(
        "San Miguel (Lima)" to GeoPoint(-12.0772, -77.0863),
        "Miraflores (Lima)" to GeoPoint(-12.1211, -77.0297),
        "San Isidro (Lima)" to GeoPoint(-12.0984, -77.0353),
        "Santiago de Surco (Lima)" to GeoPoint(-12.1456, -76.9944),
        "San Borja (Lima)" to GeoPoint(-12.0911, -77.0019),
        "Cercado de Lima" to GeoPoint(-12.0464, -77.0428),
        "Los Olivos (Lima)" to GeoPoint(-11.9932, -77.0716),
        "Jesús María (Lima)" to GeoPoint(-12.0722, -77.0494),
        "Magdalena del Mar (Lima)" to GeoPoint(-12.0906, -77.0706),
        "Pueblo Libre (Lima)" to GeoPoint(-12.0769, -77.0658),
        "Lince (Lima)" to GeoPoint(-12.0831, -77.0344),
        "Surquillo (Lima)" to GeoPoint(-12.1122, -77.0142),
        "Barranco (Lima)" to GeoPoint(-12.1469, -77.0206),
        "Chorrillos (Lima)" to GeoPoint(-12.1764, -77.0147),
        "La Molina (Lima)" to GeoPoint(-12.0886, -76.9458),
        "Ate (Lima)" to GeoPoint(-12.0289, -76.9189),
        "San Juan de Lurigancho (Lima)" to GeoPoint(-11.9792, -77.0056),
        "San Juan de Miraflores (Lima)" to GeoPoint(-12.1625, -76.9633),
        "Villa El Salvador (Lima)" to GeoPoint(-12.2136, -76.9367),
        "Villa María del Triunfo (Lima)" to GeoPoint(-12.1611, -76.9389),
        "Comas (Lima)" to GeoPoint(-11.9369, -77.0519),
        "Independencia (Lima)" to GeoPoint(-11.9908, -77.0539),
        "San Martín de Porres (Lima)" to GeoPoint(-12.0167, -77.0833),
        "Carabayllo (Lima)" to GeoPoint(-11.8986, -77.0347),
        "Puente Piedra (Lima)" to GeoPoint(-11.8672, -77.0758),
        "Rímac (Lima)" to GeoPoint(-12.0328, -77.0272),
        "Breña (Lima)" to GeoPoint(-12.0578, -77.0514),
        "La Victoria (Lima)" to GeoPoint(-12.0658, -77.0144),
        "El Agustino (Lima)" to GeoPoint(-12.0517, -77.0017),
        "Santa Anita (Lima)" to GeoPoint(-12.0469, -76.9694),
        "Lurín (Lima)" to GeoPoint(-12.2756, -76.8711),
        "Pachacámac (Lima)" to GeoPoint(-12.2300, -76.8600),
        "Chaclacayo (Lima)" to GeoPoint(-11.9806, -76.7681),
        "Chosica / Lurigancho (Lima)" to GeoPoint(-11.9392, -76.7083),
        "Bellavista (Callao)" to GeoPoint(-12.0608, -77.1264),
        "Callao (Cercado)" to GeoPoint(-12.0565, -77.1181),
        "La Perla (Callao)" to GeoPoint(-12.0717, -77.1172),
        "La Punta (Callao)" to GeoPoint(-12.0728, -77.1611),
        "Carmen de la Legua (Callao)" to GeoPoint(-12.0442, -77.0944),
        "Ventanilla (Callao)" to GeoPoint(-11.8667, -77.1333),
        "Mi Perú (Callao)" to GeoPoint(-11.8542, -77.1189),
        "Ancón (Lima)" to GeoPoint(-11.7733, -77.1761),
        "Pucusana (Lima)" to GeoPoint(-12.4833, -76.7833),
        "Punta Hermosa (Lima)" to GeoPoint(-12.3333, -76.8167),
        "Punta Negra (Lima)" to GeoPoint(-12.3667, -76.7833),
        "San Bartolo (Lima)" to GeoPoint(-12.3833, -76.7667),
        "Santa María del Mar (Lima)" to GeoPoint(-12.4000, -76.7667),
        "Santa Rosa (Lima)" to GeoPoint(-11.8000, -77.1667),
        "Cieneguilla (Lima)" to GeoPoint(-12.0833, -76.7667),
        "San Luis (Lima)" to GeoPoint(-12.0750, -77.0000)
    )

    /** Opciones del punto de partida; la última permite escribir una provincia. */
    val DISTRITOS_LIMA_CALLAO = arrayOf(
        "San Miguel (Lima)", "Miraflores (Lima)", "San Isidro (Lima)", "Santiago de Surco (Lima)",
        "San Borja (Lima)", "Cercado de Lima", "Los Olivos (Lima)", "Jesús María (Lima)",
        "Magdalena del Mar (Lima)", "Pueblo Libre (Lima)", "Lince (Lima)", "Surquillo (Lima)",
        "Barranco (Lima)", "Chorrillos (Lima)", "La Molina (Lima)", "Ate (Lima)",
        "San Juan de Lurigancho (Lima)", "San Juan de Miraflores (Lima)", "Villa El Salvador (Lima)",
        "Villa María del Triunfo (Lima)", "Comas (Lima)", "Independencia (Lima)",
        "San Martín de Porres (Lima)", "Carabayllo (Lima)", "Puente Piedra (Lima)",
        "Rímac (Lima)", "Breña (Lima)", "La Victoria (Lima)", "El Agustino (Lima)",
        "Santa Anita (Lima)", "Lurín (Lima)", "Pachacámac (Lima)", "Chaclacayo (Lima)",
        "Chosica / Lurigancho (Lima)", "Bellavista (Callao)", "Callao (Cercado)",
        "La Perla (Callao)", "La Punta (Callao)", "Carmen de la Legua (Callao)",
        "Ventanilla (Callao)", "Mi Perú (Callao)", "Ancón (Lima)", "Pucusana (Lima)",
        "Punta Hermosa (Lima)", "Punta Negra (Lima)", "San Bartolo (Lima)",
        "Santa María del Mar (Lima)", "Santa Rosa (Lima)", "Cieneguilla (Lima)",
        "San Luis (Lima)", "📍 Otra provincia o región..."
    )

    const val OTRA_PROVINCIA = "📍 Otra provincia o región..."

    /** Provincias fuera de la cobertura metropolitana (Lima y Callao). */
    val PROVINCIAS_FUERA = listOf(
        "TUMBES", "PIURA", "LAMBAYEQUE", "CHICLAYO", "TRUJILLO", "LA LIBERTAD",
        "CHIMBOTE", "HUARAZ", "ANCASH", "ICA", "CHINCHA", "PISCO", "NAZCA",
        "AREQUIPA", "MOQUEGUA", "ILO", "TACNA", "CAJAMARCA", "AMAZONAS",
        "TARAPOTO", "LORETO", "IQUITOS", "UCAYALI", "PUCALLPA", "MADRE DE DIOS",
        "CUSCO", "CUZCO", "PUNO", "JULIACA", "HUANUCO", "PASCO", "JUNIN", "HUANCAYO",
        "AYACUCHO", "APURIMAC"
    )

    /** Color, etiqueta y detalle del semáforo según el nivel de riesgo de la IA. */
    fun semaforo(nivelRiesgo: String, esInviable: Boolean): Triple<String, String, String> {
        return when {
            esInviable || nivelRiesgo.contains("ALTO", ignoreCase = true) ||
            nivelRiesgo.contains("EXTREMO", ignoreCase = true) ||
            nivelRiesgo.contains("ROJO", ignoreCase = true) -> {
                Triple("#EF4444", "🔴 Tramo de Alto Riesgo", "Zonas desoladas no recomendadas de noche o inviables")
            }
            nivelRiesgo.contains("MODERADO", ignoreCase = true) ||
            nivelRiesgo.contains("MEDIO", ignoreCase = true) ||
            nivelRiesgo.contains("AMARILLO", ignoreCase = true) -> {
                Triple("#F59E0B", "🟡 Tramo con Precaución", "Congestión vehicular y paraderos con arrebatos")
            }
            else -> {
                Triple("#10B981", "🟢 Vía Segura Iluminada", "Avenidas principales con cámaras e iluminación activa")
            }
        }
    }

    fun buscarDistritoMasCercano(lat: Double, lng: Double): Pair<String?, Float> {
        var masCercano: String? = null
        var menorDistancia = Float.MAX_VALUE
        val results = FloatArray(1)

        for ((distrito, geo) in COORDENADAS_DISTRITOS) {
            Location.distanceBetween(lat, lng, geo.latitude, geo.longitude, results)
            val d = results[0]
            if (d < menorDistancia) {
                menorDistancia = d
                masCercano = distrito
            }
        }
        return Pair(masCercano, menorDistancia)
    }

    fun resolverCoordenadaLugar(lugar: String): GeoPoint {
        val lugarUpper = lugar.uppercase().trim()

        // 1. Coincidencia directa en distritos
        COORDENADAS_DISTRITOS[lugar]?.let { return it }

        // Priorizar el mercado Ciudad de Dios de San Juan de Miraflores sobre localidades homónimas.
        if (lugarUpper.contains("CIUDAD DE DIOS")) {
            return GeoPoint(-12.15274, -76.97094)
        }

        // 2. Coincidencia parcial con distritos
        for ((distrito, geo) in COORDENADAS_DISTRITOS) {
            val nombreBase = distrito.substringBefore(" (").uppercase()
            if (lugarUpper.contains(nombreBase)) {
                return geo
            }
        }

        // 3. Atajos conocidos
        when {
            lugarUpper.contains("CIBERTEC") -> return GeoPoint(-12.1221, -77.0305) // Cibertec Miraflores
            lugarUpper.contains("CASA") -> return GeoPoint(-12.0984, -77.0353) // San Isidro
            lugarUpper.contains("TRABAJO") || lugarUpper.contains("OFICINA") -> return GeoPoint(-12.0911, -77.0019) // San Borja
            lugarUpper.contains("COMERCIAL") || lugarUpper.contains("MALL") -> return GeoPoint(-12.0860, -77.0030)
            // Provincias principales del Perú
            lugarUpper.contains("TUMBES") -> return GeoPoint(-3.5669, -80.4515)
            lugarUpper.contains("PIURA") -> return GeoPoint(-5.1945, -80.6328)
            lugarUpper.contains("CHICLAYO") || lugarUpper.contains("LAMBAYEQUE") -> return GeoPoint(-6.7714, -79.8409)
            lugarUpper.contains("TRUJILLO") || lugarUpper.contains("LIBERTAD") -> return GeoPoint(-8.1117, -79.0287)
            lugarUpper.contains("CHIMBOTE") || lugarUpper.contains("ANCASH") -> return GeoPoint(-9.0744, -78.5936)
            lugarUpper.contains("HUARAZ") -> return GeoPoint(-9.5261, -77.5289)
            lugarUpper.contains("ICA") || lugarUpper.contains("CHINCHA") || lugarUpper.contains("PISCO") -> return GeoPoint(-14.0678, -75.7286)
            lugarUpper.contains("AREQUIPA") -> return GeoPoint(-16.4090, -71.5375)
            lugarUpper.contains("CUSCO") || lugarUpper.contains("CUZCO") -> return GeoPoint(-13.5319, -71.9675)
            lugarUpper.contains("TACNA") -> return GeoPoint(-18.0146, -70.2536)
            lugarUpper.contains("PUNO") || lugarUpper.contains("JULIACA") -> return GeoPoint(-15.8422, -70.0199)
            lugarUpper.contains("HUANCAYO") || lugarUpper.contains("JUNIN") -> return GeoPoint(-12.0651, -75.2049)
            lugarUpper.contains("AYACUCHO") -> return GeoPoint(-13.1588, -74.2239)
            lugarUpper.contains("TARAPOTO") || lugarUpper.contains("SAN MARTIN") -> return GeoPoint(-6.4854, -76.3686)
            lugarUpper.contains("IQUITOS") || lugarUpper.contains("LORETO") -> return GeoPoint(-3.7491, -73.2538)
        }

        // 4. Default: Centro de Lima
        return GeoPoint(-12.0464, -77.0428)
    }

    suspend fun obtenerRutaCallesReal(inicio: GeoPoint, fin: GeoPoint, medio: String): RouteResult = withContext(Dispatchers.IO) {
        val modo = if (medio.contains("pie", ignoreCase = true)) "foot" else "driving"
        val urlStr = "https://router.project-osrm.org/route/v1/$modo/${inicio.longitude},${inicio.latitude};${fin.longitude},${fin.latitude}?overview=full&geometries=geojson"

        try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "SentinelaApp/1.0 (Android; OpenStreetMap)")

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()

                val root = JSONObject(sb.toString())
                val routes = root.optJSONArray("routes")
                if (routes != null && routes.length() > 0) {
                    val geometry = routes.getJSONObject(0).optJSONObject("geometry")
                    val coords = geometry?.optJSONArray("coordinates")
                    if (coords != null && coords.length() > 0) {
                        val puntosViales = mutableListOf<GeoPoint>()
                        for (i in 0 until coords.length()) {
                            val par = coords.getJSONArray(i)
                            val lon = par.getDouble(0)
                            val lat = par.getDouble(1)
                            puntosViales.add(GeoPoint(lat, lon))
                        }
                        if (puntosViales.size >= 2) {
                            return@withContext RouteResult(puntosViales, isApproximate = false)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo consultar enrutador OSRM en tiempo real: ${e.message}")
        }

        return@withContext RouteResult(generarPuntosCorredor(inicio, fin), isApproximate = true)
    }

    fun generarPuntosCorredor(inicio: GeoPoint, fin: GeoPoint): List<GeoPoint> {
        val puntos = mutableListOf<GeoPoint>()
        puntos.add(inicio)

        val results = FloatArray(1)
        Location.distanceBetween(inicio.latitude, inicio.longitude, fin.latitude, fin.longitude, results)
        val distMetros = results[0]

        if (distMetros > 80_000) {
            val pasos = 8
            for (i in 1 until pasos) {
                val frac = i.toDouble() / pasos
                val lat = inicio.latitude + (fin.latitude - inicio.latitude) * frac
                val lon = inicio.longitude + (fin.longitude - inicio.longitude) * frac
                puntos.add(GeoPoint(lat, lon))
            }
        } else {
            val pasos = 5
            for (i in 1 until pasos) {
                val frac = i.toDouble() / pasos
                val lat = inicio.latitude + (fin.latitude - inicio.latitude) * frac
                val lon = inicio.longitude + (fin.longitude - inicio.longitude) * frac
                puntos.add(GeoPoint(lat, lon))
            }
        }

        puntos.add(fin)
        return puntos
    }

    fun nombreDestinoVisible(destino: String): String =
        if (destino.contains("CIUDAD DE DIOS", ignoreCase = true)) {
            "Mercado Ciudad de Dios · San Juan de Miraflores"
        } else {
            destino
        }

    fun crearIconoMarcador(context: Context, icono: String, colorFondo: Int): android.graphics.drawable.Drawable {
        val density = context.resources.displayMetrics.density
        val size = (42 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, (size * 1.25f).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Sombra suave inferior
        paint.color = Color.argb(50, 0, 0, 0)
        canvas.drawCircle(size / 2f, size / 2f + 2 * density, size / 2f - 2 * density, paint)

        // Círculo principal con color de riesgo
        paint.color = colorFondo
        paint.style = Paint.Style.FILL
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3 * density, paint)

        // Borde blanco moderno
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * density
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3 * density, paint)

        // Puntero / triángulo inferior hacia la calle
        paint.style = Paint.Style.FILL
        paint.color = colorFondo
        val path = Path().apply {
            moveTo(size / 2f - 6 * density, size / 2f + 11 * density)
            lineTo(size / 2f + 6 * density, size / 2f + 11 * density)
            lineTo(size / 2f, size * 1.2f)
            close()
        }
        canvas.drawPath(path, paint)

        // Emoji o símbolo interior
        paint.color = Color.WHITE
        paint.textSize = 17 * density
        paint.textAlign = Paint.Align.CENTER
        val yPos = (size / 2f - (paint.descent() + paint.ascent()) / 2)
        canvas.drawText(icono, size / 2f, yPos, paint)

        return BitmapDrawable(context.resources, bitmap)
    }

}
