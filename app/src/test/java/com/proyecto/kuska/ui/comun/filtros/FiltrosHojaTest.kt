package com.proyecto.kuska.ui.comun.filtros

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltrosHojaTest {
    private val grupos = listOf(
        GrupoFiltro("categoria", "Categoría", listOf(OpcionFiltro("3", "Frutas"), OpcionFiltro("4", "Verduras"))),
        GrupoFiltro("modalidad", "Modalidad", listOf(
            OpcionFiltro("Recoger", "Recoger"),
            OpcionFiltro("Domicilio", "Domicilio"),
            OpcionFiltro("Ambas", "Ambas"),
        )),
    )

    private fun nuevaHoja(aplicados: Map<String, String> = emptyMap()) = FiltrosHoja().also {
        it.actualizarGrupos(grupos)
        it.fijarAplicados(aplicados)
    }

    @Test
    fun abrirCopiaLosAplicadosSinConfirmarLasSelecciones() {
        val hoja = nuevaHoja(mapOf("categoria" to "3"))
        hoja.abrir()
        hoja.seleccionar("categoria", "4")
        hoja.seleccionar("modalidad", "Recoger")
        assertEquals(mapOf("categoria" to "3"), hoja.estado.value.aplicados)
        assertEquals(mapOf("categoria" to "4", "modalidad" to "Recoger"), hoja.estado.value.borrador)
        assertEquals(1, hoja.estado.value.contador)
    }

    @Test
    fun aplicarConfirmaAmbosGruposJuntosYCierraLaEdicion() {
        val hoja = nuevaHoja()
        hoja.abrir()
        hoja.seleccionar("categoria", "3")
        hoja.seleccionar("modalidad", "Ambas")
        val seleccion = hoja.aplicar()
        assertEquals(mapOf("categoria" to "3", "modalidad" to "Ambas"), seleccion)
        assertEquals(seleccion, hoja.estado.value.aplicados)
        assertNull(hoja.estado.value.borrador)
        assertNull(hoja.aplicar())
    }

    @Test
    fun limpiarRequiereAplicarYConservaElContadorHastaEntonces() {
        val hoja = nuevaHoja(mapOf("categoria" to "3", "modalidad" to "Recoger"))
        hoja.abrir()
        hoja.limpiar()
        assertEquals(emptyMap<String, String>(), hoja.estado.value.borrador)
        assertEquals(2, hoja.estado.value.contador)
        assertEquals(emptyMap<String, String>(), hoja.aplicar())
        assertEquals(0, hoja.estado.value.contador)
    }

    @Test
    fun cerrarSinAplicarDescartaTambienLaLimpieza() {
        val aplicados = mapOf("categoria" to "3")
        val hoja = nuevaHoja(aplicados)
        hoja.abrir()
        hoja.limpiar()
        hoja.descartar()
        assertNull(hoja.estado.value.borrador)
        hoja.abrir()
        assertEquals(aplicados, hoja.estado.value.borrador)
    }

    @Test
    fun cerrarSinAplicarDescartaLosCambios() {
        val hoja = nuevaHoja(mapOf("categoria" to "3"))
        hoja.abrir()
        hoja.seleccionar("modalidad", "Domicilio")
        hoja.descartar()
        hoja.abrir()
        assertEquals(mapOf("categoria" to "3"), hoja.estado.value.borrador)
    }

    @Test
    fun todasQuitaSoloLaDimensionElegida() {
        val hoja = nuevaHoja(mapOf("categoria" to "3", "modalidad" to "Recoger"))
        hoja.abrir()
        hoja.seleccionar("categoria", null)
        assertEquals(mapOf("modalidad" to "Recoger"), hoja.aplicar())
        assertEquals(1, hoja.estado.value.contador)
    }

    @Test
    fun seleccionarOtraOpcionReemplazaLaAnteriorDelMismoGrupo() {
        val hoja = nuevaHoja()
        hoja.abrir()
        hoja.seleccionar("modalidad", "Recoger")
        hoja.seleccionar("modalidad", "Ambas")
        assertEquals(mapOf("modalidad" to "Ambas"), hoja.aplicar())
        assertEquals(1, hoja.estado.value.contador)
    }

    @Test
    fun recrearLaVistaConservaElBorradorEnElMismoControladorDelViewModel() {
        val hoja = nuevaHoja(mapOf("categoria" to "3"))
        hoja.abrir()
        hoja.seleccionar("modalidad", "Domicilio")
        // La vista recreada vuelve a abrir y a recibir los grupos, sin descartar la edición.
        hoja.abrir()
        hoja.actualizarGrupos(grupos)
        assertEquals(mapOf("categoria" to "3", "modalidad" to "Domicilio"), hoja.estado.value.borrador)
        assertEquals(mapOf("categoria" to "3"), hoja.estado.value.aplicados)
    }

    @Test
    fun opcionesQueLleganTardeNoReinicianLaEdicion() {
        val hoja = nuevaHoja(mapOf("categoria" to "3"))
        hoja.actualizarGrupos(grupos.map { if (it.clave == "categoria") it.copy(opciones = emptyList(), cargando = true) else it })
        hoja.abrir()
        hoja.seleccionar("modalidad", "Ambas")
        hoja.actualizarGrupos(grupos)
        assertEquals(mapOf("categoria" to "3", "modalidad" to "Ambas"), hoja.estado.value.borrador)
    }

    @Test
    fun categoriaAusenteOConErrorNoSeBorraSilenciosamente() {
        val hoja = nuevaHoja(mapOf("categoria" to "3"))
        hoja.actualizarGrupos(listOf(grupos.first().copy(opciones = emptyList(), error = "Sin conexión")))
        hoja.abrir()
        assertEquals(mapOf("categoria" to "3"), hoja.aplicar())
    }

    @Test
    fun ignoraOpcionesYGruposDesconocidos() {
        val hoja = nuevaHoja()
        hoja.abrir()
        hoja.seleccionar("categoria", "999")
        hoja.seleccionar("desconocido", "3")
        assertTrue(hoja.estado.value.borrador!!.isEmpty())
    }

    @Test
    fun cargaYErrorImpidenElegirOpcionesPeroPermitenTodas() {
        listOf(grupos.first().copy(cargando = true), grupos.first().copy(error = "Sin conexión")).forEach { grupo ->
            val hoja = nuevaHoja(mapOf("categoria" to "3"))
            hoja.actualizarGrupos(listOf(grupo))
            hoja.abrir()
            hoja.seleccionar("categoria", "4")
            assertEquals(mapOf("categoria" to "3"), hoja.estado.value.borrador)
            hoja.seleccionar("categoria", null)
            assertEquals(emptyMap<String, String>(), hoja.aplicar())
        }
    }

    @Test
    fun sinHojaAbiertaNoSePuedeEditarNiAplicar() {
        val hoja = nuevaHoja(mapOf("categoria" to "3"))
        hoja.limpiar()
        hoja.seleccionar("categoria", "4")
        assertNull(hoja.aplicar())
        assertEquals(mapOf("categoria" to "3"), hoja.estado.value.aplicados)
    }

    @Test
    fun admiteGruposDeOtrasPantallasSinConocerLaApi() {
        val hoja = FiltrosHoja()
        hoja.actualizarGrupos(listOf(GrupoFiltro("lectura", "Lectura", listOf(OpcionFiltro("no_leidas", "No leídas")))))
        hoja.abrir()
        hoja.seleccionar("lectura", "no_leidas")
        assertEquals(mapOf("lectura" to "no_leidas"), hoja.aplicar())
        hoja.fijarAplicados(emptyMap())
        assertEquals(0, hoja.estado.value.contador)
    }
}
