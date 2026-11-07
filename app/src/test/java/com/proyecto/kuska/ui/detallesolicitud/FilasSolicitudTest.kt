package com.proyecto.kuska.ui.detallesolicitud

import com.proyecto.kuska.data.api.modelos.SolicitudRecibida
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilasSolicitudTest {

    private val tituloEntrega = 1
    private val tituloAlimentos = 2

    private fun filas(estado: DetalleSolicitudEstado) = construirFilasSolicitud(estado, tituloEntrega, tituloAlimentos)

    @Test
    fun sinSolicitudNoHayFilas() {
        assertTrue(filas(DetalleSolicitudEstado()).isEmpty())
    }

    @Test
    fun pendienteTerminaConCancelar() {
        val lista = filas(DetalleSolicitudEstado(solicitud = solicitud(SolicitudRecibida.ESTADO_PENDIENTE)))
        assertEquals(
            listOf(
                FilaSolicitud.Encabezado::class, FilaSolicitud.Donante::class, FilaSolicitud.Seccion::class,
                FilaSolicitud.AlimentoFila::class, FilaSolicitud.AlimentoFila::class, FilaSolicitud.Cancelar::class,
            ),
            lista.map { it::class },
        )
        assertEquals(FilaSolicitud.Cancelar(cancelando = false, habilitado = true), lista.last())
    }

    @Test
    fun mientrasCancelaElBotonQuedaBloqueado() {
        val lista = filas(DetalleSolicitudEstado(solicitud = solicitud(), cancelando = true))
        assertEquals(FilaSolicitud.Cancelar(cancelando = true, habilitado = false), lista.last())
    }

    @Test
    fun soloLaPendienteTieneCancelar() {
        listOf(
            SolicitudRecibida.ESTADO_APROBADA,
            SolicitudRecibida.ESTADO_ENTREGADA,
            SolicitudRecibida.ESTADO_CANCELADA,
        ).forEach { estado ->
            val lista = filas(DetalleSolicitudEstado(solicitud = solicitud(estado)))
            assertFalse(estado, lista.any { it is FilaSolicitud.Cancelar })
        }
    }

    @Test
    fun alimentoCanceladoSaleTachado() {
        val lista = filas(
            DetalleSolicitudEstado(solicitud = solicitud(detalles = listOf(detalle(1), detalle(2, cancelado = true))))
        )
        val alimentos = lista.filterIsInstance<FilaSolicitud.AlimentoFila>()
        assertEquals(listOf(false, true), alimentos.map { it.tachado })
    }

    @Test
    fun laEntregaSoloApareceSiLlega() {
        val sin = filas(DetalleSolicitudEstado(solicitud = solicitud(SolicitudRecibida.ESTADO_ENTREGADA)))
        assertFalse(sin.any { it is FilaSolicitud.Entrega })

        val con = filas(
            DetalleSolicitudEstado(solicitud = solicitud(SolicitudRecibida.ESTADO_ENTREGADA, entrega = entrega()))
        )
        val indice = con.indexOfFirst { it is FilaSolicitud.Entrega }
        assertEquals(FilaSolicitud.Seccion(tituloEntrega), con[indice - 1])
        assertTrue((con[indice] as FilaSolicitud.Entrega).mostrarPlazo)
    }

    @Test
    fun encabezadoLlevaMotivoYAviso() {
        val estado = DetalleSolicitudEstado(
            solicitud = solicitud(SolicitudRecibida.ESTADO_RECHAZADA, motivoRechazo = "Sin cupo"),
            aviso = "Sin conexión",
        )
        val encabezado = filas(estado).first() as FilaSolicitud.Encabezado
        assertEquals(MotivoSolicitud.Rechazo("Sin cupo"), encabezado.motivo)
        assertEquals("Sin conexión", encabezado.aviso)
    }
}
