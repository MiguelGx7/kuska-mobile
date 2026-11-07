package com.proyecto.kuska.ui.creardonacion

import com.proyecto.kuska.data.api.modelos.Donacion
import org.junit.Assert.assertEquals
import org.junit.Test

/** Campos de texto de POST donaciones/{id}/alimentos/ (AlimentoNuevoRequest). */
class CamposMultipartTest {

    /** 31/01/2027 a medianoche UTC. */
    private val fecha = 1_801_353_600_000L

    private val arroz = AlimentoBorrador(
        rutaFoto = "/cache/fotos/foto_a.jpg",
        nombre = " Arroz ",
        descripcion = "Bolsas de 1 kg",
        categoriaId = 3,
        categoriaNombre = "Granos",
        unidadMedida = Donacion.UNIDAD_KG,
        cantidad = "2,50",
        fechaVencimiento = fecha,
    )

    private fun campos(alimento: AlimentoBorrador) =
        ConstructorDatos.camposMultipart(ConstructorDatos.construirAlimento(alimento))

    @Test
    fun camposDelSchemaEnOrden() {
        assertEquals(
            listOf(
                "categoria" to "3",
                "nombre" to "Arroz",
                "descripcion" to "Bolsas de 1 kg",
                "unidad_medida" to "kg",
                "cantidad_total" to "2.5",
                "fecha_vencimiento" to "2027-01-31",
            ),
            campos(arroz),
        )
    }

    @Test
    fun descripcionVaciaNoSeEnvia() {
        assertEquals(
            listOf("categoria", "nombre", "unidad_medida", "cantidad_total", "fecha_vencimiento"),
            campos(arroz.copy(descripcion = "  ")).map { it.first },
        )
    }
}
