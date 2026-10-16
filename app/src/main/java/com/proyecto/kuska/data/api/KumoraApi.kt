package com.proyecto.kuska.data.api

import com.proyecto.kuska.data.api.modelos.LoginRequest
import com.proyecto.kuska.data.api.modelos.LoginResponse
import com.proyecto.kuska.data.api.modelos.RefreshRequest
import com.proyecto.kuska.data.api.modelos.RefreshResponse
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** Endpoints de la API de Kumora. Se irán agregando con cada pantalla. */
interface KumoraApi {

    @POST("auth/login/")
    suspend fun login(@Body cuerpo: LoginRequest): Response<LoginResponse>
}

/** Solo para el refresh de tokens. Usa un cliente sin interceptor de auth ni Authenticator. */
interface RefreshApi {

    @POST("auth/refresh/")
    fun refrescar(@Body cuerpo: RefreshRequest): Call<RefreshResponse>
}
