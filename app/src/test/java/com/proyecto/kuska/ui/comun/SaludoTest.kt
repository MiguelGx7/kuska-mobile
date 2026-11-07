package com.proyecto.kuska.ui.comun

import com.proyecto.kuska.data.api.modelos.Usuario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SaludoTest {

    private fun usuario(nombres: String?, negocio: String? = null) = Usuario(
        id = 1, correo = "a@b.co", rol = Usuario.ROL_DONANTE, nombres = nombres, apellidos = "Gómez",
        estado = null, tipoDonante = null, nombreNegocio = negocio,
    )

    @Test
    fun primerNombre() {
        assertEquals("Laura", Saludo.nombre(usuario("  Laura   María ")))
    }

    @Test
    fun sinNombreUsaElNegocio() {
        assertEquals("Panadería La 14", Saludo.nombre(usuario("  ", negocio = " Panadería La 14 ")))
    }

    @Test
    fun sinNadaEsSoloHola() {
        assertNull(Saludo.nombre(usuario(null)))
        assertNull(Saludo.nombre(null))
    }
}
