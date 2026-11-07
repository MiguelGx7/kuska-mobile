package com.proyecto.kuska.ui.comun

import com.proyecto.kuska.data.api.modelos.Usuario

/** Nombre para "Hola, …" en los inicios. */
object Saludo {

    /** El primer nombre; si no hay, el del negocio; si tampoco, null (solo "Hola"). */
    fun nombre(usuario: Usuario?): String? {
        val primerNombre = usuario?.nombres?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() }
        return primerNombre ?: usuario?.nombreNegocio?.trim()?.takeIf { it.isNotEmpty() }
    }
}
