package com.proyecto.kuska.ui.editardonacion

import com.google.gson.JsonParser
import com.proyecto.kuska.data.api.modelos.Donacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CambiosDonacionTest {

    private val original = Donacion(
        id = 7,
        titulo = "Mercado de la semana",
        descripcion = "Productos en buen estado",
        direccionRecogida = "Calle 10 # 5-20, Bogotá",
        modalidadEntrega = Donacion.MODALIDAD_AMBAS,
        estado = Donacion.ESTADO_PENDIENTE,
        motivoRechazo = null,
        fechaRevision = null,
        fechaRegistro = "2026-11-06T10:00:00-05:00",
        fechaActualizacion = null,
        alimentos = emptyList(),
    )

    private fun cambios(
        titulo: String = original.titulo,
        descripcion: String = original.descripcion.orEmpty(),
        direccion: String = original.direccionRecogida.orEmpty(),
        modalidad: String? = original.modalidadEntrega,
        base: Donacion = original,
    ) = CambiosDonacion.construir(base, titulo, descripcion, direccion, modalidad)

    @Test
    fun sinCambiosQuedaVacio() {
        assertEquals(0, cambios().size())
    }

    @Test
    fun espaciosEnLosExtremosNoSonCambio() {
        assertEquals(0, cambios(titulo = "  Mercado de la semana ", direccion = "Calle 10 # 5-20, Bogotá  ").size())
    }

    @Test
    fun soloElCampoQueCambio() {
        val cuerpo = cambios(titulo = " Mercado del sábado ")
        assertEquals(setOf("titulo"), cuerpo.keySet())
        assertEquals("Mercado del sábado", cuerpo["titulo"].asString)
    }

    @Test
    fun variosCambios() {
        val cuerpo = cambios(direccion = "Carrera 7 # 12-30", modalidad = Donacion.MODALIDAD_RECOGER)
        assertEquals(setOf("direccion_recogida", "modalidad_entrega"), cuerpo.keySet())
        assertEquals("Carrera 7 # 12-30", cuerpo["direccion_recogida"].asString)
        assertEquals("Recoger", cuerpo["modalidad_entrega"].asString)
    }

    @Test
    fun vaciarUnOpcionalLoEnviaComoNull() {
        val cuerpo = cambios(descripcion = "   ")
        assertEquals(setOf("descripcion"), cuerpo.keySet())
        assertTrue(cuerpo["descripcion"].isJsonNull)
    }

    @Test
    fun opcionalNullYVacioSonLoMismo() {
        val sinOpcionales = original.copy(descripcion = null, direccionRecogida = null)
        assertEquals(0, cambios(descripcion = "", direccion = " ", base = sinOpcionales).size())
        val cuerpo = cambios(descripcion = "Nueva", direccion = "", base = sinOpcionales)
        assertEquals(setOf("descripcion"), cuerpo.keySet())
        assertEquals("Nueva", cuerpo["descripcion"].asString)
    }

    @Test
    fun modalidadSinElegirNoSeEnvia() {
        assertEquals(0, cambios(modalidad = null).size())
    }

    @Test
    fun elJsonConservaLosNull() {
        val json = CambiosDonacion.aJson(cambios(descripcion = "", titulo = "Otro"))
        val leido = JsonParser.parseString(json).asJsonObject
        assertEquals(setOf("titulo", "descripcion"), leido.keySet())
        assertTrue(leido["descripcion"].isJsonNull)
        assertEquals("Otro", leido["titulo"].asString)
    }
}
