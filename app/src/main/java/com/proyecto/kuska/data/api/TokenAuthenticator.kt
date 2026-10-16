package com.proyecto.kuska.data.api

import com.proyecto.kuska.data.api.modelos.RefreshRequest
import com.proyecto.kuska.data.sesion.SesionManager
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException

/**
 * OkHttp lo llama solo ante un 401 (los 403 no pasan por aquí).
 * Intenta UNA vez POST auth/refresh/, guarda los dos tokens nuevos y repite la petición.
 * Si el refresh es rechazado, borra la sesión y avisa para volver al login.
 */
class TokenAuthenticator(
    private val sesion: SesionManager,
    private val refreshApi: RefreshApi,
) : Authenticator {

    private val candado = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Un 401 en login/registro/refresh es un error de credenciales, no de token.
        if (RutasPublicas.esPublica(response.request)) return null
        // La petición ya se repitió con un token nuevo y aun así falló.
        if (response.priorResponse != null) {
            expirarSesion()
            return null
        }

        synchronized(candado) {
            val accessEnviado = response.request.header(AuthInterceptor.ENCABEZADO)
                ?.removePrefix("Bearer ")
            val accessActual = sesion.accessBloqueante() ?: return null

            // Otra petición ya refrescó mientras esta esperaba: repetir con el token nuevo.
            if (accessActual != accessEnviado) return conToken(response.request, accessActual)

            val refresh = sesion.refreshBloqueante() ?: run {
                expirarSesion()
                return null
            }

            val respuesta = try {
                refreshApi.refrescar(RefreshRequest(refresh)).execute()
            } catch (_: IOException) {
                // Sin conexión: no se borra la sesión, la petición original falla con su 401.
                return null
            }

            val tokens = respuesta.body()
            if (!respuesta.isSuccessful || tokens == null) {
                expirarSesion()
                return null
            }

            // El refresh rota: se guarda el nuevo (o se conserva el anterior si no vino).
            sesion.actualizarTokensBloqueante(tokens.access, tokens.refresh ?: refresh)
            return conToken(response.request, tokens.access)
        }
    }

    private fun conToken(request: Request, access: String): Request =
        request.newBuilder()
            .header(AuthInterceptor.ENCABEZADO, "Bearer $access")
            .build()

    private fun expirarSesion() {
        sesion.cerrarSesionBloqueante()
        sesion.notificarSesionExpirada()
    }
}
