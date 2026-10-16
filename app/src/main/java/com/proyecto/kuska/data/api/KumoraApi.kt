package com.proyecto.kuska.data.api

import com.proyecto.kuska.data.api.modelos.LoginRequest
import com.proyecto.kuska.data.api.modelos.LoginResponse
import com.proyecto.kuska.data.api.modelos.MensajeResponse
import com.proyecto.kuska.data.api.modelos.RecuperarConfirmarRequest
import com.proyecto.kuska.data.api.modelos.RefreshRequest
import com.proyecto.kuska.data.api.modelos.RefreshResponse
import com.proyecto.kuska.data.api.modelos.RegistroRequest
import com.proyecto.kuska.data.api.modelos.SolicitarCodigoRequest
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** Endpoints de la API de Kumora. Se irán agregando con cada pantalla. */
interface KumoraApi {

    @POST("auth/login/")
    suspend fun login(@Body cuerpo: LoginRequest): Response<LoginResponse>

    @POST("auth/registro/solicitar-codigo/")
    suspend fun solicitarCodigoRegistro(@Body cuerpo: SolicitarCodigoRequest): Response<MensajeResponse>

    /** Responde siempre lo mismo, exista o no el correo (RN-04b). */
    @POST("auth/recuperar/solicitar/")
    suspend fun solicitarRecuperacion(@Body cuerpo: SolicitarCodigoRequest): Response<MensajeResponse>

    @POST("auth/recuperar/confirmar/")
    suspend fun confirmarRecuperacion(@Body cuerpo: RecuperarConfirmarRequest): Response<MensajeResponse>

    /** Responde lo mismo que el login (access, refresh y usuario). */
    @POST("auth/registro/")
    suspend fun registrar(@Body cuerpo: RegistroRequest): Response<LoginResponse>
}

/** Solo para el refresh de tokens. Usa un cliente sin interceptor de auth ni Authenticator. */
interface RefreshApi {

    @POST("auth/refresh/")
    fun refrescar(@Body cuerpo: RefreshRequest): Call<RefreshResponse>
}
