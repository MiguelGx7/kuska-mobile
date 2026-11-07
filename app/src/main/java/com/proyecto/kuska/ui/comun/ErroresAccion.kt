package com.proyecto.kuska.ui.comun

import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.util.Resultado

/** Errores de una respuesta repartidos entre los campos que conoce la pantalla y el resto (aviso rojo). */
data class ErroresSeparados<C>(
    val porCampo: Map<C, String>,
    val generales: List<String>,
)

object ErroresAccion {

    /** [campos]: clave de la API → campo de la pantalla. Las claves que no están ahí van a [ErroresSeparados.generales]. */
    fun <C> separar(errores: Map<String, String>, campos: Map<String, C>): ErroresSeparados<C> {
        val porCampo = linkedMapOf<C, String>()
        val generales = mutableListOf<String>()
        errores.forEach { (clave, mensaje) ->
            val campo = campos[clave]
            if (campo != null) porCampo.putIfAbsent(campo, mensaje) else generales += mensaje
        }
        return ErroresSeparados(porCampo, generales)
    }

    /** Mensaje del aviso rojo: el primer error general o, si no hubo errores por clave, el mensaje de la respuesta. */
    fun aviso(error: Resultado.Error, separados: ErroresSeparados<*>): String? = when {
        separados.generales.isNotEmpty() -> separados.generales.first()
        separados.porCampo.isEmpty() -> error.mensaje
        else -> null
    }

    /**
     * Un 400 sin errores de campo (una regla de negocio, p. ej. "ya no está Pendiente") o un 404 (ya no existe)
     * pueden significar que el administrador revisó la donación mientras tanto: hay que volver a consultarla.
     * Sin código (sin conexión), 401, 403, 429 o 5xx no dicen nada del estado de la donación.
     */
    fun debeRevisarEstado(error: Resultado.Error): Boolean = when (error.codigo) {
        404 -> true
        400 -> error.erroresCampo.isEmpty()
        else -> false
    }

    /**
     * Resultado de volver a pedir la donación. Si ya no está Pendiente o ya no existe (404), no se puede seguir
     * editando. Si la consulta falla por otra razón (p. ej. sin conexión) se supone que sí, y el formulario se queda.
     */
    fun sigueEditable(consulta: Resultado<Donacion>): Boolean = when (consulta) {
        is Resultado.Exito -> consulta.datos.estado == Donacion.ESTADO_PENDIENTE
        is Resultado.Error -> consulta.codigo != 404
    }
}
