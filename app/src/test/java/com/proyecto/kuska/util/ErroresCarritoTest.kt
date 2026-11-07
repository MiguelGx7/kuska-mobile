package com.proyecto.kuska.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErroresCarritoTest {

    @Test
    fun leeElFormatoDelBackend() {
        val cuerpo = """
            {"carrito": ["Superas el máximo de solicitudes activas.", "Otro aviso."],
             "items": {"31": ["Ya no hay suficiente cantidad."], "32": ["Vencido.", "Con novedad."]},
             "donaciones": {"4": ["Indica la dirección de entrega."]}}
        """.trimIndent()
        assertEquals(
            mapOf(
                "carrito.0" to "Superas el máximo de solicitudes activas.",
                "carrito.1" to "Otro aviso.",
                "items.31" to "Ya no hay suficiente cantidad.",
                "items.32" to "Vencido.\nCon novedad.",
                "donaciones.4" to "Indica la dirección de entrega.",
            ),
            extraerErroresCarrito(cuerpo),
        )
    }

    @Test
    fun unaListaSueltaVaComoErrorDelCarrito() {
        assertEquals(mapOf("carrito.0" to "Regla"), extraerErroresCarrito("""["Regla"]"""))
    }

    @Test
    fun lasClavesDesconocidasSeConservanConSuNombre() {
        assertEquals(
            mapOf("detail" to "No permitido.", "items" to "Lista mal formada."),
            extraerErroresCarrito("""{"detail": "No permitido.", "items": ["Lista mal formada."]}"""),
        )
    }

    @Test
    fun cuerpoVacioONoJsonDaMapaVacio() {
        assertTrue(extraerErroresCarrito(null).isEmpty())
        assertTrue(extraerErroresCarrito("").isEmpty())
        assertTrue(extraerErroresCarrito("<html>").isEmpty())
    }
}
