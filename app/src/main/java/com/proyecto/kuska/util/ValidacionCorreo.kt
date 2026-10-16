package com.proyecto.kuska.util

import androidx.annotation.StringRes
import com.proyecto.kuska.R

// Kotlin puro (no android.util.Patterns) para poder probarla en la JVM.
private val formatoCorreo = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/** Valida un correo (se ignoran los espacios de los extremos). Devuelve el mensaje de error o null. */
@StringRes
fun validarCorreo(correo: String): Int? {
    val limpio = correo.trim()
    return when {
        limpio.isEmpty() -> R.string.error_correo_vacio
        !formatoCorreo.matches(limpio) -> R.string.error_correo_invalido
        else -> null
    }
}
