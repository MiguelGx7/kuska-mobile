package com.proyecto.kuska.data.api.modelos

import com.google.gson.annotations.SerializedName

/**
 * GET alimentos-disponibles/: lo que ve el beneficiario (RN-14). Nunca trae el teléfono ni la dirección del donante
 * (RN-29). Las cantidades son decimales como texto ("5.00").
 */
data class AlimentoDisponible(
    @SerializedName("id") val id: Int,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("descripcion") val descripcion: String?,
    @SerializedName("categoria") val categoria: Int,
    @SerializedName("categoria_nombre") val categoriaNombre: String?,
    @SerializedName("unidad_medida") val unidadMedida: String,
    @SerializedName("cantidad_disponible") val cantidadDisponible: String,
    @SerializedName("max_por_solicitud") val maxPorSolicitud: String?,
    /** yyyy-MM-dd. */
    @SerializedName("fecha_vencimiento") val fechaVencimiento: String,
    @SerializedName("foto_url") val fotoUrl: String?,
    @SerializedName("donacion") val donacion: Int,
    @SerializedName("donacion_titulo") val donacionTitulo: String?,
    @SerializedName("modalidad_entrega") val modalidadEntrega: String,
    @SerializedName("donante_nombre") val donanteNombre: String?,
)

/** PaginatedAlimentoDisponibleList. */
data class PaginaAlimentosDisponibles(
    @SerializedName("count") val total: Int,
    @SerializedName("next") val siguiente: String?,
    @SerializedName("results") val resultados: List<AlimentoDisponible>,
)

/** POST carrito/. Si el alimento ya está en el carrito, la API actualiza la cantidad (RN-20). */
data class CarritoAgregarRequest(
    @SerializedName("donacion_alimento") val donacionAlimento: Int,
    /** Decimal como texto, con punto: "2", "2.5". */
    @SerializedName("cantidad") val cantidad: String,
)

/** Respuesta de POST carrito/. Solo se leen los campos que usa la app. */
data class CarritoItem(
    @SerializedName("id") val id: Int,
    @SerializedName("cantidad") val cantidad: String,
)
