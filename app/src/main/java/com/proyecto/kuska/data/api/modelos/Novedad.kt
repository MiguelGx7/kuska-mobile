package com.proyecto.kuska.data.api.modelos

import com.google.gson.annotations.SerializedName

/** Novedad del donante (RN-30 a RN-33): sobre un alimento ([donacionAlimento]) o sobre toda la donación (null). */
data class Novedad(
    @SerializedName("id") val id: Int,
    @SerializedName("donacion") val donacion: Int,
    @SerializedName("donacion_titulo") val donacionTitulo: String?,
    @SerializedName("donacion_alimento") val donacionAlimento: Int?,
    @SerializedName("alimento_nombre") val alimentoNombre: String?,
    @SerializedName("descripcion") val descripcion: String,
    @SerializedName("estado") val estado: String,
    @SerializedName("respuesta_admin") val respuestaAdmin: String?,
    @SerializedName("fecha_creacion") val fechaCreacion: String,
    @SerializedName("fecha_revision") val fechaRevision: String?,
) {
    companion object {
        // EstadoNovedadEnum
        const val ESTADO_PENDIENTE = "Pendiente"
        const val ESTADO_ACEPTADA = "Aceptada"
        const val ESTADO_RECHAZADA = "Rechazada"
    }
}

/** PaginatedNovedadList. */
data class PaginaNovedades(
    @SerializedName("count") val total: Int,
    @SerializedName("next") val siguiente: String?,
    @SerializedName("results") val resultados: List<Novedad>,
)

/** Cuerpo de POST novedades/. Gson omite el null: sin [donacionAlimento] la novedad es sobre toda la donación. */
data class NovedadCrearRequest(
    @SerializedName("donacion") val donacion: Int,
    @SerializedName("donacion_alimento") val donacionAlimento: Int?,
    @SerializedName("descripcion") val descripcion: String,
)
