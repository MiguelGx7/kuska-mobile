package com.proyecto.kuska.ui.cambiarcontrasena

import com.proyecto.kuska.R
import com.proyecto.kuska.data.repositorio.PerfilRepositorio
import com.proyecto.kuska.ui.comun.ErroresAccion
import com.proyecto.kuska.ui.comun.TextoUi
import com.proyecto.kuska.ui.comun.validarConfirmacion
import com.proyecto.kuska.ui.comun.validarContrasenaNueva
import com.proyecto.kuska.util.Resultado

enum class CampoContrasena { ACTUAL, NUEVA, CONFIRMAR }

/** Errores de una respuesta: los de cada campo y el del aviso rojo. */
data class ErroresContrasena(val porCampo: Map<CampoContrasena, TextoUi>, val aviso: String?)

/**
 * Cambiar contraseña (HU-014). Las reglas de la nueva (mínimo 8, no solo números, no común, no parecida a tus
 * datos) las aplica el servidor: aquí solo que no estén vacías, el largo máximo y que la confirmación coincida.
 * Las contraseñas no se recortan.
 */
object ValidacionCambioContrasena {

    private val CAMPOS_API = mapOf(
        PerfilRepositorio.CAMPO_ACTUAL to CampoContrasena.ACTUAL,
        "contrasena_nueva" to CampoContrasena.NUEVA,
    )

    fun validar(actual: String, nueva: String, confirmar: String): Map<CampoContrasena, TextoUi> = buildMap {
        if (actual.isEmpty()) put(CampoContrasena.ACTUAL, TextoUi.Recurso(R.string.perfil_error_actual_vacia))
        validarContrasenaNueva(nueva)?.let { put(CampoContrasena.NUEVA, it) }
        validarConfirmacion(nueva, confirmar)?.let { put(CampoContrasena.CONFIRMAR, it) }
    }

    /** Errores de campo a su campo (también el 401 de la contraseña actual); el resto al aviso rojo. */
    fun repartir(error: Resultado.Error): ErroresContrasena {
        val separados = ErroresAccion.separar(error.erroresCampo, CAMPOS_API)
        return ErroresContrasena(
            porCampo = separados.porCampo.mapValues { (_, mensaje) -> TextoUi.Texto(mensaje) },
            aviso = ErroresAccion.aviso(error, separados),
        )
    }
}
