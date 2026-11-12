package com.cibertec.sentinela.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File

/**
 * Foto de perfil guardada en el teléfono, una por cuenta (profile_<userId>.jpg),
 * para que al cambiar de usuario no aparezca la foto de otra persona.
 */
object FotoPerfil {

    private const val LADO_MAXIMO = 1024

    private fun archivo(context: Context, userId: Int) = File(context.filesDir, "profile_$userId.jpg")

    fun cargar(context: Context, userId: Int): Bitmap? {
        if (userId == 0) return null
        val foto = archivo(context, userId)
        return if (foto.exists()) BitmapFactory.decodeFile(foto.path) else null
    }

    fun guardar(context: Context, userId: Int, imagen: Bitmap): Boolean {
        if (userId == 0) return false
        return try {
            archivo(context, userId).outputStream().use { imagen.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun borrar(context: Context, userId: Int) {
        archivo(context, userId).delete()
    }

    /** Lee la imagen elegida reduciéndola y enderezándola según su orientación EXIF (fotos de cámara). */
    fun decodificar(context: Context, uri: Uri): Bitmap? {
        return try {
            val resolver = context.contentResolver
            val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }

            var muestra = 1
            while (limites.outWidth / muestra > LADO_MAXIMO || limites.outHeight / muestra > LADO_MAXIMO) muestra *= 2

            val opciones = BitmapFactory.Options().apply { inSampleSize = muestra }
            val imagen = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opciones) }
                ?: return null

            val grados = resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            if (grados == 0f) imagen
            else Bitmap.createBitmap(imagen, 0, 0, imagen.width, imagen.height, Matrix().apply { postRotate(grados) }, true)
        } catch (e: Exception) {
            null
        }
    }
}
