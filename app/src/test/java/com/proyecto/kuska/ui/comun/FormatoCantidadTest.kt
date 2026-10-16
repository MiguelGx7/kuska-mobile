package com.proyecto.kuska.ui.comun

import com.proyecto.kuska.R
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatoCantidadTest {

    @Test
    fun numeroSinCerosSobrantesYConComa() {
        assertEquals("5,5", FormatoCantidad.numero("5.50"))
        assertEquals("12", FormatoCantidad.numero("12.00"))
        assertEquals("0,01", FormatoCantidad.numero("0.01"))
        assertEquals("100", FormatoCantidad.numero("100.00"))
        assertEquals("2,5", FormatoCantidad.numero("2,50"))
        assertEquals("0", FormatoCantidad.numero("0.00"))
    }

    @Test
    fun textoNoNumericoSeDejaIgual() {
        assertEquals("abc", FormatoCantidad.numero("abc"))
    }

    @Test
    fun unidadesConSimbolo() {
        assertEquals(CantidadConUnidad.ConSimbolo("2,5", "kg"), FormatoCantidad.conUnidad("2.50", "kg"))
        assertEquals(CantidadConUnidad.ConSimbolo("500", "g"), FormatoCantidad.conUnidad("500.00", "g"))
        assertEquals(CantidadConUnidad.ConSimbolo("1", "L"), FormatoCantidad.conUnidad("1.00", "L"))
        assertEquals(CantidadConUnidad.ConSimbolo("250", "ml"), FormatoCantidad.conUnidad("250.00", "ml"))
    }

    @Test
    fun unidadYPaqueteEnPlural() {
        assertEquals(
            CantidadConUnidad.EnPlural("12", 12, R.plurals.cantidad_unidades),
            FormatoCantidad.conUnidad("12.00", "unidad"),
        )
        assertEquals(
            CantidadConUnidad.EnPlural("1", 1, R.plurals.cantidad_paquetes),
            FormatoCantidad.conUnidad("1.00", "paquete"),
        )
    }

    @Test
    fun pluralConValorNoEnteroVaComoVarios() {
        // No debería llegar (RN-10a), pero no se muestra en singular.
        assertEquals(
            CantidadConUnidad.EnPlural("1,5", 0, R.plurals.cantidad_unidades),
            FormatoCantidad.conUnidad("1.50", "unidad"),
        )
    }
}
