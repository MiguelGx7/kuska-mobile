package com.proyecto.kuska.ui.detalledonacion

import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.data.api.modelos.Novedad
import com.proyecto.kuska.data.api.modelos.NovedadCrearRequest
import com.proyecto.kuska.ui.comun.ErroresAccion
import com.proyecto.kuska.ui.comun.TextoUi
import com.proyecto.kuska.util.FormatoFecha
import com.proyecto.kuska.util.Resultado

/** Qué se muestra donde se puede reportar una novedad (encabezado o tarjeta de un alimento). */
enum class ModoNovedad {
    /** La donación no está Validada ni Agotada. */
    NO,

    /** Botón "Reportar novedad". */
    REPORTAR,

    /** Ya hay una Pendiente sobre eso (RN-30): "Novedad en revisión". */
    EN_REVISION,
}

/** Sobre qué se reporta: un alimento o, con [alimentoId] null, toda la donación. */
data class ObjetivoNovedad(val alimentoId: Int?)

/** Dónde se muestra un error al reportar una novedad. */
sealed interface FalloNovedad {
    /** Error de la descripción: en su campo, con el diálogo abierto. */
    data class EnCampo(val mensaje: String) : FalloNovedad

    /** Sin conexión o 429: dentro del diálogo, que sigue abierto para reintentar sin perder lo escrito. */
    data class EnDialogo(val mensaje: String) : FalloNovedad

    /** El resto (400 por regla, p. ej. una duplicada; 403, 404, 5xx): se cierra el diálogo y va al aviso rojo. */
    data class Aviso(val mensaje: String, val recargar: Boolean) : FalloNovedad
}

/** Novedades del donante (RN-30, RN-31). El servidor valida todo; esto solo ayuda. */
object NovedadDonacion {

    private const val CAMPO_DESCRIPCION = "descripcion"

    /** Solo una donación Validada o Agotada admite novedades nuevas. */
    fun permiteReportar(donacion: Donacion): Boolean =
        donacion.estado == Donacion.ESTADO_VALIDADA || donacion.estado == Donacion.ESTADO_AGOTADA

    /** Las novedades solo existen después de revisada: no se piden para una Pendiente. */
    fun tieneNovedades(donacion: Donacion): Boolean = donacion.estado != Donacion.ESTADO_PENDIENTE

    /**
     * [alimentoId] null = toda la donación. Máximo una Pendiente por alimento y una por toda la donación, y son
     * independientes. Con [novedades] null (no cargaron) se muestra el botón: el servidor decide.
     */
    fun modo(donacion: Donacion, novedades: List<Novedad>?, alimentoId: Int?): ModoNovedad = when {
        !permiteReportar(donacion) -> ModoNovedad.NO
        novedades.orEmpty().any { it.estado == Novedad.ESTADO_PENDIENTE && it.donacionAlimento == alimentoId } ->
            ModoNovedad.EN_REVISION
        else -> ModoNovedad.REPORTAR
    }

    /** Null si la descripción sirve. */
    fun validarDescripcion(descripcion: String): TextoUi? =
        if (descripcion.isBlank()) TextoUi.Recurso(R.string.novedad_error_descripcion) else null

    fun cuerpo(donacionId: Int, objetivo: ObjetivoNovedad, descripcion: String) =
        NovedadCrearRequest(donacionId, objetivo.alimentoId, descripcion.trim())

    /** La más reciente primero; las fechas que no se pueden leer, al final. */
    fun ordenar(novedades: List<Novedad>): List<Novedad> =
        novedades.sortedByDescending { FormatoFecha.milisDeFechaHora(it.fechaCreacion) ?: Long.MIN_VALUE }

    fun clasificar(error: Resultado.Error): FalloNovedad {
        val separados = ErroresAccion.separar(error.erroresCampo, mapOf(CAMPO_DESCRIPCION to CAMPO_DESCRIPCION))
        separados.porCampo[CAMPO_DESCRIPCION]?.let { return FalloNovedad.EnCampo(it) }
        if (error.codigo == null || error.codigo == 429) return FalloNovedad.EnDialogo(error.mensaje)
        return FalloNovedad.Aviso(
            mensaje = ErroresAccion.aviso(error, separados) ?: error.mensaje,
            recargar = ErroresAccion.debeRevisarEstado(error),
        )
    }
}
