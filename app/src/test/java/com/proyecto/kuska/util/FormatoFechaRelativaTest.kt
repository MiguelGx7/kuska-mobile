package com.proyecto.kuska.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatoFechaRelativaTest {

    private val fecha = "2026-11-07T10:00:00-05:00"
    private val base = FormatoFecha.milisDeFechaHora(fecha)!!

    private fun despues(milis: Long) = FormatoFecha.fechaRelativa(fecha, base + milis)

    private val segundo = 1_000L
    private val minuto = 60 * segundo
    private val hora = 60 * minuto

    @Test
    fun menosDeUnMinutoEsAhora() {
        assertEquals(FechaRelativa.Ahora, despues(0))
        assertEquals(FechaRelativa.Ahora, despues(59 * segundo))
    }

    @Test
    fun menosDeUnaHoraEnMinutos() {
        assertEquals(FechaRelativa.Minutos(1), despues(minuto))
        assertEquals(FechaRelativa.Minutos(59), despues(59 * minuto + 59 * segundo))
    }

    @Test
    fun menosDeUnDiaEnHoras() {
        assertEquals(FechaRelativa.Horas(1), despues(hora))
        assertEquals(FechaRelativa.Horas(23), despues(23 * hora + 59 * minuto))
    }

    @Test
    fun desdeUnDiaLaFechaCompletaEnHoraDeBogota() {
        assertEquals(FechaRelativa.Fecha("07/11/2026 10:00"), despues(24 * hora))
        // En UTC cambia de día; se muestra en Bogotá.
        assertEquals(
            FechaRelativa.Fecha("06/11/2026 20:30"),
            FormatoFecha.fechaRelativa("2026-11-07T01:30:00Z", base + 3 * 24 * hora),
        )
    }

    @Test
    fun fechaFuturaCercanaEsAhoraYLejanaLaCompleta() {
        assertEquals(FechaRelativa.Ahora, despues(-30 * segundo))
        assertEquals(FechaRelativa.Fecha("07/11/2026 10:00"), despues(-2 * minuto))
    }

    @Test
    fun textoIlegibleEsNull() {
        assertNull(FormatoFecha.fechaRelativa(null, base))
        assertNull(FormatoFecha.fechaRelativa("ayer", base))
    }
}
