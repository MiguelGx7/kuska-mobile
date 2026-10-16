package com.proyecto.kuska.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErroresDonacionTest {

    @Test
    fun erroresPorAlimentoEnLista() {
        val cuerpo = """{"alimentos": [{}, {"fecha_vencimiento": ["La fecha debe ser posterior al 17/10/2026."]}]}"""
        assertEquals(
            mapOf("alimentos.1" to "La fecha debe ser posterior al 17/10/2026."),
            extraerErroresDonacion(cuerpo),
        )
    }

    @Test
    fun erroresPorAlimentoConIndice() {
        val cuerpo = """{"alimentos": {"0": {"categoria": ["La categoría no está activa."]}}}"""
        assertEquals(mapOf("alimentos.0" to "La categoría no está activa."), extraerErroresDonacion(cuerpo))
    }

    @Test
    fun errorDeLaListaDeAlimentos() {
        val cuerpo = """{"alimentos": ["Agrega al menos un alimento."]}"""
        assertEquals(mapOf("alimentos" to "Agrega al menos un alimento."), extraerErroresDonacion(cuerpo))
    }

    @Test
    fun camposFotosYGenerales() {
        val cuerpo = """{"titulo": ["Este campo es requerido."], "foto_2": ["Sube una imagen válida."],
            "non_field_errors": ["Revisa los datos."]}"""
        assertEquals(
            mapOf(
                "titulo" to "Este campo es requerido.",
                "foto_2" to "Sube una imagen válida.",
                CLAVE_ERROR_GENERAL to "Revisa los datos.",
            ),
            extraerErroresDonacion(cuerpo),
        )
    }

    @Test
    fun dentroDeDatos() {
        val cuerpo = """{"datos": {"alimentos": [{"nombre": ["Este campo es requerido."]}]}}"""
        assertEquals(mapOf("alimentos.0" to "Este campo es requerido."), extraerErroresDonacion(cuerpo))
    }

    @Test
    fun otrosFormatosNoTienenCampos() {
        assertTrue(extraerErroresDonacion("""["No se puede crear la donación."]""").isEmpty())
        assertTrue(extraerErroresDonacion("<html>").isEmpty())
        assertTrue(extraerErroresDonacion(null).isEmpty())
    }
}
