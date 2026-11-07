package com.proyecto.kuska.ui.cambiarcontrasena

import com.proyecto.kuska.R
import com.proyecto.kuska.ui.comun.TextoUi
import com.proyecto.kuska.util.Resultado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidacionCambioContrasenaTest {

    @Test
    fun todoVacio() {
        val errores = ValidacionCambioContrasena.validar("", "", "")
        assertEquals(TextoUi.Recurso(R.string.perfil_error_actual_vacia), errores[CampoContrasena.ACTUAL])
        assertEquals(TextoUi.Recurso(R.string.error_contrasena_vacia), errores[CampoContrasena.NUEVA])
        assertEquals(TextoUi.Recurso(R.string.error_confirmar_vacia), errores[CampoContrasena.CONFIRMAR])
    }

    @Test
    fun confirmacionDistinta() {
        val errores = ValidacionCambioContrasena.validar("Vieja.2026", "Nueva.2026", "Nueva.2027")
        assertEquals(mapOf(CampoContrasena.CONFIRMAR to TextoUi.Recurso(R.string.error_contrasenas_distintas)), errores)
    }

    @Test
    fun lasReglasDeLaNuevaLasValidaElServidor() {
        // "123" no cumple las reglas del servidor, pero la app no las repite.
        assertTrue(ValidacionCambioContrasena.validar("Vieja.2026", "123", "123").isEmpty())
    }

    @Test
    fun noSeRecortan() {
        val errores = ValidacionCambioContrasena.validar(" ", "Nueva.2026 ", "Nueva.2026")
        assertNull(errores[CampoContrasena.ACTUAL])
        assertEquals(TextoUi.Recurso(R.string.error_contrasenas_distintas), errores[CampoContrasena.CONFIRMAR])
    }

    @Test
    fun largoMaximoDeLaNueva() {
        val larga = "a".repeat(129)
        assertEquals(
            TextoUi.Recurso(R.string.error_largo_maximo, listOf(128)),
            ValidacionCambioContrasena.validar("x", larga, larga)[CampoContrasena.NUEVA],
        )
    }

    @Test
    fun actualIncorrectaVaASuCampo() {
        // El repositorio deja el 401 de este endpoint en erroresCampo["contrasena_actual"]; un 400 llega igual.
        listOf(400, 401).forEach { codigo ->
            val error = Resultado.Error("x", codigo, mapOf("contrasena_actual" to "La contraseña actual no es correcta."))
            val repartidos = ValidacionCambioContrasena.repartir(error)
            assertEquals(TextoUi.Texto("La contraseña actual no es correcta."), repartidos.porCampo[CampoContrasena.ACTUAL])
            assertNull(repartidos.aviso)
        }
    }

    @Test
    fun reglaDeLaNuevaVaASuCampo() {
        val error = Resultado.Error("x", 400, mapOf("contrasena_nueva" to "Esta contraseña es demasiado común."))
        assertEquals(
            TextoUi.Texto("Esta contraseña es demasiado común."),
            ValidacionCambioContrasena.repartir(error).porCampo[CampoContrasena.NUEVA],
        )
    }

    @Test
    fun sinConexionVaAlAviso() {
        val repartidos = ValidacionCambioContrasena.repartir(Resultado.Error("No hay conexión."))
        assertTrue(repartidos.porCampo.isEmpty())
        assertEquals("No hay conexión.", repartidos.aviso)
    }
}
