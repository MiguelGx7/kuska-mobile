package com.proyecto.kuska.ui.detalledonacion

import com.proyecto.kuska.data.api.modelos.Alimento
import com.proyecto.kuska.data.api.modelos.Donacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilasDetalleTest {

    private fun alimento(id: Int) = Alimento(
        id = id, categoria = 1, categoriaNombre = "Granos", nombre = "Alimento $id", descripcion = null,
        unidadMedida = "kg", cantidadTotal = "5.00", cantidadDisponible = "5.00", maxPorSolicitud = null,
        fechaVencimiento = "2027-01-31", fotoUrl = null, activo = true,
    )

    private fun donacion(estado: String, vararg ids: Int) = Donacion(
        id = 9, titulo = "Mercado", descripcion = null, direccionRecogida = null,
        modalidadEntrega = Donacion.MODALIDAD_AMBAS, estado = estado, motivoRechazo = null, fechaRevision = null,
        fechaRegistro = "2026-11-06T10:00:00-05:00", fechaActualizacion = null,
        alimentos = ids.map(::alimento),
    )

    private fun filas(estado: DetalleDonacionEstado) = construirFilas(estado, 1, 2, "Nadie ha pedido")

    private fun List<FilaDetalle>.alimentos() = filterIsInstance<FilaDetalle.AlimentoFila>()

    @Test
    fun pendienteConVariosAlimentosPermiteQuitar() {
        val filas = filas(DetalleDonacionEstado(donacion = donacion(Donacion.ESTADO_PENDIENTE, 1, 2)))
        assertEquals(listOf(ModoQuitar.PERMITIDO, ModoQuitar.PERMITIDO), filas.alimentos().map { it.quitar })
        assertTrue(filas.filterIsInstance<FilaDetalle.Encabezado>().single().editable)
        assertTrue(filas.filterIsInstance<FilaDetalle.AgregarAlimento>().single().habilitado)
        assertTrue(filas.filterIsInstance<FilaDetalle.Eliminar>().single().habilitado)
        assertTrue(filas.none { it is FilaDetalle.MensajeSolicitudes })
    }

    @Test
    fun pendienteConUnSoloAlimentoNoSeQuita() {
        val filas = filas(DetalleDonacionEstado(donacion = donacion(Donacion.ESTADO_PENDIENTE, 1)))
        assertEquals(listOf(ModoQuitar.ULTIMO), filas.alimentos().map { it.quitar })
    }

    @Test
    fun revisadaNoTieneAccionesDeEdicion() {
        val filas = filas(DetalleDonacionEstado(donacion = donacion(Donacion.ESTADO_VALIDADA, 1, 2)))
        assertEquals(listOf(ModoQuitar.NO, ModoQuitar.NO), filas.alimentos().map { it.quitar })
        assertFalse(filas.filterIsInstance<FilaDetalle.Encabezado>().single().editable)
        assertTrue(filas.none { it is FilaDetalle.AgregarAlimento || it is FilaDetalle.Eliminar })
        assertTrue(filas.any { it is FilaDetalle.MensajeSolicitudes })
    }

    @Test
    fun mientrasSeQuitaUnoTodoQuedaOcupado() {
        val filas = filas(
            DetalleDonacionEstado(donacion = donacion(Donacion.ESTADO_PENDIENTE, 1, 2), quitandoId = 2)
        )
        assertEquals(listOf(false, true), filas.alimentos().map { it.quitando })
        assertTrue(filas.alimentos().all { it.ocupado })
        assertTrue(filas.filterIsInstance<FilaDetalle.Encabezado>().single().ocupado)
        assertFalse(filas.filterIsInstance<FilaDetalle.AgregarAlimento>().single().habilitado)
        assertFalse(filas.filterIsInstance<FilaDetalle.Eliminar>().single().habilitado)
    }

    @Test
    fun elAvisoVaEnElEncabezado() {
        val filas = filas(
            DetalleDonacionEstado(donacion = donacion(Donacion.ESTADO_VALIDADA, 1), aviso = "Ya fue revisada.")
        )
        assertEquals("Ya fue revisada.", filas.filterIsInstance<FilaDetalle.Encabezado>().single().aviso)
    }

    @Test
    fun sinDonacionNoHayFilas() {
        assertTrue(filas(DetalleDonacionEstado()).isEmpty())
    }
}
