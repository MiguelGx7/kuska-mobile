package com.proyecto.kuska.data.api

import com.proyecto.kuska.data.api.modelos.AlimentoConSolicitudes
import com.proyecto.kuska.data.api.modelos.CategoriaPublica
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.data.api.modelos.LoginRequest
import com.proyecto.kuska.data.api.modelos.LoginResponse
import com.proyecto.kuska.data.api.modelos.MensajeResponse
import com.proyecto.kuska.data.api.modelos.PaginaDonaciones
import com.proyecto.kuska.data.api.modelos.Perfil
import com.proyecto.kuska.data.api.modelos.RecuperarConfirmarRequest
import com.proyecto.kuska.data.api.modelos.RefreshRequest
import com.proyecto.kuska.data.api.modelos.RefreshResponse
import com.proyecto.kuska.data.api.modelos.RegistroRequest
import com.proyecto.kuska.data.api.modelos.SolicitarCodigoRequest
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

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

    @GET("auth/perfil/")
    suspend fun obtenerPerfil(): Response<Perfil>

    /** Solo las categorías activas, sin paginación. */
    @GET("categorias/")
    suspend fun obtenerCategorias(): Response<List<CategoriaPublica>>

    /** Multipart: "datos" (JSON) y foto_0, foto_1... en el orden de los alimentos. */
    @Multipart
    @POST("donaciones/")
    suspend fun crearDonacion(@Part partes: List<MultipartBody.Part>): Response<Donacion>

    /** Paginado. [estado] null = todas. */
    @GET("donaciones/")
    suspend fun listarDonaciones(
        @Query("estado") estado: String?,
        @Query("page") pagina: Int,
    ): Response<PaginaDonaciones>

    @GET("donaciones/{id}/")
    suspend fun obtenerDonacion(@Path("id") id: Int): Response<Donacion>

    /** Solo Pendiente (RN-12). Responde 204 sin cuerpo. */
    @DELETE("donaciones/{id}/")
    suspend fun eliminarDonacion(@Path("id") id: Int): Response<Unit>

    /** Sin paginación: una entrada por alimento con sus solicitudes. */
    @GET("donaciones/{id}/solicitudes-recibidas/")
    suspend fun solicitudesRecibidas(@Path("id") id: Int): Response<List<AlimentoConSolicitudes>>
}

/** Solo para el refresh de tokens. Usa un cliente sin interceptor de auth ni Authenticator. */
interface RefreshApi {

    @POST("auth/refresh/")
    fun refrescar(@Body cuerpo: RefreshRequest): Call<RefreshResponse>
}
