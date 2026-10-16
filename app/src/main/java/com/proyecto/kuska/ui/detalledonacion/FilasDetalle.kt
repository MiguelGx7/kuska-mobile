package com.proyecto.kuska.ui.detalledonacion

import com.proyecto.kuska.data.api.modelos.Alimento
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.data.api.modelos.SolicitudRecibida

/** Filas de la lista del detalle (un solo RecyclerView con varios tipos). */
sealed interface FilaDetalle {
    val clave: String

    data class Encabezado(val donacion: Donacion) : FilaDetalle {
        override val clave = "encabezado"
    }

    data class Seccion(val titulo: Int) : FilaDetalle {
        override val clave = "seccion_$titulo"
    }

    data class AlimentoFila(val alimento: Alimento) : FilaDetalle {
        override val clave = "alimento_${alimento.id}"
    }

    /** Nombre del alimento sobre sus solicitudes. */
    data class GrupoSolicitudes(val alimentoId: Int, val nombre: String) : FilaDetalle {
        override val clave = "grupo_$alimentoId"
    }

    data class SolicitudFila(val alimentoId: Int, val solicitud: SolicitudRecibida, val unidad: String) : FilaDetalle {
        override val clave = "solicitud_${alimentoId}_${solicitud.solicitud}"
    }

    /** Cargando, vacío o error de la sección de solicitudes. */
    data class MensajeSolicitudes(val cargando: Boolean, val mensaje: String?, val conReintentar: Boolean) : FilaDetalle {
        override val clave = "mensaje_solicitudes"
    }

    data class Eliminar(val eliminando: Boolean, val error: String?) : FilaDetalle {
        override val clave = "eliminar"
    }
}

/**
 * Arma las filas: encabezado, alimentos y, según el estado, las solicitudes recibidas (no Pendiente)
 * o el botón para eliminar (Pendiente). [sinSolicitudes] es el texto para cuando nadie ha pedido nada.
 */
fun construirFilas(
    estado: DetalleDonacionEstado,
    tituloAlimentos: Int,
    tituloSolicitudes: Int,
    sinSolicitudes: String,
): List<FilaDetalle> {
    val donacion = estado.donacion ?: return emptyList()
    return buildList {
        add(FilaDetalle.Encabezado(donacion))
        add(FilaDetalle.Seccion(tituloAlimentos))
        donacion.alimentos.orEmpty().forEach { add(FilaDetalle.AlimentoFila(it)) }

        if (donacion.estado == Donacion.ESTADO_PENDIENTE) {
            add(FilaDetalle.Eliminar(estado.eliminando, estado.errorEliminar))
            return@buildList
        }

        add(FilaDetalle.Seccion(tituloSolicitudes))
        val grupos = estado.solicitudes.orEmpty().filter { it.solicitudes.isNotEmpty() }
        when {
            grupos.isNotEmpty() -> grupos.forEach { grupo ->
                add(FilaDetalle.GrupoSolicitudes(grupo.alimento, grupo.nombre))
                grupo.solicitudes.forEach { add(FilaDetalle.SolicitudFila(grupo.alimento, it, grupo.unidadMedida)) }
            }
            estado.cargandoSolicitudes || estado.solicitudes == null && estado.errorSolicitudes == null ->
                add(FilaDetalle.MensajeSolicitudes(cargando = true, mensaje = null, conReintentar = false))
            estado.errorSolicitudes != null ->
                add(FilaDetalle.MensajeSolicitudes(cargando = false, mensaje = estado.errorSolicitudes, conReintentar = true))
            else -> add(FilaDetalle.MensajeSolicitudes(cargando = false, mensaje = sinSolicitudes, conReintentar = false))
        }
    }
}
