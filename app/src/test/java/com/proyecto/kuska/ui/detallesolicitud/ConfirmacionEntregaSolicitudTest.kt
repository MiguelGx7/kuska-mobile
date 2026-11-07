package com.proyecto.kuska.ui.detallesolicitud

import com.google.gson.Gson
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.SolicitudRecibida
import com.proyecto.kuska.ui.comun.FamiliaColor
import com.proyecto.kuska.ui.comun.TextoUi
import com.proyecto.kuska.util.FormatoFecha
import com.proyecto.kuska.util.Resultado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmacionEntregaSolicitudTest {

    /** El plazo de [entrega] es el 20/10/2026 a las 10:15 en Bogotá. */
    private val antes = FormatoFecha.milisDeFechaHora("2026-10-20T10:14:59-05:00")!!
    private val justo = FormatoFecha.milisDeFechaHora("2026-10-20T10:15:00-05:00")!!
    private val despues = FormatoFecha.milisDeFechaHora("2026-10-22T08:00:00-05:00")!!

    private fun entregada(confirmacion: String? = SolicitudRecibida.CONFIRMACION_PENDIENTE, hasta: String? = "2026-10-20T10:15:00-05:00") =
        solicitud(SolicitudRecibida.ESTADO_ENTREGADA, entrega = entrega(confirmacion, hasta))

    @Test
    fun soloPendienteYAntesDelPlazoPuedeResponder() {
        assertTrue(ConfirmacionEntregaSolicitud.puedeResponder(entregada(), antes))
        assertFalse(ConfirmacionEntregaSolicitud.puedeResponder(entregada(), justo))
        assertFalse(ConfirmacionEntregaSolicitud.puedeResponder(entregada(), despues))
    }

    @Test
    fun yaRespondidaNoPuedeResponder() {
        listOf(
            SolicitudRecibida.CONFIRMACION_CONFIRMADA,
            SolicitudRecibida.CONFIRMACION_ASUMIDA,
            SolicitudRecibida.CONFIRMACION_RECLAMADA,
            SolicitudRecibida.CONFIRMACION_REVISADA,
        ).forEach { assertFalse(it, ConfirmacionEntregaSolicitud.puedeResponder(entregada(it), antes)) }
    }

    @Test
    fun sinEntregadaOSinPlazoNoPuedeResponder() {
        val aprobada = solicitud(SolicitudRecibida.ESTADO_APROBADA, entrega = entrega())
        assertFalse(ConfirmacionEntregaSolicitud.puedeResponder(aprobada, antes))
        assertFalse(ConfirmacionEntregaSolicitud.puedeResponder(solicitud(SolicitudRecibida.ESTADO_ENTREGADA), antes))
        assertFalse(ConfirmacionEntregaSolicitud.puedeResponder(entregada(hasta = null), antes))
        assertFalse(ConfirmacionEntregaSolicitud.puedeResponder(entregada(hasta = "mañana"), antes))
    }

    @Test
    fun plazoVencidoSoloSiSiguePendiente() {
        assertTrue(ConfirmacionEntregaSolicitud.plazoVencido(entrega(), despues))
        assertTrue(ConfirmacionEntregaSolicitud.plazoVencido(entrega(), justo))
        assertFalse(ConfirmacionEntregaSolicitud.plazoVencido(entrega(), antes))
        assertFalse(ConfirmacionEntregaSolicitud.plazoVencido(entrega(SolicitudRecibida.CONFIRMACION_ASUMIDA), despues))
        assertFalse(ConfirmacionEntregaSolicitud.plazoVencido(entrega(hasta = null), despues))
    }

    @Test
    fun cuerpoDeLoRecibiSoloLlevaRecibido() {
        assertEquals("""{"recibido":true}""", Gson().toJson(ConfirmacionEntregaSolicitud.cuerpoRecibido()))
    }

    @Test
    fun cuerpoDelReclamoLlevaElComentarioRecortado() {
        assertEquals(
            """{"recibido":false,"comentario":"Nadie llegó"}""",
            Gson().toJson(ConfirmacionEntregaSolicitud.cuerpoReclamo("  Nadie llegó \n")),
        )
    }

    @Test
    fun comentarioObligatorioYConLargoMaximo() {
        assertEquals(TextoUi.Recurso(R.string.no_recibido_error_vacio), ConfirmacionEntregaSolicitud.validarComentario(""))
        assertEquals(TextoUi.Recurso(R.string.no_recibido_error_vacio), ConfirmacionEntregaSolicitud.validarComentario("   \n"))
        assertEquals(
            TextoUi.Recurso(R.string.error_largo_maximo, listOf(255)),
            ConfirmacionEntregaSolicitud.validarComentario("a".repeat(256)),
        )
        assertNull(ConfirmacionEntregaSolicitud.validarComentario("a".repeat(255)))
        assertNull(ConfirmacionEntregaSolicitud.validarComentario("No llegó el arroz"))
    }

    @Test
    fun errorDelComentarioVaEnSuCampo() {
        val error = Resultado.Error("Datos inválidos", 400, mapOf("comentario" to "Cuéntanos qué pasó."))
        assertEquals(
            FalloConfirmacion.EnCampo("Cuéntanos qué pasó."),
            ConfirmacionEntregaSolicitud.clasificar(error, RespuestaEntrega.RECLAMO),
        )
    }

    @Test
    fun sinConexionY429DelReclamoQuedanEnElDialogo() {
        assertEquals(
            FalloConfirmacion.EnDialogo("Sin conexión"),
            ConfirmacionEntregaSolicitud.clasificar(Resultado.Error("Sin conexión"), RespuestaEntrega.RECLAMO),
        )
        assertEquals(
            FalloConfirmacion.EnDialogo("Demasiados intentos"),
            ConfirmacionEntregaSolicitud.clasificar(Resultado.Error("Demasiados intentos", 429), RespuestaEntrega.RECLAMO),
        )
    }

    @Test
    fun errorDeLoRecibiVaAlAvisoRojo() {
        assertEquals(
            FalloConfirmacion.Aviso("Sin conexión", recargar = false),
            ConfirmacionEntregaSolicitud.clasificar(Resultado.Error("Sin conexión"), RespuestaEntrega.RECIBIDO),
        )
        assertEquals(
            FalloConfirmacion.Aviso("Error del servidor", recargar = false),
            ConfirmacionEntregaSolicitud.clasificar(Resultado.Error("Error del servidor", 500), RespuestaEntrega.RECIBIDO),
        )
    }

    @Test
    fun plazoVencidoEnLaApiVaAlAvisoYRecarga() {
        val regla = Resultado.Error("El plazo para confirmar ya terminó.", 400)
        listOf(RespuestaEntrega.RECIBIDO, RespuestaEntrega.RECLAMO).forEach {
            assertEquals(
                FalloConfirmacion.Aviso("El plazo para confirmar ya terminó.", recargar = true),
                ConfirmacionEntregaSolicitud.clasificar(regla, it),
            )
        }
        assertEquals(
            FalloConfirmacion.Aviso("No encontrada", recargar = true),
            ConfirmacionEntregaSolicitud.clasificar(Resultado.Error("No encontrada", 404), RespuestaEntrega.RECIBIDO),
        )
    }

    @Test
    fun colorDeCadaEstado() {
        assertEquals(FamiliaColor.NARANJA, ConfirmacionEntregaSolicitud.familia(SolicitudRecibida.CONFIRMACION_PENDIENTE))
        assertEquals(FamiliaColor.VERDE, ConfirmacionEntregaSolicitud.familia(SolicitudRecibida.CONFIRMACION_CONFIRMADA))
        assertEquals(FamiliaColor.VERDE, ConfirmacionEntregaSolicitud.familia(SolicitudRecibida.CONFIRMACION_ASUMIDA))
        assertEquals(FamiliaColor.ROJO, ConfirmacionEntregaSolicitud.familia(SolicitudRecibida.CONFIRMACION_RECLAMADA))
        assertEquals(FamiliaColor.GRIS, ConfirmacionEntregaSolicitud.familia(SolicitudRecibida.CONFIRMACION_REVISADA))
        assertEquals(FamiliaColor.GRIS, ConfirmacionEntregaSolicitud.familia("Otro"))
    }

    @Test
    fun textoDeCadaEstado() {
        assertEquals(R.string.confirmacion_pendiente, ConfirmacionEntregaSolicitud.etiqueta(SolicitudRecibida.CONFIRMACION_PENDIENTE))
        assertEquals(R.string.confirmacion_confirmada, ConfirmacionEntregaSolicitud.etiqueta(SolicitudRecibida.CONFIRMACION_CONFIRMADA))
        assertEquals(R.string.confirmacion_asumida, ConfirmacionEntregaSolicitud.etiqueta(SolicitudRecibida.CONFIRMACION_ASUMIDA))
        assertEquals(R.string.confirmacion_reclamada, ConfirmacionEntregaSolicitud.etiqueta(SolicitudRecibida.CONFIRMACION_RECLAMADA))
        assertEquals(R.string.confirmacion_revisada, ConfirmacionEntregaSolicitud.etiqueta(SolicitudRecibida.CONFIRMACION_REVISADA))
        assertNull(ConfirmacionEntregaSolicitud.etiqueta("Otro"))
    }
}
