package com.proyecto.kuska.ui.detalledonacion

import com.google.gson.Gson
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.data.api.modelos.Novedad
import com.proyecto.kuska.ui.comun.TextoUi
import com.proyecto.kuska.util.Resultado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NovedadDonacionTest {

    private fun donacion(estado: String) = Donacion(
        id = 4, titulo = "Mercado", descripcion = null, direccionRecogida = null,
        modalidadEntrega = Donacion.MODALIDAD_AMBAS, estado = estado, motivoRechazo = null, fechaRevision = null,
        fechaRegistro = "2026-11-06T10:00:00-05:00", fechaActualizacion = null, alimentos = emptyList(),
    )

    private fun novedad(id: Int, alimentoId: Int?, estado: String, fecha: String = "2026-11-06T10:00:00-05:00") = Novedad(
        id = id, donacion = 4, donacionTitulo = "Mercado", donacionAlimento = alimentoId,
        alimentoNombre = alimentoId?.let { "Alimento $it" }, descripcion = "Se dañó", estado = estado,
        respuestaAdmin = null, fechaCreacion = fecha, fechaRevision = null,
    )

    @Test
    fun soloValidadaOAgotadaPermitenReportar() {
        assertTrue(NovedadDonacion.permiteReportar(donacion(Donacion.ESTADO_VALIDADA)))
        assertTrue(NovedadDonacion.permiteReportar(donacion(Donacion.ESTADO_AGOTADA)))
        listOf(Donacion.ESTADO_PENDIENTE, Donacion.ESTADO_RECHAZADA, Donacion.ESTADO_FINALIZADA).forEach {
            assertFalse(it, NovedadDonacion.permiteReportar(donacion(it)))
            assertEquals(it, ModoNovedad.NO, NovedadDonacion.modo(donacion(it), emptyList(), alimentoId = null))
        }
    }

    @Test
    fun lasNovedadesSeCarganEnCualquierDonacionNoPendiente() {
        assertFalse(NovedadDonacion.tieneNovedades(donacion(Donacion.ESTADO_PENDIENTE)))
        assertTrue(NovedadDonacion.tieneNovedades(donacion(Donacion.ESTADO_FINALIZADA)))
        assertTrue(NovedadDonacion.tieneNovedades(donacion(Donacion.ESTADO_RECHAZADA)))
    }

    @Test
    fun pendienteSobreUnAlimentoSoloOcultaEseBoton() {
        val validada = donacion(Donacion.ESTADO_VALIDADA)
        val novedades = listOf(novedad(1, alimentoId = 10, estado = Novedad.ESTADO_PENDIENTE))
        assertEquals(ModoNovedad.EN_REVISION, NovedadDonacion.modo(validada, novedades, alimentoId = 10))
        assertEquals(ModoNovedad.REPORTAR, NovedadDonacion.modo(validada, novedades, alimentoId = 11))
        assertEquals(ModoNovedad.REPORTAR, NovedadDonacion.modo(validada, novedades, alimentoId = null))
    }

    @Test
    fun pendienteGeneralSoloOcultaElBotonGeneral() {
        val validada = donacion(Donacion.ESTADO_AGOTADA)
        val novedades = listOf(novedad(1, alimentoId = null, estado = Novedad.ESTADO_PENDIENTE))
        assertEquals(ModoNovedad.EN_REVISION, NovedadDonacion.modo(validada, novedades, alimentoId = null))
        assertEquals(ModoNovedad.REPORTAR, NovedadDonacion.modo(validada, novedades, alimentoId = 10))
    }

    @Test
    fun lasYaRevisadasNoBloqueanUnaNueva() {
        val validada = donacion(Donacion.ESTADO_VALIDADA)
        val novedades = listOf(
            novedad(1, alimentoId = 10, estado = Novedad.ESTADO_ACEPTADA),
            novedad(2, alimentoId = null, estado = Novedad.ESTADO_RECHAZADA),
        )
        assertEquals(ModoNovedad.REPORTAR, NovedadDonacion.modo(validada, novedades, alimentoId = 10))
        assertEquals(ModoNovedad.REPORTAR, NovedadDonacion.modo(validada, novedades, alimentoId = null))
    }

    @Test
    fun sinNovedadesCargadasSeMuestraElBoton() {
        assertEquals(ModoNovedad.REPORTAR, NovedadDonacion.modo(donacion(Donacion.ESTADO_VALIDADA), null, alimentoId = 10))
    }

    @Test
    fun cuerpoSobreUnAlimento() {
        val cuerpo = NovedadDonacion.cuerpo(4, ObjetivoNovedad(15), "  Se dañó en la nevera. ")
        assertEquals("""{"donacion":4,"donacion_alimento":15,"descripcion":"Se dañó en la nevera."}""", Gson().toJson(cuerpo))
    }

    @Test
    fun cuerpoSobreTodaLaDonacionNoEnviaElAlimento() {
        val cuerpo = NovedadDonacion.cuerpo(4, ObjetivoNovedad(null), "Ya no puedo entregar esta donación.")
        assertEquals("""{"donacion":4,"descripcion":"Ya no puedo entregar esta donación."}""", Gson().toJson(cuerpo))
    }

    @Test
    fun descripcionObligatoria() {
        assertEquals(TextoUi.Recurso(R.string.novedad_error_descripcion), NovedadDonacion.validarDescripcion(""))
        assertEquals(TextoUi.Recurso(R.string.novedad_error_descripcion), NovedadDonacion.validarDescripcion("  \n"))
        assertNull(NovedadDonacion.validarDescripcion("Se dañó"))
    }

    @Test
    fun laMasRecientePrimero() {
        val novedades = listOf(
            novedad(1, null, Novedad.ESTADO_RECHAZADA, "2026-11-01T10:00:00-05:00"),
            novedad(2, 10, Novedad.ESTADO_PENDIENTE, "2026-11-05T10:00:00-05:00"),
            novedad(3, 11, Novedad.ESTADO_ACEPTADA, "sin fecha"),
            novedad(4, 12, Novedad.ESTADO_ACEPTADA, "2026-11-03T10:00:00Z"),
        )
        assertEquals(listOf(2, 4, 1, 3), NovedadDonacion.ordenar(novedades).map { it.id })
    }

    @Test
    fun errorDeLaDescripcionVaAlCampo() {
        val error = Resultado.Error("Vacía", 400, mapOf("descripcion" to "Este campo no puede estar en blanco."))
        assertEquals(FalloNovedad.EnCampo("Este campo no puede estar en blanco."), NovedadDonacion.clasificar(error))
    }

    @Test
    fun duplicadaVaAlAvisoYRecarga() {
        val error = Resultado.Error("Ya hay una novedad Pendiente sobre este alimento.", 400)
        assertEquals(FalloNovedad.Aviso(error.mensaje, recargar = true), NovedadDonacion.clasificar(error))
    }

    @Test
    fun errorDeOtroCampoVaAlAvisoSinRecargar() {
        val error = Resultado.Error("x", 400, mapOf("donacion_alimento" to "El alimento no es de esta donación."))
        assertEquals(FalloNovedad.Aviso("El alimento no es de esta donación.", recargar = false), NovedadDonacion.clasificar(error))
    }

    @Test
    fun noEncontradaVaAlAvisoYRecarga() {
        assertEquals(FalloNovedad.Aviso("No encontramos esa donación.", recargar = true),
            NovedadDonacion.clasificar(Resultado.Error("No encontramos esa donación.", 404)))
    }

    @Test
    fun cuentaBloqueadaYServidorVanAlAvisoSinRecargar() {
        assertEquals(FalloNovedad.Aviso("Bloqueada", recargar = false), NovedadDonacion.clasificar(Resultado.Error("Bloqueada", 403)))
        assertEquals(FalloNovedad.Aviso("Servidor", recargar = false), NovedadDonacion.clasificar(Resultado.Error("Servidor", 503)))
    }

    @Test
    fun sinConexionY429SeQuedanEnElDialogo() {
        assertEquals(FalloNovedad.EnDialogo("Sin conexión"), NovedadDonacion.clasificar(Resultado.Error("Sin conexión")))
        assertEquals(FalloNovedad.EnDialogo("Muchos intentos"), NovedadDonacion.clasificar(Resultado.Error("Muchos intentos", 429)))
    }
}
