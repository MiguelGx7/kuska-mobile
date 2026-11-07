package com.proyecto.kuska.ui.detallesolicitud

import com.proyecto.kuska.data.api.modelos.ConfirmacionEntrega
import com.proyecto.kuska.data.api.modelos.DetalleSolicitud
import com.proyecto.kuska.data.api.modelos.MiSolicitud

/** Filas del detalle de una solicitud (un solo RecyclerView con varios tipos). */
sealed interface FilaSolicitud {
    val clave: String

    /** [aviso]: error de la última acción (cancelar). [motivo]: de rechazo o de cancelación (HU-056). */
    data class Encabezado(
        val solicitud: MiSolicitud,
        val direccionEntrega: String?,
        val motivo: MotivoSolicitud?,
        val aviso: String?,
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
     * Entrega y su confirmación, solo como texto. El paso 14 (confirmar "Recibido" / "No recibido") agrega aquí
     * sus campos y los botones de la tarjeta.
     */
    data class Entrega(val entrega: ConfirmacionEntrega, val mostrarPlazo: Boolean) : FilaSolicitud {
        override val clave = "entrega"
    }

    /** "Cancelar solicitud" (solo Pendiente, RN-23). */
    data class Cancelar(val cancelando: Boolean, val habilitado: Boolean) : FilaSolicitud {
        override val clave = "cancelar"
    }
}

/** Encabezado, donante, entrega (si llegó), alimentos y, si está Pendiente, "Cancelar solicitud". */
fun construirFilasSolicitud(
    estado: DetalleSolicitudEstado,
    tituloEntrega: Int,
    tituloAlimentos: Int,
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
            add(FilaSolicitud.Entrega(it, ReglasSolicitud.mostrarPlazoConfirmacion(it)))
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
