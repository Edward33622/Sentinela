package com.cibertec.sentinela.utils

import android.app.Activity
import android.content.Context
import android.view.inputmethod.InputMethodManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Alerta simple con título "Atención" y botón "Aceptar". */
fun Context.presentarAlerta(mensaje: String, alAceptar: (() -> Unit)? = null) {
    MaterialAlertDialogBuilder(this)
        .setTitle("Atención")
        .setMessage(mensaje)
        .setCancelable(alAceptar == null)
        .setPositiveButton("Aceptar") { _, _ -> alAceptar?.invoke() }
        .show()
}

fun Activity.ocultarTeclado() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    currentFocus?.let {
        imm.hideSoftInputFromWindow(it.windowToken, 0)
        it.clearFocus()
    }
}

/**
 * Convierte una fecha ISO del backend (con o sin zona horaria, con o sin milisegundos)
 * al formato indicado. Si no se puede interpretar devuelve el texto original.
 */
fun formatearFecha(iso: String, formato: String): String {
    if (iso.isEmpty()) return ""

    val fecha: LocalDateTime = try {
        // Con zona: 2025-12-15T20:14:00Z / 2025-12-15T20:14:00.123-05:00
        OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
    } catch (e: DateTimeParseException) {
        try {
            // LocalDateTime sin zona: 2025-12-15T20:14:00 / 2025-12-15T20:14:00.123
            LocalDateTime.parse(iso)
        } catch (e2: DateTimeParseException) {
            return iso
        }
    }
    return fecha.format(DateTimeFormatter.ofPattern(formato, Locale("es", "PE")))
}
