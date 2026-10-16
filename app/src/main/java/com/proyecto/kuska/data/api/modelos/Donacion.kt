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

/** Respuesta 201 de POST donaciones/. Solo se leen los campos que usa la app. */
data class Donacion(
    @SerializedName("id") val id: Int,
    @SerializedName("titulo") val titulo: String,
    @SerializedName("estado") val estado: String,
) {
    companion object {
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
