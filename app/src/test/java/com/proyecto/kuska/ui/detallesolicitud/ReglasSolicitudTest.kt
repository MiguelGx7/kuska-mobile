package com.proyecto.kuska.ui.detallesolicitud

import com.proyecto.kuska.data.api.modelos.ConfirmacionEntrega
import com.proyecto.kuska.data.api.modelos.DetalleSolicitud
import com.proyecto.kuska.data.api.modelos.DonanteContacto
import com.proyecto.kuska.data.api.modelos.MiSolicitud
import com.proyecto.kuska.data.api.modelos.SolicitudRecibida
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun detalle(id: Int, cancelado: Boolean = false) = DetalleSolicitud(
    id = id, alimento = id * 10, nombre = "Alimento $id", unidadMedida = "kg", cantidad = "2.00",
    cancelado = cancelado, fotoUrl = null,
)

internal fun entrega(confirmacion: String? = SolicitudRecibida.CONFIRMACION_PENDIENTE, hasta: String? = "2026-10-20T10:15:00-05:00") =
    ConfirmacionEntrega(
        fechaEntrega = "2026-10-18T10:15:00-05:00", observacion = null, estadoConfirmacion = confirmacion,
        confirmarHasta = hasta, fechaConfirmacion = null, comentarioBeneficiario = null, respuestaAdmin = null,
        fechaRevision = null,
    )

internal fun solicitud(
    estado: String = SolicitudRecibida.ESTADO_PENDIENTE,
    modalidad: String = MiSolicitud.MODALIDAD_RECOGER,
    direccionEntrega: String? = null,
    donante: DonanteContacto? = DonanteContacto("Panadería", "300 123 4567", "Calle 10 # 5-20"),
    fechaLimite: String? = "2026-10-20T18:30:00-05:00",
    detalles: List<DetalleSolicitud>? = listOf(detalle(1), detalle(2)),
    motivoRechazo: String? = null,
    motivoCancelacion: String? = null,
    entrega: ConfirmacionEntrega? = null,
) = MiSolicitud(
    id = 7, codigoPedido = "KUM-0012", estado = estado, modalidadEntrega = modalidad,
    direccionEntrega = direccionEntrega, motivoRechazo = motivoRechazo, motivoCancelacion = motivoCancelacion,
    fechaSolicitud = "2026-10-16T09:00:00-05:00", fechaGestion = null, fechaLimiteRecogida = fechaLimite,
    donacion = 3, donacionTitulo = "Mercado", donante = donante, detalles = detalles, entrega = entrega,
)

class ReglasSolicitudTest {

    @Test
    fun soloSeCancelaUnaPendiente() {
        assertTrue(ReglasSolicitud.puedeCancelar(solicitud(SolicitudRecibida.ESTADO_PENDIENTE)))
        listOf(
            SolicitudRecibida.ESTADO_APROBADA,
            SolicitudRecibida.ESTADO_RECHAZADA,
            SolicitudRecibida.ESTADO_ENTREGADA,
            SolicitudRecibida.ESTADO_NO_RECOGIDA,
            SolicitudRecibida.ESTADO_CANCELADA,
        ).forEach { assertFalse(it, ReglasSolicitud.puedeCancelar(solicitud(it))) }
    }

    @Test
    fun losAlimentosCanceladosNoCuentan() {
        val conCancelado = solicitud(detalles = listOf(detalle(1), detalle(2, cancelado = true), detalle(3)))
        assertEquals(2, ReglasSolicitud.alimentosVigentes(conCancelado))
        assertEquals(0, ReglasSolicitud.alimentosVigentes(solicitud(detalles = null)))
    }

    @Test
    fun enPendienteSoloElNombreAunqueLleguenMasDatos() {
        val datos = ReglasSolicitud.datosDonante(solicitud(SolicitudRecibida.ESTADO_PENDIENTE))!!
        assertEquals(DatosDonante("Panadería", esperaAprobacion = true), datos)
    }

    @Test
    fun enAprobadaSeDestacanLosDatosDeRecogida() {
        val datos = ReglasSolicitud.datosDonante(solicitud(SolicitudRecibida.ESTADO_APROBADA))!!
        assertTrue(datos.destacado)
        assertFalse(datos.esperaAprobacion)
        assertEquals("300 123 4567", datos.telefono)
        assertEquals("Calle 10 # 5-20", datos.direccion)
        assertEquals("2026-10-20T18:30:00-05:00", datos.fechaLimite)
    }

    @Test
    fun soloSeMuestraLoQueMandaLaApi() {
        val sinContacto = solicitud(
            SolicitudRecibida.ESTADO_APROBADA,
            donante = DonanteContacto("Panadería", null, "  "),
            fechaLimite = null,
        )
        val datos = ReglasSolicitud.datosDonante(sinContacto)!!
        assertNull(datos.telefono)
        assertNull(datos.direccion)
        assertNull(datos.fechaLimite)
        assertNull(ReglasSolicitud.datosDonante(solicitud(donante = null)))
    }

    @Test
    fun despuesDeAprobadaSeVenLosDatosSinDestacar() {
        val datos = ReglasSolicitud.datosDonante(solicitud(SolicitudRecibida.ESTADO_ENTREGADA))!!
        assertFalse(datos.destacado)
        assertEquals("300 123 4567", datos.telefono)
    }

    @Test
    fun numeroParaElMarcador() {
        assertEquals("3001234567", ReglasSolicitud.numeroMarcable("300 123 4567"))
        assertEquals("+573001234567", ReglasSolicitud.numeroMarcable(" +57 (300) 123-4567"))
        assertNull(ReglasSolicitud.numeroMarcable("sin número"))
        assertNull(ReglasSolicitud.numeroMarcable(null))
    }

    @Test
    fun motivoSoloEnRechazadaOCancelada() {
        assertEquals(
            MotivoSolicitud.Rechazo("Sin cupo"),
            ReglasSolicitud.motivo(solicitud(SolicitudRecibida.ESTADO_RECHAZADA, motivoRechazo = " Sin cupo ")),
        )
        assertEquals(
            MotivoSolicitud.Cancelacion("Novedad del donante"),
            ReglasSolicitud.motivo(solicitud(SolicitudRecibida.ESTADO_CANCELADA, motivoCancelacion = "Novedad del donante")),
        )
        assertNull(ReglasSolicitud.motivo(solicitud(SolicitudRecibida.ESTADO_CANCELADA, motivoCancelacion = "")))
        // Un motivo de cancelación en una solicitud que sigue Aprobada no se muestra.
        assertNull(ReglasSolicitud.motivo(solicitud(SolicitudRecibida.ESTADO_APROBADA, motivoCancelacion = "x")))
    }

    @Test
    fun direccionDeEntregaSoloEnDomicilio() {
        assertEquals(
            "Calle 1 Sur",
            ReglasSolicitud.direccionEntrega(solicitud(modalidad = MiSolicitud.MODALIDAD_DOMICILIO, direccionEntrega = "Calle 1 Sur")),
        )
        assertNull(ReglasSolicitud.direccionEntrega(solicitud(modalidad = MiSolicitud.MODALIDAD_RECOGER, direccionEntrega = "Calle 1 Sur")))
    }
}
