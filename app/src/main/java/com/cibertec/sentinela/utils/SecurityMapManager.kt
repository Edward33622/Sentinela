package com.cibertec.sentinela.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon

enum class TipoPuntoAyuda(val emoji: String, val colorHex: String, val etiqueta: String) {
    COMISARIA("👮‍♂️", "#1E3A8A", "Comisaría PNP"),
    SERENAZGO("🛡️", "#065F46", "Base Serenazgo"),
    PUESTO_AUXILIO("🚨", "#D97706", "Puesto de Auxilio Rápido")
}

data class PuntoAyuda(
    val nombre: String,
    val tipo: TipoPuntoAyuda,
    val coordenadas: GeoPoint,
    val direccion: String,
    val telefono: String,
    val descripcion: String
)

enum class NivelRiesgoZona(val colorFill: String, val colorStroke: String, val etiqueta: String) {
    ALTO("#2EEF4444", "#DDEF4444", "Zona Crítica"),
    PRECAUCION("#2EF59E0B", "#DDF59E0B", "Zona de Precaución")
}

data class ZonaRiesgo(
    val titulo: String,
    val descripcion: String,
    val centro: GeoPoint,
    val radioMetros: Double,
    val nivel: NivelRiesgoZona
)

data class SecurityOverlayHandles(
    val markers: MutableList<Marker> = mutableListOf(),
    val polygons: MutableList<Polygon> = mutableListOf()
)

object SecurityMapManager {

    // 1. Puntos de Ayuda Policial y Serenazgo en Lima y Callao
    val PUNTOS_AYUDA = listOf(
        PuntoAyuda(
            nombre = "Comisaría PNP San Miguel",
            tipo = TipoPuntoAyuda.COMISARIA,
            coordenadas = GeoPoint(-12.0792, -77.0885),
            direccion = "Calle Libertad 345, San Miguel",
            telefono = "(01) 263-4567",
            descripcion = "Atención permanente 24h, patrullaje integrado y recepción de denuncias."
        ),
        PuntoAyuda(
            nombre = "Base Central Serenazgo San Miguel",
            tipo = TipoPuntoAyuda.SERENAZGO,
            coordenadas = GeoPoint(-12.0745, -77.0821),
            direccion = "Av. Federico Gallese 390",
            telefono = "(01) 311-4567",
            descripcion = "Centro de monitoreo de cámaras de videovigilancia y despacho de unidades de serenazgo."
        ),
        PuntoAyuda(
            nombre = "Comisaría PNP Miraflores",
            tipo = TipoPuntoAyuda.COMISARIA,
            coordenadas = GeoPoint(-12.1154, -77.0289),
            direccion = "Calle General Borgoño 1065, Miraflores",
            telefono = "(01) 445-3210",
            descripcion = "Servicio policial de respuesta rápida, atención ciudadana e intervención de emergencias."
        ),
        PuntoAyuda(
            nombre = "Base de Seguridad Ciudadana Miraflores",
            tipo = TipoPuntoAyuda.SERENAZGO,
            coordenadas = GeoPoint(-12.1225, -77.0315),
            direccion = "Av. Larco 400 (Parque Kennedy)",
            telefono = "(01) 313-3773",
            descripcion = "Puesto central táctico de serenazgo con brigada de rescate e interconexión al 105."
        ),
        PuntoAyuda(
            nombre = "Comisaría PNP San Isidro",
            tipo = TipoPuntoAyuda.COMISARIA,
            coordenadas = GeoPoint(-12.0965, -77.0321),
            direccion = "Calle Augusto Tamayo 140, San Isidro",
            telefono = "(01) 441-1550",
            descripcion = "Comisaría distrital y patrullaje motorizado en el centro financiero y residencial."
        ),
        PuntoAyuda(
            nombre = "Comisaría PNP Alfonso Ugarte (Lima)",
            tipo = TipoPuntoAyuda.COMISARIA,
            coordenadas = GeoPoint(-12.0498, -77.0425),
            direccion = "Av. Alfonso Ugarte 1240, Cercado de Lima",
            telefono = "(01) 424-0010",
            descripcion = "Comisaría central con escuadrón de auxilio rápido para el Centro Histórico y Breña."
        ),
        PuntoAyuda(
            nombre = "Comisaría PNP Monterrico - Surco",
            tipo = TipoPuntoAyuda.COMISARIA,
            coordenadas = GeoPoint(-12.1389, -76.9821),
            direccion = "Av. Manuel Olguín 240, Surco",
            telefono = "(01) 436-1212",
            descripcion = "Base policial con servicio de guardia de prevención y radio patrulla."
        ),
        PuntoAyuda(
            nombre = "Puesto de Auxilio Rápido (PAR) San Borja",
            tipo = TipoPuntoAyuda.PUESTO_AUXILIO,
            coordenadas = GeoPoint(-12.0885, -77.0032),
            direccion = "Cruce Av. Javier Prado con Av. Aviación",
            telefono = "(01) 631-1000",
            descripcion = "Módulo de respuesta inmediata con personal PNP y serenazgo interconectado."
        ),
        PuntoAyuda(
            nombre = "Comisaría PNP Sol de Oro - Los Olivos",
            tipo = TipoPuntoAyuda.COMISARIA,
            coordenadas = GeoPoint(-11.9961, -77.0694),
            direccion = "Av. Angélica Gamarra s/n, Los Olivos",
            telefono = "(01) 521-1250",
            descripcion = "Unidad policial para Lima Norte con servicio de emergencia las 24 horas."
        )
    )

    // 2. Zonas Calientes de Riesgo / Heatmap Preventivo
    val ZONAS_RIESGO = listOf(
        ZonaRiesgo(
            titulo = "Zona Crítica: Paradero Caquetá",
            descripcion = "Alto índice de robos al paso y arrebatos en paraderos de transporte público, especialmente en horas de la noche (19:00 - 23:30 hrs).",
            centro = GeoPoint(-12.0368, -77.0392),
            radioMetros = 380.0,
            nivel = NivelRiesgoZona.ALTO
        ),
        ZonaRiesgo(
            titulo = "Zona de Alerta: Cruce Javier Prado / Vía Expresa",
            descripcion = "Congestión vehicular densa y reportes de arrebatos por ventanilla abierta en semáforos en hora punta.",
            centro = GeoPoint(-12.0915, -77.0279),
            radioMetros = 260.0,
            nivel = NivelRiesgoZona.PRECAUCION
        ),
        ZonaRiesgo(
            titulo = "Zona Crítica: Paradero Puente Nuevo",
            descripcion = "Punto de transbordo masivo con reportes frecuentes de carteristas y asaltos en puentes peatonales.",
            centro = GeoPoint(-12.0354, -77.0098),
            radioMetros = 360.0,
            nivel = NivelRiesgoZona.ALTO
        ),
        ZonaRiesgo(
            titulo = "Zona de Precaución: Óvalo Higuereta",
            descripcion = "Intersección concurrida. Precaución en cajeros automáticos y paraderos informales durante la madrugada.",
            centro = GeoPoint(-12.1325, -77.0018),
            radioMetros = 250.0,
            nivel = NivelRiesgoZona.PRECAUCION
        ),
        ZonaRiesgo(
            titulo = "Zona Crítica: Av. Morales Duárez / Faucett",
            descripcion = "Zona limítrofe con poca iluminación nocturna y zonas de desmonte. No se recomienda transitar a pie de noche.",
            centro = GeoPoint(-12.0452, -77.1085),
            radioMetros = 360.0,
            nivel = NivelRiesgoZona.ALTO
        ),
        ZonaRiesgo(
            titulo = "Zona de Precaución: Jirón de la Unión",
            descripcion = "Flujo peatonal muy denso en horas de la tarde. Mantener mochilas al frente para evitar sustracciones.",
            centro = GeoPoint(-12.0465, -77.0335),
            radioMetros = 240.0,
            nivel = NivelRiesgoZona.PRECAUCION
        )
    )

    fun crearIconoPuntoAyuda(context: Context, tipo: TipoPuntoAyuda): Drawable {
        val density = context.resources.displayMetrics.density
        val size = (38 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, (size * 1.25f).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val colorFondo = Color.parseColor(tipo.colorHex)

        // Sombra inferior
        paint.color = Color.argb(60, 0, 0, 0)
        canvas.drawCircle(size / 2f, size / 2f + 2 * density, size / 2f - 2 * density, paint)

        // Círculo principal
        paint.color = colorFondo
        paint.style = Paint.Style.FILL
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3 * density, paint)

        // Borde blanco moderno
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * density
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3 * density, paint)

        // Puntero inferior hacia el suelo
        paint.style = Paint.Style.FILL
        paint.color = colorFondo
        val path = Path().apply {
            moveTo(size / 2f - 5 * density, size / 2f + 10 * density)
            lineTo(size / 2f + 5 * density, size / 2f + 10 * density)
            lineTo(size / 2f, size * 1.2f)
            close()
        }
        canvas.drawPath(path, paint)

        // Emoji representativo (👮‍♂️, 🛡️, 🚨)
        paint.color = Color.WHITE
        paint.textSize = 15 * density
        paint.textAlign = Paint.Align.CENTER
        val yPos = (size / 2f - (paint.descent() + paint.ascent()) / 2)
        canvas.drawText(tipo.emoji, size / 2f, yPos, paint)

        return BitmapDrawable(context.resources, bitmap)
    }

    /**
     * Dibuja los puntos de auxilio y zonas de riesgo sobre el mapa de forma no invasiva.
     */
    fun cargarCapaSeguridad(
        context: Context,
        mapView: MapView,
        onPuntoClick: (PuntoAyuda) -> Unit,
        onZonaClick: (ZonaRiesgo) -> Unit
    ): SecurityOverlayHandles {
        val handles = SecurityOverlayHandles()
        val density = context.resources.displayMetrics.density

        // 1. Zonas de Riesgo (Polígonos circulares suaves como capa base)
        ZONAS_RIESGO.forEach { zona ->
            val polygon = Polygon(mapView).apply {
                points = Polygon.pointsAsCircle(zona.centro, zona.radioMetros)
                fillPaint.color = Color.parseColor(zona.nivel.colorFill)
                outlinePaint.apply {
                    color = Color.parseColor(zona.nivel.colorStroke)
                    strokeWidth = 2.5f * density
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }
                title = zona.titulo
                snippet = zona.descripcion
                setOnClickListener { _, _, _ ->
                    onZonaClick(zona)
                    true
                }
            }
            mapView.overlays.add(0, polygon)
            handles.polygons.add(polygon)
        }

        // 2. Marcadores de Comisarías y Serenazgo
        PUNTOS_AYUDA.forEach { punto ->
            val marker = Marker(mapView).apply {
                position = punto.coordenadas
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "${punto.tipo.emoji} ${punto.nombre}"
                snippet = "${punto.direccion}\n📞 ${punto.telefono}"
                icon = crearIconoPuntoAyuda(context, punto.tipo)
                setOnMarkerClickListener { _, _ ->
                    onPuntoClick(punto)
                    true
                }
            }
            mapView.overlays.add(marker)
            handles.markers.add(marker)
        }

        mapView.invalidate()
        return handles
    }

    fun removerCapaSeguridad(mapView: MapView, handles: SecurityOverlayHandles) {
        handles.polygons.forEach { mapView.overlays.remove(it) }
        handles.markers.forEach { mapView.overlays.remove(it) }
        handles.polygons.clear()
        handles.markers.clear()
        mapView.invalidate()
    }

    fun mostrarDialogoPuntoAyuda(context: Context, punto: PuntoAyuda) {
        MaterialAlertDialogBuilder(context)
            .setTitle("${punto.tipo.emoji} ${punto.nombre}")
            .setMessage("📍 ${punto.direccion}\n\n📞 Central: ${punto.telefono}\n\nℹ️ ${punto.descripcion}")
            .setPositiveButton("📞 Llamar") { _, _ ->
                val numTel = punto.telefono.replace(Regex("[^0-9]"), "")
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$numTel"))
                context.startActivity(intent)
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    fun mostrarDialogoZonaRiesgo(context: Context, zona: ZonaRiesgo) {
        val icono = if (zona.nivel == NivelRiesgoZona.ALTO) "🔴" else "🟡"
        MaterialAlertDialogBuilder(context)
            .setTitle("$icono ${zona.titulo}")
            .setMessage("Nivel: ${zona.nivel.etiqueta}\nRadio de influencia: ${zona.radioMetros.toInt()} metros.\n\n${zona.descripcion}")
            .setPositiveButton("Entendido", null)
            .show()
    }
}
