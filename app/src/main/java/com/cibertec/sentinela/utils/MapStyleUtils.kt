package com.cibertec.sentinela.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

object MapStyleUtils {

    /**
     * Aplica el estilo moderno del mapa garantizando disponibilidad al 100% sin requerir API KEY.
     * En modo claro: mapa de alta definición escalado a la resolución de pantalla.
     * En modo noche: filtro matricial táctico oscuro de alto contraste.
     */
    fun aplicarEstiloModerno(mapView: MapView, modoOscuro: Boolean = false) {
        // Tile source oficial OpenStreetMap: libre de API keys y de alta disponibilidad
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.isTilesScaledToDpi = true

        if (modoOscuro) {
            val darkMatrix = ColorMatrix().apply {
                set(floatArrayOf(
                    -0.80f,  0.0f,   0.0f,  0.0f, 210f,
                     0.0f,  -0.80f,  0.0f,  0.0f, 210f,
                     0.0f,   0.0f,  -0.75f, 0.0f, 220f,
                     0.0f,   0.0f,   0.0f,  1.0f, 0f
                ))
            }
            mapView.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(darkMatrix))
        } else {
            mapView.overlayManager.tilesOverlay.setColorFilter(null)
        }
        mapView.invalidate()
    }

    /**
     * Genera los iconos estilizados para la ubicación del usuario (punto azul brillante y flecha de rumbo estilo inDrive)
     */
    fun crearPunteroUbicacion(context: Context): Pair<Bitmap, Bitmap> {
        val density = context.resources.displayMetrics.density

        // 1. Puntero estático (Punto Azul con doble anillo y sombra suave)
        val sizePunto = (32 * density).toInt()
        val bmpPunto = Bitmap.createBitmap(sizePunto, sizePunto, Bitmap.Config.ARGB_8888)
        val canvasPunto = Canvas(bmpPunto)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val centroP = sizePunto / 2f

        // Halo exterior translúcido (pulso)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#330284C7")
        canvasPunto.drawCircle(centroP, centroP, 15f * density, paint)

        // Sombra suave inferior
        paint.color = Color.parseColor("#35000000")
        canvasPunto.drawCircle(centroP, centroP + 1.5f * density, 9f * density, paint)

        // Anillo exterior blanco nítido
        paint.color = Color.WHITE
        canvasPunto.drawCircle(centroP, centroP, 9f * density, paint)

        // Núcleo azul primario brillante (estilo GPS profesional)
        paint.color = Color.parseColor("#0284C7")
        canvasPunto.drawCircle(centroP, centroP, 6.5f * density, paint)

        // Brillo interior blanco
        paint.color = Color.parseColor("#BAE6FD")
        canvasPunto.drawCircle(centroP, centroP, 2.5f * density, paint)

        // 2. Flecha de navegación con rumbo (Chevron estilo app de movilidad)
        val sizeFlecha = (36 * density).toInt()
        val bmpFlecha = Bitmap.createBitmap(sizeFlecha, sizeFlecha, Bitmap.Config.ARGB_8888)
        val canvasFlecha = Canvas(bmpFlecha)
        val paintFlecha = Paint(Paint.ANTI_ALIAS_FLAG)
        val centroF = sizeFlecha / 2f

        // Sombra de la flecha
        paintFlecha.style = Paint.Style.FILL
        paintFlecha.color = Color.parseColor("#35000000")
        val pathSombra = Path().apply {
            moveTo(centroF, 4f * density)
            lineTo(sizeFlecha - 6f * density, sizeFlecha - 5f * density)
            lineTo(centroF, sizeFlecha - 10f * density)
            lineTo(6f * density, sizeFlecha - 5f * density)
            close()
        }
        canvasFlecha.drawPath(pathSombra, paintFlecha)

        // Borde blanco de la flecha
        paintFlecha.color = Color.WHITE
        val pathBorde = Path().apply {
            moveTo(centroF, 2.5f * density)
            lineTo(sizeFlecha - 5f * density, sizeFlecha - 6f * density)
            lineTo(centroF, sizeFlecha - 11f * density)
            lineTo(5f * density, sizeFlecha - 6f * density)
            close()
        }
        canvasFlecha.drawPath(pathBorde, paintFlecha)

        // Relleno azul de la flecha
        paintFlecha.color = Color.parseColor("#0284C7")
        val pathFlecha = Path().apply {
            moveTo(centroF, 5.5f * density)
            lineTo(sizeFlecha - 7.5f * density, sizeFlecha - 8.5f * density)
            lineTo(centroF, sizeFlecha - 12.5f * density)
            lineTo(7.5f * density, sizeFlecha - 8.5f * density)
            close()
        }
        canvasFlecha.drawPath(pathFlecha, paintFlecha)

        return Pair(bmpPunto, bmpFlecha)
    }
}

/**
 * Overlay de ubicación del usuario estilo inDrive / Uber con halo azul de precisión
 */
class SentinelaLocationOverlay(
    context: Context,
    provider: IMyLocationProvider,
    mapView: MapView
) : MyLocationNewOverlay(provider, mapView) {

    init {
        val (bmpPunto, bmpFlecha) = MapStyleUtils.crearPunteroUbicacion(context)
        setPersonIcon(bmpPunto)
        setDirectionArrow(bmpPunto, bmpFlecha)
        setPersonAnchor(0.5f, 0.5f)
        setDirectionAnchor(0.5f, 0.5f)
        setDrawAccuracyEnabled(true)

        // Halo suave de precisión azul translúcido
        mCirclePaint.apply {
            color = Color.parseColor("#250284C7")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    }
}
