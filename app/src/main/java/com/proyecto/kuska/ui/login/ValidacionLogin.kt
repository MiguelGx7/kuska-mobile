package com.proyecto.kuska.ui.login

import androidx.annotation.StringRes
import com.proyecto.kuska.R

/** Validación local del login, antes de llamar a la API. Devuelve el mensaje de error o null si está bien. */
object ValidacionLogin {

    // Kotlin puro (no android.util.Patterns) para poder probarla en la JVM.
    private val formatoCorreo = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    @StringRes
    fun validarCorreo(correo: String): Int? {
        val limpio = correo.trim()
        return when {
            limpio.isEmpty() -> R.string.error_correo_vacio
            !formatoCorreo.matches(limpio) -> R.string.error_correo_invalido
            else -> null
        }
    }

    @StringRes
    fun validarContrasena(contrasena: String): Int? =
        if (contrasena.isEmpty()) R.string.error_contrasena_vacia else null
}
