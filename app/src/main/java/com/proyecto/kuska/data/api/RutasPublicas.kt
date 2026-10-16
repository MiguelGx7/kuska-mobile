package com.proyecto.kuska.data.api

import com.proyecto.kuska.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/** Rutas que no llevan token ni pasan por el refresh. */
object RutasPublicas {

    private val prefijos = listOf("auth/login/", "auth/registro/", "auth/refresh/")

    private val rutaBase = BuildConfig.BASE_URL.toHttpUrl().encodedPath

    fun esPublica(request: Request): Boolean {
        val ruta = request.url.encodedPath.removePrefix(rutaBase)
        return prefijos.any { ruta.startsWith(it) }
    }
}
