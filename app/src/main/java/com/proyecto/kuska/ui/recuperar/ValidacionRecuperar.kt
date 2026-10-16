package com.proyecto.kuska.ui.recuperar

import com.proyecto.kuska.ui.comun.TextoUi
import com.proyecto.kuska.ui.comun.validarCodigo
import com.proyecto.kuska.ui.comun.validarConfirmacion
import com.proyecto.kuska.ui.comun.validarContrasenaNueva
import com.proyecto.kuska.util.validarCorreo

enum class CampoRecuperar { CORREO, CODIGO, CONTRASENA_NUEVA, CONFIRMAR_CONTRASENA }

/** Validación local de recuperar contraseña (RN-04b). Las reglas de la contraseña las aplica el servidor. */
object ValidacionRecuperar {

    /** Paso 1. */
    fun validarPasoCorreo(correo: String): Map<CampoRecuperar, TextoUi> =
        validarCorreo(correo)?.let { mapOf(CampoRecuperar.CORREO to TextoUi.Recurso(it)) } ?: emptyMap()

    /** Paso 2. */
    fun validarPasoCambio(codigo: String, contrasena: String, confirmacion: String): Map<CampoRecuperar, TextoUi> =
        buildMap {
            validarCodigo(codigo)?.let { put(CampoRecuperar.CODIGO, it) }
            validarContrasenaNueva(contrasena)?.let { put(CampoRecuperar.CONTRASENA_NUEVA, it) }
            validarConfirmacion(contrasena, confirmacion)?.let { put(CampoRecuperar.CONFIRMAR_CONTRASENA, it) }
        }
}
