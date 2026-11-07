package com.proyecto.kuska.ui.comun.formularioalimento

import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.CategoriaPublica
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.ui.comun.TextoUi
import com.proyecto.kuska.ui.creardonacion.AlimentoBorrador
import com.proyecto.kuska.ui.creardonacion.CampoAlimento
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class FormularioAlimentoTest {

    private val dia = 24 * 60 * 60 * 1000L

    /** 16/10/2026 a medianoche UTC. */
    private val hoy = 1_792_108_800_000L

    private val borradas = mutableListOf<String?>()
    private val fotosEnUso = mutableSetOf<String>()

    private fun TestScope.formulario() = FormularioAlimento(
        scope = this,
        borrarFoto = { borradas += it },
        fotoEnUso = { it in fotosEnUso },
        hoyUtc = { hoy },
    )

    private fun valido() = AlimentoBorrador(
        rutaFoto = "/cache/fotos/foto_a.jpg",
        nombre = "Arroz",
        categoriaId = 1,
        categoriaNombre = "Granos",
        unidadMedida = Donacion.UNIDAD_KG,
        cantidad = "5",
        fechaVencimiento = hoy + 30 * dia,
    )

    private fun FormularioAlimento.actual() = requireNotNull(edicion.value)

    @Test
    fun iniciarConservaLaEdicionDelMismoIndice() = runTest {
        val formulario = formulario()
        formulario.iniciar(-1, null)
        formulario.cambiarCampo(CampoAlimento.NOMBRE, "Arroz")

        // Al rotar la vista vuelve a pedir el mismo índice: no se pierde lo escrito.
        formulario.iniciar(-1, null)
        assertEquals("Arroz", formulario.actual().alimento.nombre)

        formulario.iniciar(0, valido().copy(nombre = "Leche"))
        assertEquals(0, formulario.actual().indice)
        assertEquals("Leche", formulario.actual().alimento.nombre)
    }

    @Test
    fun indiceSinAlimentoAbreUnoNuevo() = runTest {
        val formulario = formulario()
        formulario.iniciar(3, null)
        assertEquals(-1, formulario.actual().indice)
        assertEquals(AlimentoBorrador(), formulario.actual().alimento)
    }

    @Test
    fun cambiarCampoQuitaSuError() = runTest {
        val formulario = formulario()
        formulario.iniciar(-1, null)
        assertNull(formulario.validar())
        assertTrue(CampoAlimento.NOMBRE in formulario.actual().errores)

        formulario.cambiarCampo(CampoAlimento.NOMBRE, "Arroz")
        assertFalse(CampoAlimento.NOMBRE in formulario.actual().errores)
        assertTrue(CampoAlimento.CANTIDAD in formulario.actual().errores)
    }

    @Test
    fun mismoValorNoQuitaElError() = runTest {
        val formulario = formulario()
        formulario.iniciar(-1, null)
        formulario.validar()
        // La vista restaura su texto al rotar: un "cambio" al mismo valor no debe borrar el error.
        formulario.cambiarCampo(CampoAlimento.NOMBRE, "")
        assertTrue(CampoAlimento.NOMBRE in formulario.actual().errores)
    }

    @Test
    fun elegirCategoriaYFecha() = runTest {
        val formulario = formulario()
        formulario.iniciar(-1, null)
        formulario.elegirCategoria(CategoriaPublica(7, "Lácteos", null))
        formulario.elegirFecha(hoy + dia)
        assertEquals(7, formulario.actual().alimento.categoriaId)
        assertEquals("Lácteos", formulario.actual().alimento.categoriaNombre)
        assertEquals(hoy + dia, formulario.actual().alimento.fechaVencimiento)
    }

    @Test
    fun cambiarLaUnidadVuelveAValidarLaCantidad() = runTest {
        val formulario = formulario()
        formulario.iniciar(-1, null)
        formulario.cambiarCampo(CampoAlimento.CANTIDAD, "2,5")

        formulario.elegirUnidad(Donacion.UNIDAD_UNIDAD)
        assertEquals(
            TextoUi.Recurso(R.string.error_cantidad_entera),
            formulario.actual().errores[CampoAlimento.CANTIDAD],
        )

        formulario.elegirUnidad(Donacion.UNIDAD_KG)
        assertFalse(CampoAlimento.CANTIDAD in formulario.actual().errores)
    }

    @Test
    fun cambiarLaUnidadSinCantidadNoAgregaError() = runTest {
        val formulario = formulario()
        formulario.iniciar(-1, null)
        formulario.validar()
        assertTrue(CampoAlimento.UNIDAD in formulario.actual().errores)

        formulario.elegirUnidad(Donacion.UNIDAD_PAQUETE)
        assertFalse(CampoAlimento.UNIDAD in formulario.actual().errores)
        // La cantidad vacía no se marca al elegir la unidad (se marca al guardar).
        assertFalse(CampoAlimento.CANTIDAD in formulario.actual().errores)
    }

    @Test
    fun validarDevuelveLaEdicionSiEstaBien() = runTest {
        val formulario = formulario()
        formulario.iniciar(2, valido())
        val edicion = formulario.validar()
        assertNotNull(edicion)
        assertEquals(2, edicion!!.indice)
        // Validar no cierra el formulario: eso lo hace terminar().
        assertNotNull(formulario.edicion.value)
        formulario.terminar()
        assertNull(formulario.edicion.value)
        assertTrue(borradas.isEmpty())
    }

    @Test
    fun validarUsaLaFechaDeHoy() = runTest {
        val formulario = formulario()
        formulario.iniciar(-1, valido().copy(fechaVencimiento = hoy))
        assertNull(formulario.validar())
        assertEquals(
            TextoUi.Recurso(R.string.error_fecha_pasada),
            formulario.actual().errores[CampoAlimento.FECHA],
        )
    }

    @Test
    fun fotoNuevaReemplazaYBorraLaAnterior() = runTest {
        val formulario = formulario()
        formulario.iniciar(-1, valido().copy(rutaFoto = "/cache/fotos/vieja.jpg"))
        formulario.mostrarErrores(mapOf(CampoAlimento.FOTO to TextoUi.Texto("Foto inválida")))

        formulario.procesarFoto({ File("/cache/fotos/nueva.jpg") })
        assertTrue(formulario.actual().procesandoFoto)
        advanceUntilIdle()

        assertEquals(File("/cache/fotos/nueva.jpg").path, formulario.actual().alimento.rutaFoto)
        assertFalse(formulario.actual().procesandoFoto)
        assertFalse(CampoAlimento.FOTO in formulario.actual().errores)
        assertEquals(listOf<String?>("/cache/fotos/vieja.jpg"), borradas)
    }

    @Test
    fun fotoAnteriorEnUsoNoSeBorra() = runTest {
        // Editando un alimento de la lista: su foto la sigue usando la lista hasta que se guarde.
        fotosEnUso += "/cache/fotos/de_la_lista.jpg"
        val formulario = formulario()
        formulario.iniciar(0, valido().copy(rutaFoto = "/cache/fotos/de_la_lista.jpg"))

        formulario.procesarFoto({ File("/cache/fotos/nueva.jpg") })
        advanceUntilIdle()

        assertTrue(borradas.isEmpty())
        assertEquals(File("/cache/fotos/nueva.jpg").path, formulario.actual().alimento.rutaFoto)
    }

    @Test
    fun seBorraElOriginalDeLaCamara() = runTest {
        val camara = File.createTempFile("camara_", ".jpg")
        val formulario = formulario()
        formulario.iniciar(-1, null)

        formulario.procesarFoto({ File("/cache/fotos/reducida.jpg") }, archivoCamara = camara)
        advanceUntilIdle()

        assertFalse(camara.exists())
    }

    @Test
    fun fotoQueNoSeLeeMuestraError() = runTest {
        val formulario = formulario()
        formulario.iniciar(-1, null)

        formulario.procesarFoto({ null })
        advanceUntilIdle()

        assertFalse(formulario.actual().procesandoFoto)
        assertNull(formulario.actual().alimento.rutaFoto)
        assertEquals(
            TextoUi.Recurso(R.string.error_foto_no_leida),
            formulario.actual().errores[CampoAlimento.FOTO],
        )
    }

    @Test
    fun siSeCierraMientrasSeReduceSeBorraLaFotoNueva() = runTest {
        val formulario = formulario()
        formulario.iniciar(-1, null)

        formulario.procesarFoto({ File("/cache/fotos/tarde.jpg") })
        formulario.cancelar()
        advanceUntilIdle()

        assertNull(formulario.edicion.value)
        assertEquals(listOf<String?>(File("/cache/fotos/tarde.jpg").path), borradas)
    }

    @Test
    fun noValidaMientrasSeReduceLaFoto() = runTest {
        val formulario = formulario()
        formulario.iniciar(-1, valido())
        formulario.procesarFoto({ File("/cache/fotos/nueva.jpg") })
        assertNull(formulario.validar())
        assertTrue(formulario.actual().errores.isEmpty())
    }

    @Test
    fun cancelarBorraLaFotoNuevaSiNoSeUsa() = runTest {
        val formulario = formulario()
        formulario.iniciar(-1, valido().copy(rutaFoto = "/cache/fotos/sin_guardar.jpg"))
        formulario.cancelar()
        assertNull(formulario.edicion.value)
        assertEquals(listOf<String?>("/cache/fotos/sin_guardar.jpg"), borradas)
    }

    @Test
    fun cancelarNoBorraLaFotoDeUnAlimentoGuardado() = runTest {
        fotosEnUso += "/cache/fotos/guardada.jpg"
        val formulario = formulario()
        formulario.iniciar(0, valido().copy(rutaFoto = "/cache/fotos/guardada.jpg"))
        formulario.cancelar()
        assertTrue(borradas.isEmpty())
    }

    @Test
    fun erroresDelServidorSeSuman() = runTest {
        val formulario = formulario()
        formulario.iniciar(-1, valido())
        formulario.mostrarErrores(mapOf(CampoAlimento.FECHA to TextoUi.Texto("Debe vencer después del 18/10/2026.")))
        assertEquals(
            TextoUi.Texto("Debe vencer después del 18/10/2026."),
            formulario.actual().errores[CampoAlimento.FECHA],
        )
        // Al cambiar la fecha el error del servidor desaparece, igual que uno local.
        formulario.elegirFecha(hoy + 60 * dia)
        assertFalse(CampoAlimento.FECHA in formulario.actual().errores)
    }
}
