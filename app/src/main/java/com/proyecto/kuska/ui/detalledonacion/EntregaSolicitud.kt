package com.proyecto.kuska.ui.detalledonacion

import com.proyecto.kuska.data.api.modelos.EntregarRequest
import com.proyecto.kuska.data.api.modelos.SolicitudRecibida
import com.proyecto.kuska.ui.comun.ErroresAccion
import com.proyecto.kuska.util.Resultado

/** Dónde se muestra un error al registrar una entrega. */
sealed interface FalloEntrega {
    /** Error de la observación: en su campo, con el diálogo abierto. */
    data class EnCampo(val mensaje: String) : FalloEntrega

    /** Sin conexión o 429: dentro del diálogo, que sigue abierto para reintentar sin perder la observación. */
    data class EnDialogo(val mensaje: String) : FalloEntrega

    /** El resto (400 por regla, 403, 404, 5xx): se cierra el diálogo y va al aviso rojo del encabezado. */
    data class Aviso(val mensaje: String, val recargar: Boolean) : FalloEntrega
}

/** Registrar entrega desde las solicitudes recibidas (RN-26). La entrega es total. */
object EntregaSolicitud {

    const val MAX_OBSERVACION = 255
    private const val CAMPO_OBSERVACION = "observacion"

    /** Solo una solicitud Aprobada que no esté cancelada. */
    fun puedeRegistrar(solicitud: SolicitudRecibida): Boolean =
        solicitud.estado == SolicitudRecibida.ESTADO_APROBADA && !solicitud.cancelado

    /** La observación sin espacios sobrantes; vacía no se envía. */
    fun cuerpo(observacion: String): EntregarRequest =
        EntregarRequest(observacion.trim().takeIf { it.isNotEmpty() })

    fun clasificar(error: Resultado.Error): FalloEntrega {
        val separados = ErroresAccion.separar(error.erroresCampo, mapOf(CAMPO_OBSERVACION to CAMPO_OBSERVACION))
        separados.porCampo[CAMPO_OBSERVACION]?.let { return FalloEntrega.EnCampo(it) }
        if (error.codigo == null || error.codigo == 429) return FalloEntrega.EnDialogo(error.mensaje)
        return FalloEntrega.Aviso(
            mensaje = ErroresAccion.aviso(error, separados) ?: error.mensaje,
            recargar = ErroresAccion.debeRevisarEstado(error),
        )
    }
}
