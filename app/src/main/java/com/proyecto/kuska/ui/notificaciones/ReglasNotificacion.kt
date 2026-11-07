package com.proyecto.kuska.ui.notificaciones

import androidx.annotation.DrawableRes
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Notificacion
import com.proyecto.kuska.data.api.modelos.Usuario

/** A qué pantalla lleva una notificación. */
sealed interface DestinoNotificacion {
    /** Detalle de la donación (donante). */
    data class Donacion(val id: Int) : DestinoNotificacion

    /** Detalle de la solicitud (beneficiario). */
    data class Solicitud(val id: Int) : DestinoNotificacion
}

/** Reglas de las notificaciones (HU-119, HU-120). */
object ReglasNotificacion {

    private const val MAXIMO_CONTADOR = 99

    /** Texto del contador de la campana: null (sin número) si no hay no leídas; "99+" si pasa de 99. */
    fun textoContador(noLeidas: Int?): String? = when {
        noLeidas == null || noLeidas <= 0 -> null
        noLeidas > MAXIMO_CONTADOR -> "$MAXIMO_CONTADOR+"
        else -> noLeidas.toString()
    }

    /**
     * El donante va al detalle de la donación y el beneficiario al de la solicitud. Si no trae lo que su rol puede
     * abrir (p. ej. una sanción), null: solo se marca como leída.
     */
    fun destino(rol: String?, notificacion: Notificacion): DestinoNotificacion? = when (rol) {
        Usuario.ROL_DONANTE -> notificacion.donacion?.let { DestinoNotificacion.Donacion(it) }
        Usuario.ROL_BENEFICIARIO -> notificacion.solicitud?.let { DestinoNotificacion.Solicitud(it) }
        else -> null
    }

    /** Ícono por grupo de tipos (6 grupos y uno genérico para los tipos que la app no conoce). */
    @DrawableRes
    fun icono(tipo: String): Int = when (tipo) {
        Notificacion.TIPO_NUEVA_DONACION,
        Notificacion.TIPO_DONACION_VALIDADA,
        Notificacion.TIPO_DONACION_RECHAZADA -> R.drawable.ic_donaciones

        Notificacion.TIPO_NUEVA_SOLICITUD,
        Notificacion.TIPO_SOLICITUD_APROBADA,
        Notificacion.TIPO_SOLICITUD_RECHAZADA,
        Notificacion.TIPO_SOLICITUD_CANCELADA,
        Notificacion.TIPO_SOLICITUD_NO_RECOGIDA -> R.drawable.ic_pedidos

        Notificacion.TIPO_SOLICITUD_ENTREGADA -> R.drawable.ic_entrega

        Notificacion.TIPO_NOVEDAD_CREADA,
        Notificacion.TIPO_NOVEDAD_ACEPTADA,
        Notificacion.TIPO_NOVEDAD_RECHAZADA,
        Notificacion.TIPO_ALIMENTO_CANCELADO -> R.drawable.ic_novedad

        Notificacion.TIPO_ENTREGA_RECLAMADA,
        Notificacion.TIPO_RECLAMO_REVISADO -> R.drawable.ic_reclamo

        Notificacion.TIPO_SANCION -> R.drawable.ic_candado

        else -> R.drawable.ic_campana
    }

    /** La lista con la notificación [id] marcada como [leida] (las demás no cambian). */
    fun conLeida(lista: List<Notificacion>, id: Int, leida: Boolean): List<Notificacion> =
        lista.map { if (it.id == id && it.leida != leida) it.copy(leida = leida) else it }

    fun todasLeidas(lista: List<Notificacion>): List<Notificacion> =
        lista.map { if (it.leida) it else it.copy(leida = true) }
}
