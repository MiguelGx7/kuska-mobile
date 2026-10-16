package com.proyecto.kuska.ui.creardonacion

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.proyecto.kuska.data.api.modelos.Donacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConstructorDatosTest {

    /** 31/01/2027 a medianoche UTC (como lo entrega MaterialDatePicker). */
    private val fechaArroz = 1_801_353_600_000L

    /** 15/12/2026 a medianoche UTC. */
    private val fechaLeche = 1_797_292_800_000L

    private val arroz = AlimentoBorrador(
        rutaFoto = "/cache/fotos/foto_a.jpg",
        nombre = " Arroz ",
        descripcion = "Bolsas de 1 kg",
        categoriaId = 1,
        categoriaNombre = "Granos",
        unidadMedida = Donacion.UNIDAD_KG,
        cantidad = "5,50",
        fechaVencimiento = fechaArroz,
    )

    private val leche = AlimentoBorrador(
        rutaFoto = "/cache/fotos/foto_b.jpg",
        nombre = "Leche",
        descripcion = "  ",
        categoriaId = 2,
        categoriaNombre = "Lácteos",
        unidadMedida = Donacion.UNIDAD_UNIDAD,
        cantidad = "12",
        fechaVencimiento = fechaLeche,
    )

    /** El JSON tal como viaja en el campo "datos". */
    private fun json(
        titulo: String = " Mercado de la semana ",
        descripcion: String = "Productos en buen estado",
        direccion: String = "Calle 10 # 5-20, Bogotá",
        modalidad: String = Donacion.MODALIDAD_AMBAS,
        alimentos: List<AlimentoBorrador> = listOf(arroz, leche),
    ): JsonObject {
        val datos = ConstructorDatos.construir(titulo, descripcion, direccion, modalidad, alimentos)
        return JsonParser.parseString(Gson().toJson(datos)).asJsonObject
    }

    @Test
    fun donacionConLosCamposDelSchema() {
        val datos = json()
        assertEquals(
            setOf("titulo", "descripcion", "direccion_recogida", "modalidad_entrega", "alimentos"),
            datos.keySet(),
        )
        assertEquals("Mercado de la semana", datos["titulo"].asString)
        assertEquals("Productos en buen estado", datos["descripcion"].asString)
        assertEquals("Calle 10 # 5-20, Bogotá", datos["direccion_recogida"].asString)
        assertEquals("Ambas", datos["modalidad_entrega"].asString)
    }

    @Test
    fun alimentosEnOrdenYConLosCamposDelSchema() {
        val alimentos = json()["alimentos"].asJsonArray
        assertEquals(2, alimentos.size())

        val primero = alimentos[0].asJsonObject
        assertEquals(
            setOf("categoria", "nombre", "descripcion", "unidad_medida", "cantidad_total", "fecha_vencimiento"),
            primero.keySet(),
        )
        assertEquals(1, primero["categoria"].asInt)
        assertEquals("Arroz", primero["nombre"].asString)
        assertEquals("Bolsas de 1 kg", primero["descripcion"].asString)
        assertEquals("kg", primero["unidad_medida"].asString)
        assertEquals("2027-01-31", primero["fecha_vencimiento"].asString)

        val segundo = alimentos[1].asJsonObject
        assertEquals(2, segundo["categoria"].asInt)
        assertEquals("Leche", segundo["nombre"].asString)
        assertEquals("unidad", segundo["unidad_medida"].asString)
        assertEquals("2026-12-15", segundo["fecha_vencimiento"].asString)
    }

    @Test
    fun cantidadComoTextoConPunto() {
        val alimentos = json()["alimentos"].asJsonArray
        val primera = alimentos[0].asJsonObject["cantidad_total"]
        assertEquals(true, primera.asJsonPrimitive.isString)
        assertEquals("5.5", primera.asString)
        assertEquals("12", alimentos[1].asJsonObject["cantidad_total"].asString)
    }

    @Test
    fun opcionalesVaciosNoSeEnvian() {
        val datos = json(descripcion = "  ", direccion = "")
        assertFalse(datos.has("descripcion"))
        assertFalse(datos.has("direccion_recogida"))
        // La leche tiene descripción en blanco.
        assertFalse(datos["alimentos"].asJsonArray[1].asJsonObject.has("descripcion"))
    }

    @Test
    fun cadaModalidadSeEnviaTalCual() {
        listOf(Donacion.MODALIDAD_RECOGER, Donacion.MODALIDAD_DOMICILIO, Donacion.MODALIDAD_AMBAS).forEach {
            assertEquals(it, json(modalidad = it)["modalidad_entrega"].asString)
        }
    }
}
