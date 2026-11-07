package com.proyecto.kuska.data.api

import com.proyecto.kuska.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Respuestas401Test {

    private fun peticion(ruta: String) = Request.Builder()
        .url(BuildConfig.BASE_URL.toHttpUrl().resolve(ruta)!!)
        .build()

    @Test
    fun soloLaRutaDeCambiarContrasena() {
        assertTrue(Respuestas401.esCambioContrasena(peticion("auth/perfil/cambiar-contrasena/")))
        assertFalse(Respuestas401.esCambioContrasena(peticion("auth/perfil/")))
        assertFalse(Respuestas401.esCambioContrasena(peticion("donaciones/")))
    }

    @Test
    fun tokenVencidoDeSimplejwt() {
        assertTrue(
            Respuestas401.esTokenInvalido(
                """{"detail":"Given token not valid for any token type","code":"token_not_valid","messages":[]}"""
            )
        )
    }

    @Test
    fun contrasenaIncorrectaNoEsUnTokenVencido() {
        assertFalse(Respuestas401.esTokenInvalido("""{"detail":"La contraseña actual no es correcta."}"""))
        assertFalse(Respuestas401.esTokenInvalido("""{"contrasena_actual":["Incorrecta."]}"""))
        assertFalse(Respuestas401.esTokenInvalido(""))
        assertFalse(Respuestas401.esTokenInvalido(null))
        assertFalse(Respuestas401.esTokenInvalido("no es json"))
    }
}
