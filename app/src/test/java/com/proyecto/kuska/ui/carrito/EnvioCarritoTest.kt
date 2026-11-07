package com.proyecto.kuska.ui.carrito

import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.AlimentoDisponible
import com.proyecto.kuska.data.api.modelos.Carrito
import com.proyecto.kuska.data.api.modelos.CarritoGrupo
import com.proyecto.kuska.data.api.modelos.CarritoItem
import com.proyecto.kuska.data.api.modelos.EleccionDonacion
import com.proyecto.kuska.ui.comun.TextoUi
import com.proyecto.kuska.util.Resultado
import com.proyecto.kuska.util.extraerErroresCarrito
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvioCarritoTest {

    private fun alimento(id: Int, disponible: String = "5.00") = AlimentoDisponible(
        id = id, nombre = "Arroz", descripcion = null, categoria = 1, categoriaNombre = null, unidadMedida = "kg",
        cantidadDisponible = disponible, maxPorSolicitud = "2.00", fechaVencimiento = "2027-01-31", fotoUrl = null,
        donacion = 1, donacionTitulo = null, modalidadEntrega = "Ambas", donanteNombre = null,
    )

    private fun item(id: Int, disponible: String = "5.00") =
        CarritoItem(id = id, cantidad = "1.00", valido = true, motivos = emptyList(), alimento = alimento(id, disponible))

    private fun grupo(donacion: Int, modalidad: String, vararg items: CarritoItem) =
        CarritoGrupo(donacion, "Donación $donacion", modalidad, "Laura", items.toList())

    private val carrito = Carrito(
        donaciones = listOf(
            grupo(1, "Recoger", item(10)),
            grupo(2, "Ambas", item(20)),
            grupo(3, "Domicilio", item(30)),
            grupo(4, "Ambas", item(40), item(41)),
        ),
        avisos = emptyList(),
        puedeEnviar = true,
    )

    // --- Cuerpo del envío ---

    @Test
    fun elCuerpoSoloLlevaLasAmbasYLaDireccionSoloParaDomicilio() {
        val elecciones = mapOf(
            2 to EleccionEntrega("Recoger", direccion = "Calle 5"),
            4 to EleccionEntrega("Domicilio", direccion = "  Calle 1 Sur # 5-20  "),
        )
        assertEquals(
            listOf(
                EleccionDonacion(2, "Recoger", null),
                EleccionDonacion(4, "Domicilio", "Calle 1 Sur # 5-20"),
            ),
            EnvioCarrito.cuerpo(carrito, elecciones).donaciones,
        )
    }

    @Test
    fun sinDonacionesAmbasElCuerpoVaVacio() {
        val soloUna = Carrito(listOf(grupo(1, "Recoger", item(10))), emptyList(), true)
        assertTrue(EnvioCarrito.cuerpo(soloUna, emptyMap()).donaciones.isEmpty())
    }

    @Test
    fun seCreaUnaSolicitudPorDonacion() {
        assertEquals(4, EnvioCarrito.solicitudesACrear(carrito))
    }

    // --- Validación local ---

    @Test
    fun lasAmbasSinModalidadPidenElegir() {
        val errores = EnvioCarrito.validar(carrito, mapOf(2 to EleccionEntrega("Recoger")))
        assertEquals(mapOf(4 to TextoUi.Recurso(R.string.carrito_error_modalidad)), errores)
    }

    @Test
    fun domicilioSinDireccionEsError() {
        val errores = EnvioCarrito.validar(
            carrito,
            mapOf(2 to EleccionEntrega("Domicilio", direccion = "   "), 4 to EleccionEntrega("Recoger")),
        )
        assertEquals(mapOf(2 to TextoUi.Recurso(R.string.carrito_error_direccion)), errores)
    }

    @Test
    fun direccionDeMasDe200CaracteresEsError() {
        assertEquals(
            TextoUi.Recurso(R.string.error_largo_maximo, listOf(200)),
            EnvioCarrito.errorDireccion("a".repeat(201)),
        )
        assertNull(EnvioCarrito.errorDireccion("a".repeat(200)))
    }

    @Test
    fun conTodoElegidoNoHayErrores() {
        val elecciones = mapOf(2 to EleccionEntrega("Recoger"), 4 to EleccionEntrega("Domicilio", "Calle 1"))
        assertTrue(EnvioCarrito.validar(carrito, elecciones).isEmpty())
    }

    // --- Cuándo se puede enviar ---

    @Test
    fun soloSePuedeEnviarSiElServidorLoPermiteHayAlgoYNadaEnCurso() {
        assertTrue(EnvioCarrito.puedeEnviar(carrito, ocupado = false))
        assertFalse(EnvioCarrito.puedeEnviar(carrito, ocupado = true))
        assertFalse(EnvioCarrito.puedeEnviar(carrito.copy(puedeEnviar = false), ocupado = false))
        assertFalse(EnvioCarrito.puedeEnviar(Carrito(emptyList(), emptyList(), true), ocupado = false))
        assertFalse(EnvioCarrito.puedeEnviar(null, ocupado = false))
    }

    // --- Modalidad y dirección del perfil ---

    @Test
    fun alElegirDomicilioSeLlenaLaDireccionDelPerfil() {
        val eleccion = EnvioCarrito.elegir(EleccionEntrega(), "Domicilio", " Calle 9 ")
        assertEquals(EleccionEntrega("Domicilio", "Calle 9"), eleccion)
    }

    @Test
    fun noSePisaLaDireccionQueElUsuarioEscribioOBorro() {
        val escrita = EleccionEntrega("Recoger", "Mi casa", direccionEditada = true)
        assertEquals("Mi casa", EnvioCarrito.elegir(escrita, "Domicilio", "Calle 9").direccion)
        val borrada = EleccionEntrega("Recoger", "", direccionEditada = true)
        assertEquals("", EnvioCarrito.elegir(borrada, "Domicilio", "Calle 9").direccion)
    }

    @Test
    fun elegirRecogerConservaLaDireccion() {
        val eleccion = EnvioCarrito.elegir(EleccionEntrega("Domicilio", "Calle 9"), "Recoger", "Otra")
        assertEquals(EleccionEntrega("Recoger", "Calle 9"), eleccion)
    }

    @Test
    fun elPerfilQueLlegaTardeLlenaSoloLosDomicilioVacios() {
        val elecciones = mapOf(
            2 to EleccionEntrega("Domicilio"),
            4 to EleccionEntrega("Recoger"),
            5 to EleccionEntrega("Domicilio", "", direccionEditada = true),
        )
        val resultado = EnvioCarrito.conDireccionPerfil(elecciones, "Calle 9")
        assertEquals("Calle 9", resultado.getValue(2).direccion)
        assertEquals("", resultado.getValue(4).direccion)
        assertEquals("", resultado.getValue(5).direccion)
    }

    @Test
    fun alRecargarSeConservanSoloLasEleccionesDeAmbasQueSiguen() {
        val elecciones = mapOf(2 to EleccionEntrega("Recoger"), 1 to EleccionEntrega("Recoger"), 9 to EleccionEntrega("Recoger"))
        assertEquals(setOf(2), EnvioCarrito.conservar(elecciones, carrito).keys)
    }

    // --- Errores del 400 ---

    private fun error400(cuerpo: String) =
        Resultado.Error("Mensaje general", 400, extraerErroresCarrito(cuerpo))

    @Test
    fun repartirMandaCadaErrorASuLugar() {
        val errores = EnvioCarrito.repartir(
            error400(
                """{"carrito": ["Límite de solicitudes."], "items": {"20": ["Agotado."]},
                    "donaciones": {"4": ["Indica la dirección."]}, "otra_cosa": ["Raro."]}"""
            )
        )
        assertEquals(mapOf(20 to "Agotado."), errores.porItem)
        assertEquals(mapOf(4 to "Indica la dirección."), errores.porDonacion)
        assertEquals(listOf("Límite de solicitudes.", "Raro."), errores.generales)
    }

    @Test
    fun sinErroresReconociblesQuedaElMensajeDeLaRespuesta() {
        val errores = EnvioCarrito.repartir(Resultado.Error("No se pudo.", 400, emptyMap()))
        assertEquals(ErroresEnvio(generales = listOf("No se pudo.")), errores)
    }

    @Test
    fun losErroresDeItemsODonacionesQueYaNoEstanPasanAlAvisoGeneral() {
        val errores = ErroresEnvio(
            porItem = mapOf(20 to "Agotado.", 99 to "Ya no existe."),
            porDonacion = mapOf(4 to "Dirección.", 77 to "Donación cerrada."),
            generales = listOf("Límite."),
        )
        val ajustados = EnvioCarrito.ajustar(errores, carrito)
        assertEquals(mapOf(20 to "Agotado."), ajustados.porItem)
        assertEquals(mapOf(4 to "Dirección."), ajustados.porDonacion)
        assertEquals(listOf("Límite.", "Ya no existe.", "Donación cerrada."), ajustados.generales)
    }

    // --- Filas ---

    @Test
    fun lasFilasMuestranAvisosGruposEItemsConSusErrores() {
        val estado = CarritoEstado(
            carrito = carrito.copy(avisos = listOf("Superas el límite.")),
            errores = ErroresEnvio(porItem = mapOf(41 to "Agotado."), porDonacion = mapOf(4 to "Revisa."), generales = listOf("Otro.")),
            erroresLocales = mapOf(2 to TextoUi.Recurso(R.string.carrito_error_modalidad)),
            quitandoId = 30,
        )
        val filas = FilasCarrito.armar(estado)
        assertEquals(FilaCarrito.Avisos(listOf("Superas el límite.", "Otro.")), filas.first())
        val grupos = filas.filterIsInstance<FilaCarrito.Grupo>().associateBy { it.grupo.donacion }
        assertNull(grupos.getValue(1).eleccion)
        assertEquals(EleccionEntrega(), grupos.getValue(2).eleccion)
        assertEquals(TextoUi.Recurso(R.string.carrito_error_modalidad), grupos.getValue(2).error)
        assertEquals(TextoUi.Texto("Revisa."), grupos.getValue(4).error)
        val items = filas.filterIsInstance<FilaCarrito.Item>().associateBy { it.item.id }
        assertEquals("Agotado.", items.getValue(41).error)
        assertTrue(items.getValue(30).quitando)
        assertFalse(items.getValue(10).habilitado)
    }

    @Test
    fun unAlimentoSinNadaDisponibleNoSePuedeEditarSoloQuitar() {
        val agotado = Carrito(listOf(grupo(1, "Recoger", item(10, disponible = "0.00"))), emptyList(), false)
        val fila = FilasCarrito.armar(CarritoEstado(carrito = agotado)).filterIsInstance<FilaCarrito.Item>().single()
        assertFalse(fila.editable)
        assertTrue(fila.habilitado)
    }

    @Test
    fun elCarritoVacioNoTieneFilas() {
        val estado = CarritoEstado(carrito = Carrito(emptyList(), emptyList(), false))
        assertTrue(FilasCarrito.armar(estado).isEmpty())
        assertTrue(estado.vacio)
    }
}
