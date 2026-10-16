package com.proyecto.kuska.ui.recuperar

import com.proyecto.kuska.R
import com.proyecto.kuska.ui.comun.TextoUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidacionRecuperarTest {

    private fun recurso(id: Int) = TextoUi.Recurso(id)

    private fun cambio(codigo: String = "482913", contrasena: String = "Nueva.Clave2026", confirmacion: String = contrasena) =
        ValidacionRecuperar.validarPasoCambio(codigo, contrasena, confirmacion)

    @Test
    fun correoValido() {
        assertTrue(ValidacionRecuperar.validarPasoCorreo("laura@correo.com").isEmpty())
        assertTrue(ValidacionRecuperar.validarPasoCorreo("  laura@correo.com ").isEmpty())
    }

    @Test
    fun correoVacioEInvalido() {
        assertEquals(
            mapOf(CampoRecuperar.CORREO to recurso(R.string.error_correo_vacio)),
            ValidacionRecuperar.validarPasoCorreo(" "),
        )
        assertEquals(
            mapOf(CampoRecuperar.CORREO to recurso(R.string.error_correo_invalido)),
            ValidacionRecuperar.validarPasoCorreo("laura@correo"),
        )
    }

    @Test
    fun cambioValido() {
        assertTrue(cambio().isEmpty())
        assertTrue(cambio(codigo = " 482913 ").isEmpty())
    }

    @Test
    fun codigoDebeTenerSeisDigitos() {
        listOf("", "48291", "4829131", "48a913", "482 913").forEach {
            assertEquals(it, recurso(R.string.error_codigo_formato), cambio(codigo = it)[CampoRecuperar.CODIGO])
        }
    }

    @Test
    fun contrasenaVaciaYLarga() {
        assertEquals(
            recurso(R.string.error_contrasena_vacia),
            cambio(contrasena = "", confirmacion = "")[CampoRecuperar.CONTRASENA_NUEVA],
        )
        val larga = "c".repeat(129)
        assertEquals(
            TextoUi.Recurso(R.string.error_largo_maximo, listOf(128)),
            cambio(contrasena = larga)[CampoRecuperar.CONTRASENA_NUEVA],
        )
        assertTrue(cambio(contrasena = "c".repeat(128)).isEmpty())
    }

    @Test
    fun contrasenaSinReglasLocales() {
        // Las reglas (largo mínimo, etc.) las aplica el servidor.
        assertTrue(cambio(contrasena = "1").isEmpty())
    }

    @Test
    fun confirmacionVaciaODistinta() {
        assertEquals(
            recurso(R.string.error_confirmar_vacia),
            cambio(confirmacion = "")[CampoRecuperar.CONFIRMAR_CONTRASENA],
        )
        assertEquals(
            recurso(R.string.error_contrasenas_distintas),
            cambio(confirmacion = "Otra.Clave")[CampoRecuperar.CONFIRMAR_CONTRASENA],
        )
    }

    @Test
    fun variosErroresALaVez() {
        assertEquals(
            setOf(CampoRecuperar.CODIGO, CampoRecuperar.CONTRASENA_NUEVA, CampoRecuperar.CONFIRMAR_CONTRASENA),
            cambio(codigo = "1", contrasena = "", confirmacion = "").keys,
        )
    }
}
