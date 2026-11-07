package com.proyecto.kuska.data.api.modelos

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * GET carrito/ (RN-21): agrupado por donación. Cada ítem dice si sigue siendo válido y por qué no; [avisos] explica
 * si el carrito supera los límites y [puedeEnviar] lo resume todo. El carrito no reserva (RN-20).
 */
data class Carrito(
    @SerializedName("donaciones") val donaciones: List<CarritoGrupo>?,
    @SerializedName("avisos") val avisos: List<String>?,
    @SerializedName("puede_enviar") val puedeEnviar: Boolean,
) {
    val grupos: List<CarritoGrupo> get() = donaciones.orEmpty()
}

/** Los ítems de una donación: al enviar, cada grupo se vuelve una solicitud (RN-22). */
data class CarritoGrupo(
    @SerializedName("donacion") val donacion: Int,
    @SerializedName("titulo") val titulo: String?,
    /** Recoger, Domicilio o Ambas. Si es Ambas, el beneficiario elige al enviar. */
    @SerializedName("modalidad_entrega") val modalidadEntrega: String,
    @SerializedName("donante_nombre") val donanteNombre: String?,
    @SerializedName("items") val items: List<CarritoItem>?,
)

/** PATCH carrito/{id}/. Decimal como texto, con punto. */
data class CarritoCantidadRequest(
    @SerializedName("cantidad") val cantidad: String,
)

/** POST carrito/enviar/. Solo lleva las donaciones que necesitan elección (las "Ambas"). */
data class EnviarCarritoRequest(
    @SerializedName("donaciones") val donaciones: List<EleccionDonacion>,
)

/** La dirección solo va para Domicilio (null no se envía). */
data class EleccionDonacion(
    @SerializedName("donacion") val donacion: Int,
    @SerializedName("modalidad_entrega") val modalidadEntrega: String,
    @SerializedName("direccion_entrega") val direccionEntrega: String? = null,
)

/** Respuesta 201 de carrito/enviar/. Las solicitudes (MiSolicitud) se leen en el paso 13. */
data class PedidoEnviado(
    @SerializedName("codigo_pedido") val codigoPedido: String,
    @SerializedName("solicitudes") val solicitudes: List<JsonElement>?,
)
