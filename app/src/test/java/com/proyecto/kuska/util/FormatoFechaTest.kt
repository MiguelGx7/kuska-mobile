package com.proyecto.kuska.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatoFechaTest {

    @Test
    fun fechaSoloSeReordena() {
        assertEquals("31/01/2027", FormatoFecha.fecha("2027-01-31"))
        assertNull(FormatoFecha.fecha("31/01/2027"))
        assertNull(FormatoFecha.fecha(null))
    }

    @Test
    fun fechaHoraConZonaDeBogota() {
        assertEquals("16/10/2026", FormatoFecha.fechaDeFechaHora("2026-10-16T18:30:00-05:00"))
        assertEquals("16/10/2026", FormatoFecha.fechaDeFechaHora("2026-10-16T18:30:00.123456-05:00"))
    }

    @Test
    fun despuesDeLasSieteDeLaNocheEnUtcYaEsOtroDia() {
        // 00:30 UTC del 17 = 19:30 del 16 en Bogotá.
        assertEquals("16/10/2026", FormatoFecha.fechaDeFechaHora("2026-10-17T00:30:00Z"))
        assertEquals("16/10/2026", FormatoFecha.fechaDeFechaHora("2026-10-17T00:30:00.5+00:00"))
        // 05:00 UTC del 17 = medianoche del 17 en Bogotá.
        assertEquals("17/10/2026", FormatoFecha.fechaDeFechaHora("2026-10-17T05:00:00Z"))
    }

    @Test
    fun otrasZonasYFormatos() {
        assertEquals("16/10/2026", FormatoFecha.fechaDeFechaHora("2026-10-17T06:00:00+0530"))
        assertEquals("16/10/2026", FormatoFecha.fechaDeFechaHora("2026-10-16T23:59"))
    }

    @Test
    fun milisegundosExactos() {
        assertEquals(1_792_108_800_000L, FormatoFecha.milisDeFechaHora("2026-10-15T19:00:00-05:00"))
        assertEquals(1_792_108_800_000L, FormatoFecha.milisDeFechaHora("2026-10-16T00:00:00.999999Z"))
    }

    @Test
    fun textoInvalido() {
        assertNull(FormatoFecha.fechaDeFechaHora(null))
        assertNull(FormatoFecha.fechaDeFechaHora(""))
        assertNull(FormatoFecha.fechaDeFechaHora("2026-10-16"))
        assertNull(FormatoFecha.fechaDeFechaHora("2026-13-40T10:00:00Z"))
    }
}
