package com.proyecto.kuska.ui.iniciobeneficiario

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltrosAlimentosTest {

    @Test
    fun busquedaSinEspaciosYVaciaNoSeEnvia() {
        assertEquals("arroz", FiltrosAlimentos(busqueda = "  arroz ").parametroBusqueda)
        assertNull(FiltrosAlimentos(busqueda = "   ").parametroBusqueda)
        assertNull(FiltrosAlimentos().parametroBusqueda)
    }

    @Test
    fun sinFiltrosNoHayNadaActivo() {
        assertFalse(FiltrosAlimentos().activos)
        assertFalse(FiltrosAlimentos(busqueda = "  ").activos)
    }

    @Test
    fun cualquierFiltroCuentaComoActivo() {
        assertTrue(FiltrosAlimentos(busqueda = "pan").activos)
        assertTrue(FiltrosAlimentos(categoria = 3).activos)
        assertTrue(FiltrosAlimentos(modalidad = "Recoger").activos)
    }

    @Test
    fun espaciosNoCambianLaConsulta() {
        assertTrue(FiltrosAlimentos(busqueda = "arroz").mismaConsulta(FiltrosAlimentos(busqueda = "arroz ")))
        assertTrue(FiltrosAlimentos(busqueda = "").mismaConsulta(FiltrosAlimentos(busqueda = "  ")))
        assertFalse(FiltrosAlimentos(busqueda = "arroz").mismaConsulta(FiltrosAlimentos(busqueda = "arroz", categoria = 1)))
        assertFalse(FiltrosAlimentos(modalidad = "Recoger").mismaConsulta(FiltrosAlimentos(modalidad = "Domicilio")))
    }

    @Test
    fun modalidadesDelFiltro() {
        assertEquals(listOf(null, "Recoger", "Domicilio"), FiltrosAlimentos.MODALIDADES)
    }
}
