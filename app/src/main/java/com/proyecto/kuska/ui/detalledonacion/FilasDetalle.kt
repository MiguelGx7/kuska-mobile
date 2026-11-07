package com.proyecto.kuska.ui.detalledonacion

import com.proyecto.kuska.data.api.modelos.Alimento
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.data.api.modelos.SolicitudRecibida

/** Qué se muestra en la tarjeta de un alimento para quitarlo (solo en una donación Pendiente). */
enum class ModoQuitar {
    /** La donación ya no está Pendiente. */
    NO,

    /** Botón "Quitar". */
    PERMITIDO,

    /** Es el único alimento: se explica que hay que eliminar la donación. */
    ULTIMO,
}

/** Filas de la lista del detalle (un solo RecyclerView con varios tipos). */
sealed interface FilaDetalle {
    val clave: String

    /** [editable]: muestra "Editar datos". [aviso]: aviso rojo de la última acción que falló. */
    data class Encabezado(
        val donacion: Donacion,
        val editable: Boolean,
        val ocupado: Boolean,
        val aviso: String?,
    ) : FilaDetalle {
        override val clave = "encabezado"
    }

    data class Seccion(val titulo: Int) : FilaDetalle {
        override val clave = "seccion_$titulo"
    }

    /** [quitando]: se está quitando este alimento. [ocupado]: hay otra acción en curso (no se puede quitar). */
    data class AlimentoFila(
        val alimento: Alimento,
        val quitar: ModoQuitar = ModoQuitar.NO,
        val quitando: Boolean = false,
        val ocupado: Boolean = false,
    ) : FilaDetalle {
        override val clave = "alimento_${alimento.id}"
    }

    data class AgregarAlimento(val habilitado: Boolean) : FilaDetalle {
        override val clave = "agregar_alimento"
    }

    /** Nombre del alimento sobre sus solicitudes. */
    data class GrupoSolicitudes(val alimentoId: Int, val nombre: String) : FilaDetalle {
        override val clave = "grupo_$alimentoId"
    }

    /**
     * Una solicitud aparece en una tarjeta por cada alimento que pidió; todas registran la misma entrega total.
     * [entregable]: muestra "Registrar entrega". [entregando]: se está registrando la entrega de esta solicitud.
     * [ocupado]: hay una acción en curso (el botón queda deshabilitado).
     */
    data class SolicitudFila(
        val alimentoId: Int,
        val solicitud: SolicitudRecibida,
        val unidad: String,
        val entregable: Boolean = false,
        val entregando: Boolean = false,
        val ocupado: Boolean = false,
    ) : FilaDetalle {
        override val clave = "solicitud_${alimentoId}_${solicitud.solicitud}"
    }

    /** Cargando, vacío o error de la sección de solicitudes. */
    data class MensajeSolicitudes(val cargando: Boolean, val mensaje: String?, val conReintentar: Boolean) : FilaDetalle {
        override val clave = "mensaje_solicitudes"
    }

    data class Eliminar(val eliminando: Boolean, val habilitado: Boolean) : FilaDetalle {
        override val clave = "eliminar"
    }
}

/**
 * Arma las filas: encabezado, alimentos y, según el estado, las solicitudes recibidas (no Pendiente) o las
 * acciones de una donación Pendiente (editar, agregar y quitar alimentos, eliminar; RN-12).
 * [sinSolicitudes] es el texto para cuando nadie ha pedido nada.
 */
fun construirFilas(
    estado: DetalleDonacionEstado,
    tituloAlimentos: Int,
    tituloSolicitudes: Int,
    sinSolicitudes: String,
): List<FilaDetalle> {
    val donacion = estado.donacion ?: return emptyList()
    val pendiente = donacion.estado == Donacion.ESTADO_PENDIENTE
    val ocupado = estado.ocupado
    val alimentos = donacion.alimentos.orEmpty()
    return buildList {
        add(FilaDetalle.Encabezado(donacion, editable = pendiente, ocupado = ocupado, aviso = estado.aviso))
        add(FilaDetalle.Seccion(tituloAlimentos))
        val modo = when {
            !pendiente -> ModoQuitar.NO
            alimentos.size > 1 -> ModoQuitar.PERMITIDO
            else -> ModoQuitar.ULTIMO
        }
        alimentos.forEach {
            add(FilaDetalle.AlimentoFila(it, modo, quitando = estado.quitandoId == it.id, ocupado = ocupado))
        }

        if (pendiente) {
            add(FilaDetalle.AgregarAlimento(habilitado = !ocupado))
            add(FilaDetalle.Eliminar(estado.eliminando, habilitado = !ocupado))
            return@buildList
        }

        add(FilaDetalle.Seccion(tituloSolicitudes))
        val grupos = estado.solicitudes.orEmpty().filter { it.solicitudes.isNotEmpty() }
        when {
            grupos.isNotEmpty() -> grupos.forEach { grupo ->
                add(FilaDetalle.GrupoSolicitudes(grupo.alimento, grupo.nombre))
                grupo.solicitudes.forEach {
                    add(
                        FilaDetalle.SolicitudFila(
                            grupo.alimento, it, grupo.unidadMedida,
                            entregable = EntregaSolicitud.puedeRegistrar(it),
                            entregando = estado.entregandoId == it.solicitud,
                            ocupado = ocupado,
                        )
                    )
                }
            }
            estado.cargandoSolicitudes || estado.solicitudes == null && estado.errorSolicitudes == null ->
                add(FilaDetalle.MensajeSolicitudes(cargando = true, mensaje = null, conReintentar = false))
            estado.errorSolicitudes != null ->
                add(FilaDetalle.MensajeSolicitudes(cargando = false, mensaje = estado.errorSolicitudes, conReintentar = true))
            else -> add(FilaDetalle.MensajeSolicitudes(cargando = false, mensaje = sinSolicitudes, conReintentar = false))
        }
    }
}
