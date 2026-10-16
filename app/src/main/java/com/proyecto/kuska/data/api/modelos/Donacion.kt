package com.proyecto.kuska.data.api.modelos

import com.google.gson.annotations.SerializedName

/** GET categorias/: solo devuelve las activas (RN-17). */
data class CategoriaPublica(
    @SerializedName("id") val id: Int,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("descripcion") val descripcion: String?,
)

/** GET auth/perfil/. Solo se leen los campos que usa la app. */
data class Perfil(
    @SerializedName("id") val id: Int,
    @SerializedName("direccion") val direccion: String?,
)

/**
 * Campo "datos" de POST donaciones/ (multipart). Las fotos van aparte como foto_0, foto_1...
 * en el mismo orden de [alimentos]. Gson omite los null, así que los opcionales vacíos no se envían.
 */
data class DatosDonacion(
    @SerializedName("titulo") val titulo: String,
    @SerializedName("descripcion") val descripcion: String?,
    @SerializedName("direccion_recogida") val direccionRecogida: String?,
    @SerializedName("modalidad_entrega") val modalidadEntrega: String,
    @SerializedName("alimentos") val alimentos: List<DatosAlimento>,
)

data class DatosAlimento(
    @SerializedName("categoria") val categoria: Int,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("descripcion") val descripcion: String?,
    @SerializedName("unidad_medida") val unidadMedida: String,
    /** Decimal como texto, con punto: "5", "2.5". */
    @SerializedName("cantidad_total") val cantidadTotal: String,
    /** yyyy-MM-dd. */
    @SerializedName("fecha_vencimiento") val fechaVencimiento: String,
)

/** Donación del donante (GET donaciones/, GET donaciones/{id}/ y respuesta de POST donaciones/). */
data class Donacion(
    @SerializedName("id") val id: Int,
    @SerializedName("titulo") val titulo: String,
    @SerializedName("descripcion") val descripcion: String?,
    @SerializedName("direccion_recogida") val direccionRecogida: String?,
    @SerializedName("modalidad_entrega") val modalidadEntrega: String,
    @SerializedName("estado") val estado: String,
    @SerializedName("motivo_rechazo") val motivoRechazo: String?,
    @SerializedName("fecha_revision") val fechaRevision: String?,
    @SerializedName("fecha_registro") val fechaRegistro: String,
    @SerializedName("fecha_actualizacion") val fechaActualizacion: String?,
    @SerializedName("alimentos") val alimentos: List<Alimento>?,
) {
    companion object {
        // EstadoDonacionEnum
        const val ESTADO_PENDIENTE = "Pendiente"
        const val ESTADO_VALIDADA = "Validada"
        const val ESTADO_RECHAZADA = "Rechazada"
        const val ESTADO_AGOTADA = "Agotada"
        const val ESTADO_FINALIZADA = "Finalizada"

        val ESTADOS = listOf(ESTADO_PENDIENTE, ESTADO_VALIDADA, ESTADO_RECHAZADA, ESTADO_AGOTADA, ESTADO_FINALIZADA)

        // ModalidadDonacionEnum
        const val MODALIDAD_RECOGER = "Recoger"
        const val MODALIDAD_DOMICILIO = "Domicilio"
        const val MODALIDAD_AMBAS = "Ambas"

        // UnidadMedidaEnum
        const val UNIDAD_KG = "kg"
        const val UNIDAD_G = "g"
        const val UNIDAD_L = "L"
        const val UNIDAD_ML = "ml"
        const val UNIDAD_UNIDAD = "unidad"
        const val UNIDAD_PAQUETE = "paquete"

        val UNIDADES = listOf(UNIDAD_KG, UNIDAD_G, UNIDAD_L, UNIDAD_ML, UNIDAD_UNIDAD, UNIDAD_PAQUETE)

        /** RN-10a: estas unidades solo admiten cantidades enteras. */
        val UNIDADES_ENTERAS = setOf(UNIDAD_UNIDAD, UNIDAD_PAQUETE)
    }
}

/** Alimento de una donación. Las cantidades son decimales como texto ("5.00"). */
data class Alimento(
    @SerializedName("id") val id: Int,
    @SerializedName("categoria") val categoria: Int,
    @SerializedName("categoria_nombre") val categoriaNombre: String?,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("descripcion") val descripcion: String?,
    @SerializedName("unidad_medida") val unidadMedida: String,
    @SerializedName("cantidad_total") val cantidadTotal: String,
    @SerializedName("cantidad_disponible") val cantidadDisponible: String,
    @SerializedName("max_por_solicitud") val maxPorSolicitud: String?,
    /** yyyy-MM-dd. */
    @SerializedName("fecha_vencimiento") val fechaVencimiento: String,
    @SerializedName("foto_url") val fotoUrl: String?,
    @SerializedName("activo") val activo: Boolean,
)

/** PaginatedDonacionList. */
data class PaginaDonaciones(
    @SerializedName("count") val total: Int,
    @SerializedName("next") val siguiente: String?,
    @SerializedName("results") val resultados: List<Donacion>,
)

/** GET donaciones/{id}/solicitudes-recibidas/: las solicitudes de cada alimento (RN-15). */
data class AlimentoConSolicitudes(
    @SerializedName("alimento") val alimento: Int,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("unidad_medida") val unidadMedida: String,
    @SerializedName("cantidad_total") val cantidadTotal: String,
    @SerializedName("cantidad_disponible") val cantidadDisponible: String,
    @SerializedName("solicitudes") val solicitudes: List<SolicitudRecibida>,
)

data class SolicitudRecibida(
    @SerializedName("solicitud") val solicitud: Int,
    @SerializedName("codigo_pedido") val codigoPedido: String,
    @SerializedName("estado") val estado: String,
    @SerializedName("modalidad_entrega") val modalidadEntrega: String?,
    @SerializedName("fecha_solicitud") val fechaSolicitud: String?,
    @SerializedName("fecha_limite_recogida") val fechaLimiteRecogida: String?,
    @SerializedName("cantidad") val cantidad: String,
    @SerializedName("cancelado") val cancelado: Boolean,
    @SerializedName("beneficiario") val beneficiario: BeneficiarioContacto,
    @SerializedName("estado_confirmacion") val estadoConfirmacion: String?,
) {
    companion object {
        // EstadoSolicitudEnum
        const val ESTADO_PENDIENTE = "Pendiente"
        const val ESTADO_APROBADA = "Aprobada"
        const val ESTADO_RECHAZADA = "Rechazada"
        const val ESTADO_ENTREGADA = "Entregada"
        const val ESTADO_NO_RECOGIDA = "No recogida"
        const val ESTADO_CANCELADA = "Cancelada"
    }
}

/** Teléfono y dirección solo llegan si la solicitud está Aprobada, Entregada o No recogida (RN-15). */
data class BeneficiarioContacto(
    @SerializedName("nombre") val nombre: String,
    @SerializedName("telefono") val telefono: String?,
    @SerializedName("direccion") val direccion: String?,
)
