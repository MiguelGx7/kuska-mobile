package com.proyecto.kuska.ui.detallesolicitud

import com.proyecto.kuska.data.api.modelos.ConfirmacionEntrega
import com.proyecto.kuska.data.api.modelos.DetalleSolicitud
import com.proyecto.kuska.data.api.modelos.MiSolicitud
import com.proyecto.kuska.ui.comun.TextoUi

/** Filas del detalle de una solicitud (un solo RecyclerView con varios tipos). */
sealed interface FilaSolicitud {
    val clave: String

    /** [aviso]: error de la última acción (cancelar o confirmar). [motivo]: de rechazo o de cancelación (HU-056). */
    data class Encabezado(
        val solicitud: MiSolicitud,
        val direccionEntrega: String?,
        val motivo: MotivoSolicitud?,
        val aviso: TextoUi?,
    ) : FilaSolicitud {
        override val clave = "encabezado"
    }

    /** HU-055 / RN-29. */
    data class Donante(val datos: DatosDonante) : FilaSolicitud {
        override val clave = "donante"
    }

    data class Seccion(val titulo: Int) : FilaSolicitud {
        override val clave = "seccion_$titulo"
    }

    /** [tachado]: el alimento se canceló (HU-056). */
    data class AlimentoFila(val detalle: DetalleSolicitud) : FilaSolicitud {
        override val clave = "alimento_${detalle.id}"
        val tachado get() = detalle.cancelado
    }

    /**
     * Entrega y su confirmación (RN-26b). [puedeResponder]: pregunta, plazo y los botones "Sí, lo recibí" /
     * "No me llegó". Si no, la confirmación como etiqueta de color; [plazoVencido]: sigue Pendiente pero ya no se
     * puede responder. [enviando]: botón con carga; [habilitado]: no hay otra acción en curso.
     */
    data class Entrega(
        val entrega: ConfirmacionEntrega,
        val puedeResponder: Boolean,
        val plazoVencido: Boolean = false,
        val enviando: RespuestaEntrega? = null,
        val habilitado: Boolean = true,
    ) : FilaSolicitud {
        override val clave = "entrega"
    }

    /** "Cancelar solicitud" (solo Pendiente, RN-23). */
    data class Cancelar(val cancelando: Boolean, val habilitado: Boolean) : FilaSolicitud {
        override val clave = "cancelar"
    }
}

/**
 * Encabezado, donante, entrega (si llegó), alimentos y, si está Pendiente, "Cancelar solicitud".
 * [ahora]: para saber si todavía se puede responder la entrega.
 */
fun construirFilasSolicitud(
    estado: DetalleSolicitudEstado,
    tituloEntrega: Int,
    tituloAlimentos: Int,
    ahora: Long = System.currentTimeMillis(),
): List<FilaSolicitud> {
    val solicitud = estado.solicitud ?: return emptyList()
    return buildList {
        add(
            FilaSolicitud.Encabezado(
                solicitud,
                direccionEntrega = ReglasSolicitud.direccionEntrega(solicitud),
                motivo = ReglasSolicitud.motivo(solicitud),
                aviso = estado.aviso,
            )
        )
        ReglasSolicitud.datosDonante(solicitud)?.let { add(FilaSolicitud.Donante(it)) }
        solicitud.entrega?.let {
            add(FilaSolicitud.Seccion(tituloEntrega))
            add(
                FilaSolicitud.Entrega(
                    entrega = it,
                    puedeResponder = ConfirmacionEntregaSolicitud.puedeResponder(solicitud, ahora),
                    plazoVencido = ConfirmacionEntregaSolicitud.plazoVencido(it, ahora),
                    enviando = estado.confirmando,
                    habilitado = !estado.ocupado,
                )
            )
        }
        val detalles = solicitud.detalles.orEmpty()
        if (detalles.isNotEmpty()) {
            add(FilaSolicitud.Seccion(tituloAlimentos))
            detalles.forEach { add(FilaSolicitud.AlimentoFila(it)) }
        }
        if (ReglasSolicitud.puedeCancelar(solicitud)) {
            add(FilaSolicitud.Cancelar(estado.cancelando, habilitado = !estado.ocupado))
        }
    }
}
