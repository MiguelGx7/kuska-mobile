package com.proyecto.kuska.data.api.modelos

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("correo") val correo: String,
    @SerializedName("contrasena") val contrasena: String,
)

data class LoginResponse(
    @SerializedName("access") val access: String,
    @SerializedName("refresh") val refresh: String,
    @SerializedName("usuario") val usuario: Usuario,
)

data class RefreshRequest(
    @SerializedName("refresh") val refresh: String,
)

data class RefreshResponse(
    @SerializedName("access") val access: String,
    @SerializedName("refresh") val refresh: String?,
)

data class SolicitarCodigoRequest(
    @SerializedName("correo") val correo: String,
)

data class MensajeResponse(
    @SerializedName("mensaje") val mensaje: String?,
)

data class RecuperarConfirmarRequest(
    @SerializedName("correo") val correo: String,
    @SerializedName("codigo") val codigo: String,
    @SerializedName("contrasena_nueva") val contrasenaNueva: String,
)

/** Registro público (RN-04d). Los campos null no se envían (Gson los omite). */
data class RegistroRequest(
    @SerializedName("correo") val correo: String,
    @SerializedName("contrasena") val contrasena: String,
    @SerializedName("rol") val rol: String,
    @SerializedName("nombres") val nombres: String,
    @SerializedName("apellidos") val apellidos: String,
    @SerializedName("tipo_documento") val tipoDocumento: String,
    @SerializedName("numero_documento") val numeroDocumento: String,
    @SerializedName("telefono") val telefono: String,
    @SerializedName("direccion") val direccion: String?,
    @SerializedName("tipo_donante") val tipoDonante: String?,
    @SerializedName("nombre_negocio") val nombreNegocio: String?,
    @SerializedName("nit") val nit: String?,
    @SerializedName("codigo") val codigo: String,
) {
    companion object {
        /** TipoDocumentoEnum del schema, en el orden en que se muestran. */
        val TIPOS_DOCUMENTO = listOf("CC", "CE", "PAS", "PPT")

        const val TIPO_DONANTE_PERSONA = "Persona"
        const val TIPO_DONANTE_NEGOCIO = "Negocio"
    }
}
