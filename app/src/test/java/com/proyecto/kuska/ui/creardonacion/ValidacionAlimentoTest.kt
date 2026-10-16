package com.proyecto.kuska.ui.creardonacion

import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.ui.comun.TextoUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidacionAlimentoTest {

    private val dia = 24 * 60 * 60 * 1000L

    /** 16/10/2026 a medianoche UTC. */
    private val hoy = 1_792_108_800_000L

    private fun recurso(id: Int, vararg argumentos: Any) = TextoUi.Recurso(id, argumentos.toList())

    private fun valido() = AlimentoBorrador(
        rutaFoto = "/cache/fotos/foto_1.jpg",
        nombre = "Arroz",
        descripcion = "Bolsas de 1 kg",
        categoriaId = 1,
        categoriaNombre = "Granos",
        unidadMedida = Donacion.UNIDAD_KG,
        cantidad = "5",
        fechaVencimiento = hoy + 30 * dia,
    )

    private fun errores(alimento: AlimentoBorrador) = ValidacionAlimento.validar(alimento, hoy)

    @Test
    fun alimentoValido() {
        assertTrue(errores(valido()).isEmpty())
        assertTrue(errores(valido().copy(descripcion = "")).isEmpty())
    }

    @Test
    fun obligatorios() {
        assertEquals(
            mapOf(
                CampoAlimento.FOTO to recurso(R.string.error_foto_obligatoria),
                CampoAlimento.NOMBRE to recurso(R.string.error_campo_obligatorio),
                CampoAlimento.CATEGORIA to recurso(R.string.error_categoria),
                CampoAlimento.UNIDAD to recurso(R.string.error_unidad),
                CampoAlimento.CANTIDAD to recurso(R.string.error_cantidad_vacia),
                CampoAlimento.FECHA to recurso(R.string.error_fecha_vacia),
            ),
            errores(AlimentoBorrador(nombre = "   ", cantidad = " ")),
        )
    }

    @Test
    fun largosMaximos() {
        assertTrue(errores(valido().copy(nombre = "a".repeat(100))).isEmpty())
        assertEquals(
            mapOf(CampoAlimento.NOMBRE to recurso(R.string.error_largo_maximo, 100)),
            errores(valido().copy(nombre = "a".repeat(101))),
        )
        assertEquals(
            mapOf(CampoAlimento.DESCRIPCION to recurso(R.string.error_largo_maximo, 255)),
            errores(valido().copy(descripcion = "a".repeat(256))),
        )
    }

    @Test
    fun cantidadMayorQueCero() {
        val error = recurso(R.string.error_cantidad_mayor_cero)
        assertEquals(error, ValidacionAlimento.validarCantidad("0", Donacion.UNIDAD_KG))
        assertEquals(error, ValidacionAlimento.validarCantidad("0,00", Donacion.UNIDAD_KG))
        assertEquals(error, ValidacionAlimento.validarCantidad("0", Donacion.UNIDAD_UNIDAD))
    }

    @Test
    fun cantidadConFormatoInvalido() {
        val error = recurso(R.string.error_cantidad_invalida)
        listOf("-2", "2,", ",5", "2.5.1", "1e3", "dos", "2 5").forEach {
            assertEquals(it, error, ValidacionAlimento.validarCantidad(it, Donacion.UNIDAD_KG))
        }
    }

    @Test
    fun unidadYPaqueteSoloEnteros() {
        val error = recurso(R.string.error_cantidad_entera)
        listOf(Donacion.UNIDAD_UNIDAD, Donacion.UNIDAD_PAQUETE).forEach { unidad ->
            assertNull(ValidacionAlimento.validarCantidad("12", unidad))
            assertNull(ValidacionAlimento.validarCantidad("12,0", unidad))
            assertEquals(error, ValidacionAlimento.validarCantidad("2,5", unidad))
            assertEquals(error, ValidacionAlimento.validarCantidad("2.25", unidad))
        }
    }

    @Test
    fun demasUnidadesHastaDosDecimales() {
        listOf(Donacion.UNIDAD_KG, Donacion.UNIDAD_G, Donacion.UNIDAD_L, Donacion.UNIDAD_ML).forEach { unidad ->
            assertNull(ValidacionAlimento.validarCantidad("2,5", unidad))
            assertNull(ValidacionAlimento.validarCantidad("2.25", unidad))
            assertNull(ValidacionAlimento.validarCantidad("0.01", unidad))
            assertNull(ValidacionAlimento.validarCantidad("2,500", unidad))
            assertEquals(
                recurso(R.string.error_cantidad_decimales, 2),
                ValidacionAlimento.validarCantidad("2,555", unidad),
            )
        }
    }

    @Test
    fun cantidadMaximoOchoCifrasEnteras() {
        assertNull(ValidacionAlimento.validarCantidad("99999999.99", Donacion.UNIDAD_KG))
        assertEquals(
            recurso(R.string.error_cantidad_maxima, 8),
            ValidacionAlimento.validarCantidad("100000000", Donacion.UNIDAD_KG),
        )
    }

    @Test
    fun cantidadSinUnidadSoloRevisaFormato() {
        assertNull(ValidacionAlimento.validarCantidad("2,5", null))
    }

    @Test
    fun fechaPosteriorAHoy() {
        assertNull(ValidacionAlimento.validarFecha(hoy + dia, hoy))
        assertEquals(recurso(R.string.error_fecha_pasada), ValidacionAlimento.validarFecha(hoy, hoy))
        assertEquals(recurso(R.string.error_fecha_pasada), ValidacionAlimento.validarFecha(hoy - dia, hoy))
        assertEquals(recurso(R.string.error_fecha_vacia), ValidacionAlimento.validarFecha(null, hoy))
    }

    @Test
    fun normalizarCantidad() {
        assertEquals("2.5", ValidacionAlimento.normalizarCantidad(" 2,50 "))
        assertEquals("5", ValidacionAlimento.normalizarCantidad("5.0"))
        assertEquals("100", ValidacionAlimento.normalizarCantidad("100"))
        assertEquals("0.01", ValidacionAlimento.normalizarCantidad("0,01"))
    }
}
