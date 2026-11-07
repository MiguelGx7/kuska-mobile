package com.proyecto.kuska.data.api.modelos

import com.google.gson.annotations.SerializedName

/**
 * Notificación dentro de la app (RN-41). Las crea el backend; la app solo las lista y las marca como leídas.
 * [donacion] y [solicitud] dicen a qué lleva (pueden venir los dos o ninguno).
 */
data class Notificacion(
    @SerializedName("id") val id: Int,
    @SerializedName("tipo") val tipo: String,
    @SerializedName("titulo") val titulo: String,
    @SerializedName("mensaje") val mensaje: String,
    @SerializedName("donacion") val donacion: Int?,
    @SerializedName("solicitud") val solicitud: Int?,
    @SerializedName("leida") val leida: Boolean,
    @SerializedName("fecha") val fecha: String?,
) {
    companion object {
        // TipoNotificacionEnum
        const val TIPO_NUEVA_DONACION = "nueva_donacion"
        const val TIPO_DONACION_VALIDADA = "donacion_validada"
        const val TIPO_DONACION_RECHAZADA = "donacion_rechazada"
        const val TIPO_NUEVA_SOLICITUD = "nueva_solicitud"
        const val TIPO_SOLICITUD_APROBADA = "solicitud_aprobada"
        const val TIPO_SOLICITUD_RECHAZADA = "solicitud_rechazada"
        const val TIPO_SOLICITUD_CANCELADA = "solicitud_cancelada"
        const val TIPO_ALIMENTO_CANCELADO = "alimento_cancelado"
        const val TIPO_SOLICITUD_ENTREGADA = "solicitud_entregada"
        const val TIPO_SOLICITUD_NO_RECOGIDA = "solicitud_no_recogida"
        const val TIPO_NOVEDAD_CREADA = "novedad_creada"
        const val TIPO_NOVEDAD_ACEPTADA = "novedad_aceptada"
        const val TIPO_NOVEDAD_RECHAZADA = "novedad_rechazada"
        const val TIPO_SANCION = "sancion"
        const val TIPO_ENTREGA_RECLAMADA = "entrega_reclamada"
        const val TIPO_RECLAMO_REVISADO = "reclamo_revisado"
    }
}

/** PaginatedNotificacionList. [noLeidas]: total del usuario, sin importar filtros ni página (HU-120). */
data class PaginaNotificaciones(
    @SerializedName("count") val total: Int,
    @SerializedName("next") val siguiente: String?,
    @SerializedName("results") val resultados: List<Notificacion>,
    @SerializedName("no_leidas") val noLeidas: Int,
)

/** Respuesta de "Marcar todas como leídas". */
data class LeerTodas(
    @SerializedName("marcadas") val marcadas: Int,
    @SerializedName("no_leidas") val noLeidas: Int,
)
