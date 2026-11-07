package com.proyecto.kuska.ui.comun

import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.util.Resultado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErroresAccionTest {

    private enum class Campo { TITULO, DIRECCION }

    private val campos = mapOf("titulo" to Campo.TITULO, "direccion_recogida" to Campo.DIRECCION)

    private fun donacion(estado: String) = Donacion(
        id = 1, titulo = "T", descripcion = null, direccionRecogida = null,
        modalidadEntrega = Donacion.MODALIDAD_AMBAS, estado = estado, motivoRechazo = null,
        fechaRevision = null, fechaRegistro = "2026-11-06T10:00:00-05:00", fechaActualizacion = null,
        alimentos = emptyList(),
    )

    @Test
    fun separaCamposConocidosDelResto() {
        val separados = ErroresAccion.separar(
            mapOf("titulo" to "Escribe el título.", "foto" to "Sube una imagen válida."),
            campos,
        )
        assertEquals(mapOf(Campo.TITULO to "Escribe el título."), separados.porCampo)
        assertEquals(listOf("Sube una imagen válida."), separados.generales)
    }

    @Test
    fun avisoConErroresGenerales() {
        val error = Resultado.Error("Primer mensaje", 400, mapOf("otro" to "Algo general"))
        assertEquals("Algo general", ErroresAccion.aviso(error, ErroresAccion.separar(error.erroresCampo, campos)))
    }

    @Test
    fun avisoSinErroresPorClaveUsaElMensaje() {
        val error = Resultado.Error("No se puede editar: la donación está Validada.", 400)
        assertEquals(error.mensaje, ErroresAccion.aviso(error, ErroresAccion.separar(error.erroresCampo, campos)))
    }

    @Test
    fun sinAvisoSiTodoEsDeUnCampo() {
        val error = Resultado.Error("Escribe el título.", 400, mapOf("titulo" to "Escribe el título."))
        assertNull(ErroresAccion.aviso(error, ErroresAccion.separar(error.erroresCampo, campos)))
    }

    @Test
    fun cuandoRevisarElEstado() {
        // Regla de negocio sin campos (p. ej. "ya no está Pendiente") o ya no existe.
        assertTrue(ErroresAccion.debeRevisarEstado(Resultado.Error("x", 400)))
        assertTrue(ErroresAccion.debeRevisarEstado(Resultado.Error("x", 404)))
        // Error de un campo: es del formulario, no del estado.
        assertFalse(ErroresAccion.debeRevisarEstado(Resultado.Error("x", 400, mapOf("titulo" to "x"))))
        // Sin conexión, sesión, cuenta bloqueada, demasiados intentos o servidor caído.
        listOf(null, 401, 403, 429, 500).forEach {
            assertFalse("$it", ErroresAccion.debeRevisarEstado(Resultado.Error("x", it)))
        }
    }

    @Test
    fun sigueEditableSegunLaConsulta() {
        assertTrue(ErroresAccion.sigueEditable(Resultado.Exito(donacion(Donacion.ESTADO_PENDIENTE))))
        assertFalse(ErroresAccion.sigueEditable(Resultado.Exito(donacion(Donacion.ESTADO_VALIDADA))))
        assertFalse(ErroresAccion.sigueEditable(Resultado.Exito(donacion(Donacion.ESTADO_RECHAZADA))))
        assertFalse(ErroresAccion.sigueEditable(Resultado.Error("No encontramos esa donación.", 404)))
        // Si no se pudo consultar (sin conexión), el formulario se queda con su mensaje.
        assertTrue(ErroresAccion.sigueEditable(Resultado.Error("Sin conexión")))
    }
}
