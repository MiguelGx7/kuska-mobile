package com.proyecto.kuska.ui.creardonacion

import com.proyecto.kuska.R
import com.proyecto.kuska.ui.comun.TextoUi

enum class CampoDonacion { TITULO, DESCRIPCION, DIRECCION, MODALIDAD }

/** Validación local de los datos de la donación (RN-10). La lista de alimentos se exige con el botón deshabilitado. */
object ValidacionDonacion {

    /** Largos máximos de PatchedDonacionEditarRequest (descripcion no tiene). */
    const val LARGO_TITULO = 120
    const val LARGO_DIRECCION = 200

    fun validar(titulo: String, direccion: String, modalidad: String?): Map<CampoDonacion, TextoUi> = buildMap {
        validarTexto(titulo, LARGO_TITULO, obligatorio = true)?.let { put(CampoDonacion.TITULO, it) }
        validarTexto(direccion, LARGO_DIRECCION, obligatorio = false)?.let { put(CampoDonacion.DIRECCION, it) }
        if (modalidad == null) put(CampoDonacion.MODALIDAD, TextoUi.Recurso(R.string.error_modalidad))
    }
}
