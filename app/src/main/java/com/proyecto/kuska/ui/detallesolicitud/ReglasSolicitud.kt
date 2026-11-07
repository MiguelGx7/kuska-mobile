package com.proyecto.kuska.ui.detallesolicitud

import com.proyecto.kuska.data.api.modelos.ConfirmacionEntrega
import com.proyecto.kuska.data.api.modelos.MiSolicitud
import com.proyecto.kuska.data.api.modelos.SolicitudRecibida

/**
 * Datos del donante que se muestran (RN-29). [destacado]: la solicitud está Aprobada y hay que ir a recoger.
 * [esperaAprobacion]: está Pendiente; solo se ve el nombre y se explica cuándo aparecen los demás datos.
 * [fechaLimite] es el texto de la API (fecha con hora).
 */
data class DatosDonante(
    val nombre: String,
    val telefono: String? = null,
    val direccion: String? = null,
    val fechaLimite: String? = null,
    val destacado: Boolean = false,
    val esperaAprobacion: Boolean = false,
)

/** Motivo que se muestra en el aviso rojo de una solicitud Rechazada o Cancelada. */
sealed interface MotivoSolicitud {
    val texto: String

    data class Rechazo(override val texto: String) : MotivoSolicitud
    data class Cancelacion(override val texto: String) : MotivoSolicitud
}

/** Reglas de lo que ve el beneficiario en sus solicitudes (HU-051 a HU-056). */
object ReglasSolicitud {

    /** RN-23: solo una Pendiente se puede cancelar. */
    fun puedeCancelar(solicitud: MiSolicitud): Boolean = solicitud.estado == SolicitudRecibida.ESTADO_PENDIENTE

    /** Alimentos que siguen en la solicitud (los cancelados no cuentan). */
    fun alimentosVigentes(solicitud: MiSolicitud): Int = solicitud.detalles.orEmpty().count { !it.cancelado }

    /**
     * RN-29: la API decide qué manda; aquí solo se muestra lo que llegó. En Pendiente, únicamente el nombre aunque
     * llegara algo más. La fecha límite solo tiene sentido después de aprobada.
     */
    fun datosDonante(solicitud: MiSolicitud): DatosDonante? {
        val donante = solicitud.donante ?: return null
        val nombre = donante.nombre.trim()
        if (solicitud.estado == SolicitudRecibida.ESTADO_PENDIENTE) {
            return DatosDonante(nombre, esperaAprobacion = true)
        }
        val datos = DatosDonante(
            nombre = nombre,
            telefono = donante.telefono.limpio(),
            direccion = donante.direccionRecogida.limpio(),
            fechaLimite = solicitud.fechaLimiteRecogida.limpio(),
            destacado = solicitud.estado == SolicitudRecibida.ESTADO_APROBADA,
        )
        // Sin nada que mostrar (ni nombre) no hay tarjeta.
        return datos.takeIf { nombre.isNotEmpty() || it.telefono != null || it.direccion != null || it.fechaLimite != null }
    }

    /** Número para el marcador ("tel:"): solo dígitos y un "+" inicial. Null si no queda ningún dígito. */
    fun numeroMarcable(telefono: String?): String? {
        val texto = telefono?.trim().orEmpty()
        val digitos = texto.filter { it.isDigit() }
        if (digitos.isEmpty()) return null
        return if (texto.startsWith("+")) "+$digitos" else digitos
    }

    /** HU-056: el motivo solo se muestra si la solicitud quedó Rechazada o Cancelada y la API lo manda. */
    fun motivo(solicitud: MiSolicitud): MotivoSolicitud? = when (solicitud.estado) {
        SolicitudRecibida.ESTADO_RECHAZADA -> solicitud.motivoRechazo.limpio()?.let { MotivoSolicitud.Rechazo(it) }
        SolicitudRecibida.ESTADO_CANCELADA -> solicitud.motivoCancelacion.limpio()?.let { MotivoSolicitud.Cancelacion(it) }
        else -> null
    }

    /** La dirección de entrega solo aplica a Domicilio. */
    fun direccionEntrega(solicitud: MiSolicitud): String? =
        solicitud.direccionEntrega.limpio().takeIf { solicitud.modalidadEntrega == MiSolicitud.MODALIDAD_DOMICILIO }

    /** "Puedes confirmar hasta…" solo mientras la confirmación sigue Pendiente (RN-26b). */
    fun mostrarPlazoConfirmacion(entrega: ConfirmacionEntrega): Boolean =
        entrega.estadoConfirmacion == SolicitudRecibida.CONFIRMACION_PENDIENTE && entrega.confirmarHasta.limpio() != null

    private fun String?.limpio(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
