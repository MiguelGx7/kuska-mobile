package com.proyecto.kuska.data.api.modelos

import com.google.gson.annotations.SerializedName

/**
 * MiSolicitud: una solicitud del beneficiario (una por donación de cada pedido). La lista y el detalle devuelven
 * lo mismo. Los estados son los de [SolicitudRecibida] (EstadoSolicitudEnum).
 */
data class MiSolicitud(
    @SerializedName("id") val id: Int,
    @SerializedName("codigo_pedido") val codigoPedido: String,
    @SerializedName("estado") val estado: String,
    /** Recoger o Domicilio. */
    @SerializedName("modalidad_entrega") val modalidadEntrega: String,
    @SerializedName("direccion_entrega") val direccionEntrega: String?,
    @SerializedName("motivo_rechazo") val motivoRechazo: String?,
    @SerializedName("motivo_cancelacion") val motivoCancelacion: String?,
    @SerializedName("fecha_solicitud") val fechaSolicitud: String?,
    @SerializedName("fecha_gestion") val fechaGestion: String?,
    @SerializedName("fecha_limite_recogida") val fechaLimiteRecogida: String?,
    @SerializedName("donacion") val donacion: Int,
    @SerializedName("donacion_titulo") val donacionTitulo: String,
    /** RN-29: en Pendiente solo trae el nombre; desde Aprobada, también teléfono y dirección de recogida. */
    @SerializedName("donante") val donante: DonanteContacto?,
    @SerializedName("detalles") val detalles: List<DetalleSolicitud>?,
    /** Solo si fue Entregada (RN-26b). */
    @SerializedName("entrega") val entrega: ConfirmacionEntrega?,
) {
    companion object {
        /** Orden de los chips del filtro. */
        val ESTADOS = listOf(
            SolicitudRecibida.ESTADO_PENDIENTE,
            SolicitudRecibida.ESTADO_APROBADA,
            SolicitudRecibida.ESTADO_ENTREGADA,
            SolicitudRecibida.ESTADO_NO_RECOGIDA,
            SolicitudRecibida.ESTADO_RECHAZADA,
            SolicitudRecibida.ESTADO_CANCELADA,
        )

        // ModalidadSolicitudEnum
        const val MODALIDAD_RECOGER = "Recoger"
        const val MODALIDAD_DOMICILIO = "Domicilio"
    }
}

/** Un alimento pedido. [cancelado]: se canceló solo este alimento (p. ej. por una novedad). */
data class DetalleSolicitud(
    @SerializedName("id") val id: Int,
    @SerializedName("alimento") val alimento: Int,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("unidad_medida") val unidadMedida: String,
    @SerializedName("cantidad") val cantidad: String,
    @SerializedName("cancelado") val cancelado: Boolean,
    @SerializedName("foto_url") val fotoUrl: String?,
)

data class DonanteContacto(
    @SerializedName("nombre") val nombre: String,
    @SerializedName("telefono") val telefono: String?,
    @SerializedName("direccion_recogida") val direccionRecogida: String?,
)

/** Entrega con su confirmación, como la ve el beneficiario (RN-26b). Estados en SolicitudRecibida.CONFIRMACION_*. */
data class ConfirmacionEntrega(
    @SerializedName("fecha_entrega") val fechaEntrega: String?,
    @SerializedName("observacion") val observacion: String?,
    @SerializedName("estado_confirmacion") val estadoConfirmacion: String?,
    /** 48 h desde la entrega. */
    @SerializedName("confirmar_hasta") val confirmarHasta: String?,
    @SerializedName("fecha_confirmacion") val fechaConfirmacion: String?,
    @SerializedName("comentario_beneficiario") val comentarioBeneficiario: String?,
    @SerializedName("respuesta_admin") val respuestaAdmin: String?,
    @SerializedName("fecha_revision") val fechaRevision: String?,
)

/** PaginatedMiSolicitudList. */
data class PaginaMisSolicitudes(
    @SerializedName("count") val total: Int,
    @SerializedName("next") val siguiente: String?,
    @SerializedName("results") val resultados: List<MiSolicitud>,
)
