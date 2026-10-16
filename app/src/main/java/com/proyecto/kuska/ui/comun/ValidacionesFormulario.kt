package com.proyecto.kuska.ui.comun

import com.proyecto.kuska.R

/** Validaciones compartidas por el registro y la recuperación de contraseña. Devuelven el error o null. */

/** Largo máximo de la contraseña según el schema. Las demás reglas las aplica el servidor. */
const val LARGO_MAXIMO_CONTRASENA = 128

private val formatoCodigo = Regex("^\\d{6}$")

/** Código de verificación: exactamente 6 dígitos (se ignoran los espacios de los extremos). */
fun validarCodigo(codigo: String): TextoUi? =
    if (formatoCodigo.matches(codigo.trim())) null else TextoUi.Recurso(R.string.error_codigo_formato)

/** Contraseña nueva: obligatoria y sin pasar el largo máximo. No se recorta. */
fun validarContrasenaNueva(contrasena: String): TextoUi? = when {
    contrasena.isEmpty() -> TextoUi.Recurso(R.string.error_contrasena_vacia)
    contrasena.length > LARGO_MAXIMO_CONTRASENA ->
        TextoUi.Recurso(R.string.error_largo_maximo, listOf(LARGO_MAXIMO_CONTRASENA))
    else -> null
}

/** Confirmación (solo existe en la app): obligatoria e igual a la contraseña. */
fun validarConfirmacion(contrasena: String, confirmacion: String): TextoUi? = when {
    confirmacion.isEmpty() -> TextoUi.Recurso(R.string.error_confirmar_vacia)
    confirmacion != contrasena -> TextoUi.Recurso(R.string.error_contrasenas_distintas)
    else -> null
}
