package com.proyecto.kuska.ui.detallealimento

import com.google.gson.Gson
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.AlimentoDisponible
import com.proyecto.kuska.ui.comun.TextoUi
import com.proyecto.kuska.util.Resultado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CantidadPedidoTest {

    private fun alimento(unidad: String, disponible: String, maximo: String?) = AlimentoDisponible(
        id = 15, nombre = "Arroz", descripcion = null, categoria = 1, categoriaNombre = "Granos", unidadMedida = unidad,
        cantidadDisponible = disponible, maxPorSolicitud = maximo, fechaVencimiento = "2027-01-31", fotoUrl = null,
        donacion = 4, donacionTitulo = "Mercado", modalidadEntrega = "Ambas", donanteNombre = "Laura",
    )

    private fun bd(texto: String) = BigDecimal(texto)

    @Test
    fun elMaximoEsElMenorEntreMaxPorSolicitudYDisponible() {
        assertEquals(0, bd("2").compareTo(CantidadPedido.maximo(alimento("kg", "5.00", "2.00"))))
        assertEquals(0, bd("1.5").compareTo(CantidadPedido.maximo(alimento("kg", "1.50", "3.00"))))
    }

    @Test
    fun sinMaxPorSolicitudElMaximoEsLoDisponible() {
        assertEquals(0, bd("4.25").compareTo(CantidadPedido.maximo(alimento("L", "4.25", null))))
    }

    @Test
    fun enUnidadYPaqueteElMaximoSeRedondeaHaciaAbajo() {
        assertEquals(0, bd("3").compareTo(CantidadPedido.maximo(alimento("unidad", "3.00", null))))
        assertEquals(0, bd("2").compareTo(CantidadPedido.maximo(alimento("paquete", "5.00", "2.50"))))
        assertNull(CantidadPedido.maximo(alimento("unidad", "0.50", null)))
    }

    @Test
    fun sinNadaQuePedirNoHayMaximo() {
        assertNull(CantidadPedido.maximo(alimento("kg", "0.00", "2.00")))
        assertNull(CantidadPedido.maximo(alimento("kg", "abc", null)))
    }

    @Test
    fun soloEnterosEnUnidadYPaquete() {
        assertFalse(CantidadPedido.admiteDecimales("unidad"))
        assertFalse(CantidadPedido.admiteDecimales("paquete"))
        listOf("kg", "g", "L", "ml").forEach { assertTrue(it, CantidadPedido.admiteDecimales(it)) }
    }

    @Test
    fun empiezaEnUnoOEnElMaximoSiEsMenor() {
        assertEquals("1", CantidadPedido.inicial(bd("5")))
        assertEquals("0,5", CantidadPedido.inicial(bd("0.5")))
    }

    @Test
    fun sumarYRestarDeAUnoDentroDeLosLimites() {
        val maximo = bd("2.5")
        assertEquals("2", CantidadPedido.sumar("1", maximo))
        assertEquals("2,5", CantidadPedido.sumar("2", maximo))
        assertEquals("2,5", CantidadPedido.sumar("2,5", maximo))
        assertEquals("1,5", CantidadPedido.restar("2,5", maximo))
        assertEquals("1", CantidadPedido.restar("1,5", maximo))
        assertEquals("1", CantidadPedido.restar("1", maximo))
        assertEquals("1", CantidadPedido.sumar("", maximo))
    }

    @Test
    fun habilitarLosBotones() {
        val maximo = bd("3")
        assertFalse(CantidadPedido.puedeRestar("1", maximo))
        assertTrue(CantidadPedido.puedeRestar("2", maximo))
        assertTrue(CantidadPedido.puedeSumar("2", maximo))
        assertFalse(CantidadPedido.puedeSumar("3", maximo))
        assertFalse(CantidadPedido.puedeRestar("", maximo))
        assertTrue(CantidadPedido.puedeSumar("", maximo))
    }

    @Test
    fun validar() {
        val maximo = bd("2.5")
        assertNull(CantidadPedido.validar("2,5", "kg", maximo))
        assertNull(CantidadPedido.validar("0.75", "kg", maximo))
        assertEquals(TextoUi.Recurso(R.string.carrito_error_maximo, listOf("2,5")), CantidadPedido.validar("3", "kg", maximo))
        assertEquals(TextoUi.Recurso(R.string.error_cantidad_decimales, listOf(2)), CantidadPedido.validar("1,255", "kg", maximo))
        assertEquals(TextoUi.Recurso(R.string.error_cantidad_entera), CantidadPedido.validar("1,5", "unidad", bd("3")))
        assertEquals(TextoUi.Recurso(R.string.error_cantidad_vacia), CantidadPedido.validar(" ", "kg", maximo))
        assertEquals(TextoUi.Recurso(R.string.error_cantidad_mayor_cero), CantidadPedido.validar("0", "kg", maximo))
    }

    @Test
    fun cuerpoDelPost() {
        assertEquals("""{"donacion_alimento":15,"cantidad":"2.5"}""", Gson().toJson(CantidadPedido.cuerpo(15, "2,50")))
        assertEquals("""{"donacion_alimento":15,"cantidad":"3"}""", Gson().toJson(CantidadPedido.cuerpo(15, " 3 ")))
    }

    @Test
    fun errorDeCantidadVaAlCampo() {
        val error = Resultado.Error("x", 400, mapOf("cantidad" to "Máximo 2 por solicitud."))
        assertEquals(FalloCarrito.EnCampo("Máximo 2 por solicitud."), CantidadPedido.clasificar(error))
    }

    @Test
    fun reglaDelServidorVaAlAvisoYRecargaLaLista() {
        val error = Resultado.Error("Ya tienes una solicitud activa en esta donación.", 400)
        assertEquals(FalloCarrito.Aviso(error.mensaje, recargarLista = true), CantidadPedido.clasificar(error))
        val noDisponible = Resultado.Error("x", 400, mapOf("donacion_alimento" to "Este alimento ya no está disponible."))
        assertEquals(
            FalloCarrito.Aviso("Este alimento ya no está disponible.", recargarLista = true),
            CantidadPedido.clasificar(noDisponible),
        )
    }

    @Test
    fun sinConexion429Y403VanAlAvisoSinRecargar() {
        listOf(Resultado.Error("Sin conexión"), Resultado.Error("Muchos", 429), Resultado.Error("Bloqueada", 403)).forEach {
            assertEquals(FalloCarrito.Aviso(it.mensaje, recargarLista = false), CantidadPedido.clasificar(it))
        }
    }
}
