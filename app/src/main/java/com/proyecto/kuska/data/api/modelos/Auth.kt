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
