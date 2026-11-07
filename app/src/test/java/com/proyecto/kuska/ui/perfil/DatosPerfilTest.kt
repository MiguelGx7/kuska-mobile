package com.proyecto.kuska.ui.perfil

import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Perfil
import com.proyecto.kuska.ui.comun.TextoUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatosPerfilTest {

    private val persona = Perfil(
        id = 1, correo = "laura@correo.com", rol = "Donante", nombres = "Laura", apellidos = "Gómez",
        tipoDocumento = "CC", numeroDocumento = "1020304050", telefono = "3001234567", direccion = null,
        tipoDonante = "Persona", nombreNegocio = null, nit = null, fechaRegistro = "2026-10-17T00:30:00Z",
    )

    private fun List<DatoPerfil>.valor(etiqueta: Int) = first { it.etiqueta == etiqueta }.valor

    @Test
    fun personaSinNegocioNiNit() {
        val datos = DatosPerfil.todos(persona)
        assertFalse(datos.any { it.etiqueta == R.string.perfil_nombre_negocio || it.etiqueta == R.string.registro_nit })
        assertEquals(
            listOf(
                R.string.registro_nombres, R.string.registro_apellidos, R.string.login_etiqueta_correo, R.string.perfil_rol,
                R.string.registro_tipo_documento, R.string.registro_numero_documento, R.string.registro_telefono,
                R.string.perfil_direccion, R.string.perfil_fecha_registro,
            ),
            datos.map { it.etiqueta },
        )
    }

    @Test
    fun donanteNegocioMuestraNegocioYNit() {
        val negocio = persona.copy(tipoDonante = "Negocio", nombreNegocio = "Panadería Sol", nit = "900123456")
        val datos = DatosPerfil.todos(negocio)
        assertEquals(TextoUi.Texto("Panadería Sol"), datos.valor(R.string.perfil_nombre_negocio))
        assertEquals(TextoUi.Texto("900123456"), datos.valor(R.string.registro_nit))
        assertTrue(DatosPerfil.soloLectura(negocio).any { it.etiqueta == R.string.registro_nit })
    }

    @Test
    fun beneficiarioNuncaMuestraNegocio() {
        val beneficiario = persona.copy(rol = "Beneficiario", tipoDonante = "Negocio", nombreNegocio = "X", nit = "1")
        assertFalse(DatosPerfil.esNegocio(beneficiario))
    }

    @Test
    fun valoresFormateados() {
        val datos = DatosPerfil.todos(persona)
        assertEquals(TextoUi.Recurso(R.string.tipo_documento_cc), datos.valor(R.string.registro_tipo_documento))
        assertEquals(TextoUi.Texto("1020304050"), datos.valor(R.string.registro_numero_documento))
        assertEquals(TextoUi.Texto("16/10/2026"), datos.valor(R.string.perfil_fecha_registro))
        assertEquals(TextoUi.Recurso(R.string.perfil_sin_dato), datos.valor(R.string.perfil_direccion))
        assertEquals(TextoUi.Texto("Donante"), datos.valor(R.string.perfil_rol))
    }

    @Test
    fun tipoDeDocumentoDesconocidoTalCual() {
        assertEquals(TextoUi.Texto("NUIP"), DatosPerfil.tipoDocumento("NUIP"))
        assertEquals(TextoUi.Recurso(R.string.tipo_documento_ppt), DatosPerfil.tipoDocumento("PPT"))
    }

    @Test
    fun soloLecturaNoIncluyeLoEditable() {
        val editables = setOf(R.string.registro_nombres, R.string.registro_apellidos, R.string.registro_telefono, R.string.perfil_direccion)
        assertTrue(DatosPerfil.soloLectura(persona).none { it.etiqueta in editables })
    }
}
