package com.proyecto.kuska.ui.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanIntroTest {

    @Test
    fun conAnimacionesSaleALos33YSeVaConFundido() {
        val plan = PlanIntro.para(animacionesActivas = true)
        assertTrue(plan.animada)
        assertEquals(3300L, plan.salirEn)
        assertEquals(400L, plan.fundido)
        assertEquals(3700L, plan.total)
    }

    @Test
    fun sinAnimacionesSeQuitaALos26SinFundido() {
        val plan = PlanIntro.para(animacionesActivas = false)
        assertFalse(plan.animada)
        assertEquals(2600L, plan.salirEn)
        assertEquals(0L, plan.fundido)
        assertEquals(2600L, plan.total)
    }

    @Test
    fun escalaEnCeroDesactivaLasAnimaciones() {
        assertFalse(PlanIntro.animacionesActivas(0f))
        assertTrue(PlanIntro.animacionesActivas(0.5f))
        assertTrue(PlanIntro.animacionesActivas(1f))
        assertTrue(PlanIntro.animacionesActivas(10f))
    }

    @Test
    fun soloAlAbrirEnFrio() {
        assertTrue(PlanIntro.debeMostrar(hayEstadoGuardado = false))
        // Al girar o al restaurar el proceso hay estado guardado.
        assertFalse(PlanIntro.debeMostrar(hayEstadoGuardado = true))
    }

    @Test
    fun secuenciaEnOrden() {
        // Entra a los 120 ms y llega al centro después de 1,5 s.
        assertEquals(1620L, TiemposIntro.LLEGADA)
        // El grano salta y el parpadeo empieza 0,35 s después de llegar; los dos terminan antes de salir.
        assertTrue(TiemposIntro.LLEGADA + TiemposIntro.SALTO_GRANO < TiemposIntro.SALIDA)
        assertTrue(TiemposIntro.LLEGADA + TiemposIntro.RETRASO_PARPADEO + TiemposIntro.PARPADEO < TiemposIntro.SALIDA)
        // El subtítulo aparece 0,3 s después del título y termina antes del fundido.
        assertTrue(TiemposIntro.LLEGADA + TiemposIntro.RETRASO_SUBTITULO + TiemposIntro.TEXTO <= TiemposIntro.SALIDA)
    }
}
