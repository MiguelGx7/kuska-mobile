package com.proyecto.kuska.data.api

import com.proyecto.kuska.BuildConfig
import com.proyecto.kuska.data.sesion.SesionManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Construye los clientes Retrofit/OkHttp de la app. */
class ClienteApi(sesion: SesionManager) {

    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        redactHeader(AuthInterceptor.ENCABEZADO)
    }

    private val clienteBase: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    private val refreshApi: RefreshApi = retrofit(clienteBase).create(RefreshApi::class.java)

    private val clienteAutenticado: OkHttpClient = clienteBase.newBuilder()
        .addInterceptor(AuthInterceptor(sesion))
        .authenticator(TokenAuthenticator(sesion, refreshApi))
        .build()

    val api: KumoraApi = retrofit(clienteAutenticado).create(KumoraApi::class.java)

    private fun retrofit(cliente: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(cliente)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}
