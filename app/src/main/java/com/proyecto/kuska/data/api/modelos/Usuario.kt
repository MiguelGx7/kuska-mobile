package com.proyecto.kuska.data.api.modelos

import com.google.gson.annotations.SerializedName

data class Usuario(
    @SerializedName("id") val id: Int,
    @SerializedName("correo") val correo: String,
    @SerializedName("rol") val rol: String,
    @SerializedName("nombres") val nombres: String?,
    @SerializedName("apellidos") val apellidos: String?,
    @SerializedName("estado") val estado: String?,
    @SerializedName("tipo_donante") val tipoDonante: String?,
    @SerializedName("nombre_negocio") val nombreNegocio: String?,
) {
    companion object {
        const val ROL_DONANTE = "Donante"
        const val ROL_BENEFICIARIO = "Beneficiario"
        const val ROL_ADMINISTRADOR = "Administrador"
    }
}
