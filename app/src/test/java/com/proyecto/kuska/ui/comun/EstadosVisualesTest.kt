package com.proyecto.kuska.ui.comun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EstadosVisualesTest {

    @Test
    fun familiaDeCadaEstadoDeDonacion() {
        assertEquals(FamiliaColor.NARANJA, EstadosVisuales.familiaDonacion("Pendiente"))
        assertEquals(FamiliaColor.VERDE, EstadosVisuales.familiaDonacion("Validada"))
        assertEquals(FamiliaColor.ROJO, EstadosVisuales.familiaDonacion("Rechazada"))
        assertEquals(FamiliaColor.GRIS, EstadosVisuales.familiaDonacion("Agotada"))
        assertEquals(FamiliaColor.GRIS, EstadosVisuales.familiaDonacion("Finalizada"))
    }

    @Test
    fun familiaDeCadaEstadoDeSolicitud() {
        assertEquals(FamiliaColor.NARANJA, EstadosVisuales.familiaSolicitud("Pendiente"))
        assertEquals(FamiliaColor.NARANJA, EstadosVisuales.familiaSolicitud("Aprobada"))
        assertEquals(FamiliaColor.VERDE, EstadosVisuales.familiaSolicitud("Entregada"))
        assertEquals(FamiliaColor.ROJO, EstadosVisuales.familiaSolicitud("Rechazada"))
        assertEquals(FamiliaColor.ROJO, EstadosVisuales.familiaSolicitud("No recogida"))
        assertEquals(FamiliaColor.GRIS, EstadosVisuales.familiaSolicitud("Cancelada"))
    }

    @Test
    fun familiaDeCadaEstadoDeNovedad() {
        assertEquals(FamiliaColor.NARANJA, EstadosVisuales.familiaNovedad("Pendiente"))
        assertEquals(FamiliaColor.VERDE, EstadosVisuales.familiaNovedad("Aceptada"))
        assertEquals(FamiliaColor.ROJO, EstadosVisuales.familiaNovedad("Rechazada"))
        assertEquals(FamiliaColor.GRIS, EstadosVisuales.familiaNovedad("Archivada"))
        assertEquals(com.proyecto.kuska.R.string.estado_aceptada, EstadosVisuales.etiqueta("Aceptada"))
    }

    @Test
    fun estadoDesconocidoEnGrisYSinTexto() {
        assertEquals(FamiliaColor.GRIS, EstadosVisuales.familiaDonacion("Archivada"))
        assertEquals(FamiliaColor.GRIS, EstadosVisuales.familiaSolicitud("pendiente"))
        assertNull(EstadosVisuales.etiqueta("Archivada"))
    }
}
