package com.proyecto.kuska.ui.iniciodonante

import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.CantidadPorUnidad
import com.proyecto.kuska.data.api.modelos.CategoriaSerie
import com.proyecto.kuska.data.api.modelos.DashboardDonante
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.data.api.modelos.DonacionesPorEstado
import com.proyecto.kuska.data.api.modelos.MesDonante
import com.proyecto.kuska.ui.comun.CantidadConUnidad
import com.proyecto.kuska.ui.comun.EstadosVisuales
import com.proyecto.kuska.ui.comun.FamiliaColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun dashboard(
    estados: DonacionesPorEstado? = DonacionesPorEstado(2, 3, 1, 0, 4),
    entregas: Int = 5,
    alimentosEntregados: Int = 9,
    cantidades: List<CantidadPorUnidad>? = emptyList(),
    meses: List<MesDonante>? = emptyList(),
    categorias: List<CategoriaSerie>? = emptyList(),
) = DashboardDonante(
    donacionesPorEstado = estados, entregas = entregas, alimentosEntregados = alimentosEntregados,
    cantidadesEntregadas = cantidades, personasAyudadas = 3, porMes = meses, porCategoria = categorias,
)

private fun mes(mes: String, donaciones: Int = 0, entregas: Int = 0) =
    MesDonante(mes = mes, donaciones = donaciones, entregas = entregas, alimentosDonados = 0, alimentosEntregados = 0)

class ReglasInicioDonanteTest {

    @Test
    fun estadosEnElOrdenDeMisDonaciones() {
        val estados = ReglasInicioDonante.estados(dashboard())
        assertEquals(Donacion.ESTADOS, estados.map { it.estado })
        assertEquals(listOf(2, 3, 1, 0, 4), estados.map { it.cantidad })
    }

    @Test
    fun coloresAcordadosDeCadaEstado() {
        assertEquals(
            listOf(FamiliaColor.NARANJA, FamiliaColor.VERDE, FamiliaColor.ROJO, FamiliaColor.GRIS, FamiliaColor.GRIS),
            ReglasInicioDonante.estados(dashboard()).map { EstadosVisuales.familiaDonacion(it.estado) },
        )
    }

    @Test
    fun cuentaNuevaSoloSinNingunaDonacion() {
        assertTrue(ReglasInicioDonante.esCuentaNueva(dashboard(estados = DonacionesPorEstado(0, 0, 0, 0, 0))))
        assertTrue(ReglasInicioDonante.esCuentaNueva(dashboard(estados = null)))
        // Una sola Pendiente ya no es cuenta nueva.
        assertFalse(ReglasInicioDonante.esCuentaNueva(dashboard(estados = DonacionesPorEstado(1, 0, 0, 0, 0))))
    }

    @Test
    fun sinEntregas() {
        assertFalse(ReglasInicioDonante.tieneEntregas(dashboard(entregas = 0, alimentosEntregados = 0)))
        assertTrue(ReglasInicioDonante.tieneEntregas(dashboard(entregas = 1, alimentosEntregados = 0)))
    }

    @Test
    fun cantidadesAgrupadasPorUnidadSinSumarUnidadesDistintas() {
        val cantidades = ReglasInicioDonante.cantidades(
            dashboard(
                cantidades = listOf(
                    CantidadPorUnidad(Donacion.UNIDAD_UNIDAD, 3, "30.00"),
                    CantidadPorUnidad(Donacion.UNIDAD_L, 1, "4.00"),
                    CantidadPorUnidad(Donacion.UNIDAD_KG, 2, "12.50"),
                    CantidadPorUnidad(Donacion.UNIDAD_G, 0, "0.00"),
                ),
            )
        )
        assertEquals(
            listOf(
                CantidadConUnidad.ConSimbolo("12,5", "kg"),
                CantidadConUnidad.ConSimbolo("4", "L"),
                CantidadConUnidad.EnPlural("30", 30, R.plurals.cantidad_unidades),
            ),
            cantidades,
        )
    }

    @Test
    fun cantidadesIlegiblesODesconocidasNoRompen() {
        val cantidades = ReglasInicioDonante.cantidades(
            dashboard(
                cantidades = listOf(
                    CantidadPorUnidad("caja", 1, "2.00"),
                    CantidadPorUnidad(Donacion.UNIDAD_PAQUETE, 1, "1.00"),
                    CantidadPorUnidad(Donacion.UNIDAD_KG, 1, "mucho"),
                ),
            )
        )
        assertEquals(
            listOf(
                CantidadConUnidad.EnPlural("1", 1, R.plurals.cantidad_paquetes),
                CantidadConUnidad.ConSimbolo("2", "caja"),
            ),
            cantidades,
        )
        assertTrue(ReglasInicioDonante.cantidades(dashboard(cantidades = null)).isEmpty())
    }

    @Test
    fun mesesConSuInicialYAnio() {
        val meses = ReglasInicioDonante.meses(
            dashboard(meses = listOf(mes("2025-12", donaciones = 2), mes("2026-01", entregas = 1), mes("2026-13"), mes("ayer")))
        )
        assertEquals(listOf("D", "E", "?", "?"), meses.map { it.inicial })
        assertEquals(listOf(2025, 2026, null, null), meses.map { it.anio })
        assertEquals(listOf(12, 1, null, null), meses.map { it.mes })
        assertEquals(listOf(2, 0, 0, 0), meses.map { it.donaciones })
    }

    @Test
    fun sinActividadSiTodosLosMesesEnCero() {
        val enCero = ReglasInicioDonante.meses(dashboard(meses = (1..12).map { mes("2026-%02d".format(it)) }))
        assertTrue(ReglasInicioDonante.sinActividad(enCero))
        val conUna = ReglasInicioDonante.meses(dashboard(meses = listOf(mes("2026-01"), mes("2026-02", entregas = 1))))
        assertFalse(ReglasInicioDonante.sinActividad(conUna))
    }

    @Test
    fun categoriasSoloConAlgoQueMostrar() {
        val categorias = ReglasInicioDonante.categorias(
            dashboard(
                categorias = listOf(
                    CategoriaSerie(1, "Lácteos", 5, 2),
                    CategoriaSerie(2, "Panadería", 0, 0),
                    CategoriaSerie(3, "Frutas", 0, 1),
                ),
            )
        )
        assertEquals(listOf("Lácteos", "Frutas"), categorias.map { it.categoria })
    }

    @Test
    fun escalaDeLasBarras() {
        assertEquals(0, EscalaGrafica.maximo(emptyList()))
        assertEquals(7, EscalaGrafica.maximo(listOf(3, 7, 0)))
        assertEquals(0f, EscalaGrafica.proporcion(5, 0), 0f)
        assertEquals(0f, EscalaGrafica.proporcion(0, 10), 0f)
        assertEquals(1f, EscalaGrafica.proporcion(4, 4), 0f)
        assertEquals(0.25f, EscalaGrafica.proporcion(1, 4), 0.0001f)
        // Nunca pasa de 1 aunque el valor supere el máximo.
        assertEquals(1f, EscalaGrafica.proporcion(9, 4), 0f)
    }
}
