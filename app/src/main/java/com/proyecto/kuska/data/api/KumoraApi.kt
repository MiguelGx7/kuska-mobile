package com.proyecto.kuska.data.api

import com.proyecto.kuska.data.api.modelos.Alimento
import com.proyecto.kuska.data.api.modelos.AlimentoConSolicitudes
import com.proyecto.kuska.data.api.modelos.Carrito
import com.proyecto.kuska.data.api.modelos.CarritoAgregarRequest
import com.proyecto.kuska.data.api.modelos.CarritoCantidadRequest
import com.proyecto.kuska.data.api.modelos.CarritoItem
import com.proyecto.kuska.data.api.modelos.EnviarCarritoRequest
import com.proyecto.kuska.data.api.modelos.PedidoEnviado
import com.proyecto.kuska.data.api.modelos.PaginaAlimentosDisponibles
import com.proyecto.kuska.data.api.modelos.CambiarContrasenaRequest
import com.proyecto.kuska.data.api.modelos.CategoriaPublica
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.data.api.modelos.Entrega
import com.proyecto.kuska.data.api.modelos.EntregarRequest
import com.proyecto.kuska.data.api.modelos.LoginRequest
import com.proyecto.kuska.data.api.modelos.LoginResponse
import com.proyecto.kuska.data.api.modelos.MensajeResponse
import com.proyecto.kuska.data.api.modelos.ConfirmarEntregaRequest
import com.proyecto.kuska.data.api.modelos.MiSolicitud
import com.proyecto.kuska.data.api.modelos.Novedad
import com.proyecto.kuska.data.api.modelos.NovedadCrearRequest
import com.proyecto.kuska.data.api.modelos.PaginaNovedades
import com.proyecto.kuska.data.api.modelos.PaginaDonaciones
import com.proyecto.kuska.data.api.modelos.PaginaMisSolicitudes
import com.proyecto.kuska.data.api.modelos.Perfil
import com.proyecto.kuska.data.api.modelos.RecuperarConfirmarRequest
import com.proyecto.kuska.data.api.modelos.RefreshRequest
import com.proyecto.kuska.data.api.modelos.RefreshResponse
import com.proyecto.kuska.data.api.modelos.RegistroRequest
import com.proyecto.kuska.data.api.modelos.SolicitarCodigoRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
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

    /** RN-04a. [cuerpo]: JSON con solo los campos que cambiaron (direccion null para vaciarla). */
    @PATCH("auth/perfil/")
    suspend fun editarPerfil(@Body cuerpo: RequestBody): Response<Perfil>

    /**
     * Invalida todos los refresh tokens del usuario, también el de este celular. Una contraseña actual incorrecta
     * puede llegar como 400 o 401: ver [Respuestas401].
     */
    @POST("auth/perfil/cambiar-contrasena/")
    suspend fun cambiarContrasena(@Body cuerpo: CambiarContrasenaRequest): Response<MensajeResponse>

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

    /** Solo Pendiente (RN-12). [cuerpo]: JSON con solo los campos que cambiaron (null para vaciar un opcional). */
    @PATCH("donaciones/{id}/")
    suspend fun editarDonacion(@Path("id") id: Int, @Body cuerpo: RequestBody): Response<Donacion>

    /** Solo Pendiente. Multipart: campos del alimento (AlimentoNuevoRequest) y "foto". */
    @Multipart
    @POST("donaciones/{id}/alimentos/")
    suspend fun agregarAlimento(@Path("id") id: Int, @Part partes: List<MultipartBody.Part>): Response<Alimento>

    /** Solo Pendiente y nunca el último alimento. Responde 204 sin cuerpo. */
    @DELETE("donaciones/{id}/alimentos/{alimento_id}/")
    suspend fun quitarAlimento(@Path("id") id: Int, @Path("alimento_id") alimentoId: Int): Response<Unit>

    /** Sin paginación: una entrada por alimento con sus solicitudes. */
    @GET("donaciones/{id}/solicitudes-recibidas/")
    suspend fun solicitudesRecibidas(@Path("id") id: Int): Response<List<AlimentoConSolicitudes>>

    /** Solo el donante dueño y solo solicitudes Aprobada (RN-26). La entrega es total. Responde 201. */
    @POST("solicitudes/{id}/entregar/")
    suspend fun registrarEntrega(@Path("id") solicitudId: Int, @Body cuerpo: EntregarRequest): Response<Entrega>

    /** Mis novedades de una donación. Paginado. */
    @GET("novedades/")
    suspend fun listarNovedades(
        @Query("donacion") donacion: Int,
        @Query("page") pagina: Int,
    ): Response<PaginaNovedades>

    /** Solo sobre una donación Validada o Agotada propia (RN-30). Responde 201. */
    @POST("novedades/")
    suspend fun reportarNovedad(@Body cuerpo: NovedadCrearRequest): Response<Novedad>

    /** Beneficiario (RN-14). Paginado y ordenado por vencimiento. Los filtros null no se envían. */
    @GET("alimentos-disponibles/")
    suspend fun listarAlimentosDisponibles(
        @Query("search") busqueda: String?,
        @Query("categoria") categoria: Int?,
        @Query("modalidad") modalidad: String?,
        @Query("page") pagina: Int,
    ): Response<PaginaAlimentosDisponibles>

    /** RN-20: 201 si es nuevo, 200 si ya estaba y se actualizó la cantidad. El carrito no reserva. */
    @POST("carrito/")
    suspend fun agregarAlCarrito(@Body cuerpo: CarritoAgregarRequest): Response<CarritoItem>

    /** RN-21: agrupado por donación, con los ítems inválidos marcados y los avisos de límites. */
    @GET("carrito/")
    suspend fun obtenerCarrito(): Response<Carrito>

    /** Revalida RN-20 (tope por solicitud y disponible). No reserva. */
    @PATCH("carrito/{id}/")
    suspend fun cambiarCantidadCarrito(@Path("id") id: Int, @Body cuerpo: CarritoCantidadRequest): Response<CarritoItem>

    /** Solo ítems propios; uno ajeno responde 404. Responde 204 sin cuerpo. */
    @DELETE("carrito/{id}/")
    suspend fun quitarDelCarrito(@Path("id") id: Int): Response<Unit>

    /** RN-22: una solicitud por donación con el mismo codigo_pedido. Si algo falla, 400 y no crea nada. */
    @POST("carrito/enviar/")
    suspend fun enviarCarrito(@Body cuerpo: EnviarCarritoRequest): Response<PedidoEnviado>

    /** Mis solicitudes. Paginado; [estado] null = todas. Antes de listar, la API aplica los vencimientos (RN-27). */
    @GET("mis-solicitudes/")
    suspend fun listarMisSolicitudes(
        @Query("estado") estado: String?,
        @Query("page") pagina: Int,
    ): Response<PaginaMisSolicitudes>

    /** Datos de contacto del donante solo desde Aprobada (RN-29). Una ajena responde 404. */
    @GET("mis-solicitudes/{id}/")
    suspend fun obtenerMiSolicitud(@Path("id") id: Int): Response<MiSolicitud>

    /** RN-23: solo si está Pendiente. Devuelve las cantidades, sin sanción. Responde 200 con la solicitud cancelada. */
    @POST("mis-solicitudes/{id}/cancelar/")
    suspend fun cancelarMiSolicitud(@Path("id") id: Int): Response<MiSolicitud>

    /** RN-26b: solo Entregada, una vez y antes de entrega.confirmar_hasta. Responde 200 con la solicitud actualizada. */
    @POST("mis-solicitudes/{id}/confirmar-entrega/")
    suspend fun confirmarEntrega(@Path("id") id: Int, @Body cuerpo: ConfirmarEntregaRequest): Response<MiSolicitud>
}

/** Solo para el refresh de tokens. Usa un cliente sin interceptor de auth ni Authenticator. */
interface RefreshApi {

    @POST("auth/refresh/")
    fun refrescar(@Body cuerpo: RefreshRequest): Call<RefreshResponse>
}
