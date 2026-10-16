package com.proyecto.kuska.ui.login

import androidx.annotation.StringRes
import com.proyecto.kuska.R

/** Validación local del login, antes de llamar a la API. Devuelve el mensaje de error o null si está bien. */
object ValidacionLogin {

    @StringRes
    fun validarCorreo(correo: String): Int? = com.proyecto.kuska.util.validarCorreo(correo)

    @StringRes
    fun validarContrasena(contrasena: String): Int? =
        if (contrasena.isEmpty()) R.string.error_contrasena_vacia else null
}
