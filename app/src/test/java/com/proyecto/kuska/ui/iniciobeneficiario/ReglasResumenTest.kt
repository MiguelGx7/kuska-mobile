package com.proyecto.kuska.ui.iniciobeneficiario

import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.DashboardBeneficiario
import com.proyecto.kuska.data.api.modelos.MiSolicitud
import com.proyecto.kuska.data.api.modelos.ProximaRecogida
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun resumen(cupos: Int, maximo: Int = 3) = DashboardBeneficiario(
    solicitudesPendientes = 1, solicitudesAprobadas = 1, maxSolicitudesActivas = maximo, cuposDisponibles = cupos,
    proximaRecogida = null, notificacionesNoLeidas = 0,
)

private fun recogida(modalidad: String) = ProximaRecogida(
    solicitud = 7, codigoPedido = "KUM-0012", fechaLimiteRecogida = "2026-11-08T18:30:00-05:00",
    modalidadEntrega = modalidad, donacion = 3, donacionTitulo = "Mercado",
)

class ReglasResumenTest {

    @Test
    fun cuposNuncaNegativos() {
        assertEquals(2, ReglasResumen.cupos(resumen(cupos = 2)))
        assertEquals(0, ReglasResumen.cupos(resumen(cupos = 0)))
        assertEquals(0, ReglasResumen.cupos(resumen(cupos = -1)))
    }

    @Test
    fun avisoDeLimiteSoloSinCupos() {
        assertTrue(ReglasResumen.enLimite(resumen(cupos = 0)))
        assertTrue(ReglasResumen.enLimite(resumen(cupos = -2)))
        assertFalse(ReglasResumen.enLimite(resumen(cupos = 1)))
    }

    @Test
    fun recogerDiceRecogeAntes() {
        val recoger = recogida(MiSolicitud.MODALIDAD_RECOGER)
        assertEquals(R.string.solicitud_recoger_antes, ReglasResumen.etiquetaFecha(recoger))
        assertEquals(R.string.resumen_proxima_recogida, ReglasResumen.titulo(recoger))
    }

    @Test
    fun domicilioDiceFechaLimite() {
        val domicilio = recogida(MiSolicitud.MODALIDAD_DOMICILIO)
        assertEquals(R.string.resumen_fecha_limite, ReglasResumen.etiquetaFecha(domicilio))
        assertEquals(R.string.resumen_proxima_entrega, ReglasResumen.titulo(domicilio))
    }
}
