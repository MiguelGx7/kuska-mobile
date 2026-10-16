package com.proyecto.kuska.data.api

import com.proyecto.kuska.data.sesion.SesionManager
import okhttp3.Interceptor
import okhttp3.Response

/** Agrega "Authorization: Bearer <access>" a todas las peticiones salvo las rutas públicas. */
class AuthInterceptor(private val sesion: SesionManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (RutasPublicas.esPublica(request)) return chain.proceed(request)

        val access = sesion.accessBloqueante() ?: return chain.proceed(request)
        return chain.proceed(
            request.newBuilder()
                .header(ENCABEZADO, "Bearer $access")
                .build()
        )
    }

    companion object {
        const val ENCABEZADO = "Authorization"
    }
}
