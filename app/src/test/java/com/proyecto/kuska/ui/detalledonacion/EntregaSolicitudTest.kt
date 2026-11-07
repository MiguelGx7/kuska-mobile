package com.proyecto.kuska.ui.detalledonacion

import com.google.gson.Gson
import com.proyecto.kuska.data.api.modelos.BeneficiarioContacto
import com.proyecto.kuska.data.api.modelos.SolicitudRecibida
import com.proyecto.kuska.util.Resultado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntregaSolicitudTest {

    private fun solicitud(estado: String, cancelado: Boolean = false) = SolicitudRecibida(
        solicitud = 1, codigoPedido = "KUM-1", estado = estado, modalidadEntrega = "Recoger",
        fechaSolicitud = null, fechaLimiteRecogida = null, cantidad = "1.00", cancelado = cancelado,
        beneficiario = BeneficiarioContacto("Laura", null, null), estadoConfirmacion = null,
    )

    @Test
    fun soloAprobadaNoCanceladaPermiteRegistrar() {
        assertTrue(EntregaSolicitud.puedeRegistrar(solicitud(SolicitudRecibida.ESTADO_APROBADA)))
        assertFalse(EntregaSolicitud.puedeRegistrar(solicitud(SolicitudRecibida.ESTADO_APROBADA, cancelado = true)))
        listOf(
            SolicitudRecibida.ESTADO_PENDIENTE, SolicitudRecibida.ESTADO_RECHAZADA, SolicitudRecibida.ESTADO_ENTREGADA,
            SolicitudRecibida.ESTADO_NO_RECOGIDA, SolicitudRecibida.ESTADO_CANCELADA,
        ).forEach { assertFalse(it, EntregaSolicitud.puedeRegistrar(solicitud(it))) }
    }

    @Test
    fun observacionVaciaNoSeEnvia() {
        assertEquals("{}", Gson().toJson(EntregaSolicitud.cuerpo("")))
        assertEquals("{}", Gson().toJson(EntregaSolicitud.cuerpo("   \n ")))
    }

    @Test
    fun observacionSeEnviaSinEspaciosSobrantes() {
        assertEquals("""{"observacion":"Faltó una panela."}""", Gson().toJson(EntregaSolicitud.cuerpo("  Faltó una panela. ")))
    }

    @Test
    fun errorDeLaObservacionVaAlCampo() {
        val error = Resultado.Error("Muy larga", 400, mapOf("observacion" to "Máximo 255 caracteres."))
        assertEquals(FalloEntrega.EnCampo("Máximo 255 caracteres."), EntregaSolicitud.clasificar(error))
    }

    @Test
    fun reglaDeNegocioVaAlAvisoYRecarga() {
        val error = Resultado.Error("No se puede entregar: hay una novedad pendiente.", 400)
        assertEquals(
            FalloEntrega.Aviso("No se puede entregar: hay una novedad pendiente.", recargar = true),
            EntregaSolicitud.clasificar(error),
        )
    }

    @Test
    fun noEncontradaVaAlAvisoYRecarga() {
        assertEquals(FalloEntrega.Aviso("No encontramos esa solicitud.", recargar = true),
            EntregaSolicitud.clasificar(Resultado.Error("No encontramos esa solicitud.", 404)))
    }

    @Test
    fun cuentaBloqueadaVaAlAvisoSinRecargar() {
        val error = Resultado.Error("Tu cuenta está bloqueada hasta el 20/10/2026 a las 18:30.", 403)
        assertEquals(FalloEntrega.Aviso(error.mensaje, recargar = false), EntregaSolicitud.clasificar(error))
    }

    @Test
    fun errorDelServidorVaAlAvisoSinRecargar() {
        assertEquals(FalloEntrega.Aviso("Error del servidor", recargar = false),
            EntregaSolicitud.clasificar(Resultado.Error("Error del servidor", 500)))
    }

    @Test
    fun sinConexionY429SeQuedanEnElDialogo() {
        assertEquals(FalloEntrega.EnDialogo("Sin conexión"), EntregaSolicitud.clasificar(Resultado.Error("Sin conexión")))
        assertEquals(FalloEntrega.EnDialogo("Muchos intentos"), EntregaSolicitud.clasificar(Resultado.Error("Muchos intentos", 429)))
    }
}
