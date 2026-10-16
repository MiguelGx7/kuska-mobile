package com.proyecto.kuska.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ErroresApiTest {

    @Test
    fun detail() {
        assertEquals("Tu cuenta está bloqueada.", extraerMensajeError("""{"detail": "Tu cuenta está bloqueada."}"""))
    }

    @Test
    fun errorDeCampo() {
        assertEquals("Este correo ya está registrado.", extraerMensajeError("""{"correo": ["Este correo ya está registrado."]}"""))
    }

    @Test
    fun listaDeMensajes() {
        assertEquals("No hay stock suficiente.", extraerMensajeError("""["No hay stock suficiente."]"""))
    }

    @Test
    fun detailTienePrioridad() {
        assertEquals("Primero", extraerMensajeError("""{"campo": ["Otro"], "detail": "Primero"}"""))
    }

    @Test
    fun cuerpoVacioOInvalido() {
        assertNull(extraerMensajeError(null))
        assertNull(extraerMensajeError(""))
        assertNull(extraerMensajeError("<html>Bad Gateway</html>"))
        assertNull(extraerMensajeError("{}"))
    }
}
