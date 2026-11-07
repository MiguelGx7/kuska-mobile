package com.proyecto.kuska.ui.detallesolicitud

import androidx.annotation.StringRes
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.ConfirmacionEntrega
import com.proyecto.kuska.data.api.modelos.ConfirmarEntregaRequest
import com.proyecto.kuska.data.api.modelos.MiSolicitud
import com.proyecto.kuska.data.api.modelos.SolicitudRecibida
import com.proyecto.kuska.ui.comun.ErroresAccion
import com.proyecto.kuska.ui.comun.FamiliaColor
import com.proyecto.kuska.ui.comun.TextoUi
import com.proyecto.kuska.util.FormatoFecha
import com.proyecto.kuska.util.Resultado

/** Lo que responde el beneficiario sobre su pedido entregado (RN-26b). */
enum class RespuestaEntrega { RECIBIDO, RECLAMO }

/** Dónde va un error al responder. */
sealed interface FalloConfirmacion {
    /** Error del comentario: en su campo, con el diálogo de reclamo abierto. */
    data class EnCampo(val mensaje: String) : FalloConfirmacion

    /** Sin conexión o 429 en el reclamo: dentro del diálogo, que sigue abierto sin perder lo escrito. */
    data class EnDialogo(val mensaje: String) : FalloConfirmacion

    /** El resto (y todo error de "Lo recibí"): aviso rojo del encabezado; si [recargar], se vuelve a pedir el detalle. */
    data class Aviso(val mensaje: String, val recargar: Boolean) : FalloConfirmacion
}

/** Confirmar "Recibido" / "No recibido" (HU-057, HU-058) y ver la respuesta (HU-059). */
object ConfirmacionEntregaSolicitud {

    const val MAX_COMENTARIO = 255
    private const val CAMPO_COMENTARIO = "comentario"

    /**
     * RN-26b: solo una Entregada con la confirmación Pendiente y antes de confirmar_hasta. Sin una fecha que se
     * pueda leer no se ofrece responder (el servidor la pasará a Asumida).
     */
    fun puedeResponder(solicitud: MiSolicitud, ahora: Long): Boolean {
        if (solicitud.estado != SolicitudRecibida.ESTADO_ENTREGADA) return false
        val entrega = solicitud.entrega ?: return false
        val hasta = limite(entrega) ?: return false
        return entrega.estadoConfirmacion == SolicitudRecibida.CONFIRMACION_PENDIENTE && ahora < hasta
    }

    /** Sigue Pendiente pero las 48 h ya pasaron y el servidor todavía no la pasó a Asumida. */
    fun plazoVencido(entrega: ConfirmacionEntrega, ahora: Long): Boolean {
        val hasta = limite(entrega) ?: return false
        return entrega.estadoConfirmacion == SolicitudRecibida.CONFIRMACION_PENDIENTE && ahora >= hasta
    }

    fun cuerpoRecibido() = ConfirmarEntregaRequest(recibido = true)

    fun cuerpoReclamo(comentario: String) = ConfirmarEntregaRequest(recibido = false, comentario = comentario.trim())

    /** El comentario del reclamo es obligatorio y de máximo [MAX_COMENTARIO] caracteres. */
    fun validarComentario(comentario: String): TextoUi? {
        val texto = comentario.trim()
        return when {
            texto.isEmpty() -> TextoUi.Recurso(R.string.no_recibido_error_vacio)
            texto.length > MAX_COMENTARIO -> TextoUi.Recurso(R.string.error_largo_maximo, listOf(MAX_COMENTARIO))
            else -> null
        }
    }

    fun clasificar(error: Resultado.Error, respuesta: RespuestaEntrega): FalloConfirmacion {
        val separados = ErroresAccion.separar(error.erroresCampo, mapOf(CAMPO_COMENTARIO to CAMPO_COMENTARIO))
        if (respuesta == RespuestaEntrega.RECLAMO) {
            separados.porCampo[CAMPO_COMENTARIO]?.let { return FalloConfirmacion.EnCampo(it) }
            if (error.codigo == null || error.codigo == 429) return FalloConfirmacion.EnDialogo(error.mensaje)
        }
        return FalloConfirmacion.Aviso(
            mensaje = ErroresAccion.aviso(error, separados) ?: separados.porCampo.values.firstOrNull() ?: error.mensaje,
            recargar = ErroresAccion.debeRevisarEstado(error),
        )
    }

    fun familia(estado: String): FamiliaColor = when (estado) {
        SolicitudRecibida.CONFIRMACION_PENDIENTE -> FamiliaColor.NARANJA
        SolicitudRecibida.CONFIRMACION_CONFIRMADA, SolicitudRecibida.CONFIRMACION_ASUMIDA -> FamiliaColor.VERDE
        SolicitudRecibida.CONFIRMACION_RECLAMADA -> FamiliaColor.ROJO
        else -> FamiliaColor.GRIS // Revisada y los que la app no conoce
    }

    /** Null si la app no conoce el estado (se muestra el texto de la API). */
    @StringRes
    fun etiqueta(estado: String): Int? = when (estado) {
        SolicitudRecibida.CONFIRMACION_PENDIENTE -> R.string.confirmacion_pendiente
        SolicitudRecibida.CONFIRMACION_CONFIRMADA -> R.string.confirmacion_confirmada
        SolicitudRecibida.CONFIRMACION_ASUMIDA -> R.string.confirmacion_asumida
        SolicitudRecibida.CONFIRMACION_RECLAMADA -> R.string.confirmacion_reclamada
        SolicitudRecibida.CONFIRMACION_REVISADA -> R.string.confirmacion_revisada
        else -> null
    }

    private fun limite(entrega: ConfirmacionEntrega): Long? = FormatoFecha.milisDeFechaHora(entrega.confirmarHasta)
}
