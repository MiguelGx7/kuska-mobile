package com.proyecto.kuska.data.api.modelos

import com.google.gson.annotations.SerializedName

/**
 * GET y PATCH auth/perfil/ (nunca trae la contraseña). Solo se editan nombres, apellidos, teléfono y dirección
 * (RN-04a). Crear donación también lo usa para proponer la dirección.
 */
data class Perfil(
    @SerializedName("id") val id: Int,
    @SerializedName("correo") val correo: String? = null,
    @SerializedName("rol") val rol: String? = null,
    @SerializedName("nombres") val nombres: String? = null,
    @SerializedName("apellidos") val apellidos: String? = null,
    @SerializedName("tipo_documento") val tipoDocumento: String? = null,
    @SerializedName("numero_documento") val numeroDocumento: String? = null,
    @SerializedName("telefono") val telefono: String? = null,
    @SerializedName("direccion") val direccion: String? = null,
    @SerializedName("tipo_donante") val tipoDonante: String? = null,
    @SerializedName("nombre_negocio") val nombreNegocio: String? = null,
    @SerializedName("nit") val nit: String? = null,
    /** ISO-8601 con hora. */
    @SerializedName("fecha_registro") val fechaRegistro: String? = null,
) {
    companion object {
        // TipoDonanteEnum
        const val TIPO_PERSONA = "Persona"
        const val TIPO_NEGOCIO = "Negocio"

        // TipoDocumentoEnum
        const val DOCUMENTO_CC = "CC"
        const val DOCUMENTO_CE = "CE"
        const val DOCUMENTO_PAS = "PAS"
        const val DOCUMENTO_PPT = "PPT"
    }
}

/** POST auth/perfil/cambiar-contrasena/. Las reglas de la contraseña nueva las aplica el servidor. */
data class CambiarContrasenaRequest(
    @SerializedName("contrasena_actual") val contrasenaActual: String,
    @SerializedName("contrasena_nueva") val contrasenaNueva: String,
)
