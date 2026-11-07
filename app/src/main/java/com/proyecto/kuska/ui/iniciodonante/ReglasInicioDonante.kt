package com.proyecto.kuska.ui.iniciodonante

import com.proyecto.kuska.data.api.modelos.CategoriaSerie
import com.proyecto.kuska.data.api.modelos.DashboardDonante
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.ui.comun.CantidadConUnidad
import com.proyecto.kuska.ui.comun.FormatoCantidad
import java.math.BigDecimal

/** Cuántas donaciones tiene en un estado (HU-035). */
data class ConteoEstado(val estado: String, val cantidad: Int)

/** Un mes de la gráfica (HU-037). [inicial]: "E", "F"…; [mes] 1 a 12 y [anio], o null si no se pudo leer. */
data class BarraMes(
    val inicial: String,
    val anio: Int?,
    val mes: Int?,
    val donaciones: Int,
    val entregas: Int,
)

/** Reglas del inicio del donante (HU-035 a HU-037). */
object ReglasInicioDonante {

    private val formatoMes = Regex("^(\\d{4})-(\\d{2})$")
    private const val INICIALES = "EFMAMJJASOND"

    /** Las 5 cifras en el orden de los chips de Mis donaciones. */
    fun estados(dashboard: DashboardDonante): List<ConteoEstado> {
        val conteo = dashboard.donacionesPorEstado
        val valores = listOf(conteo?.pendiente, conteo?.validada, conteo?.rechazada, conteo?.agotada, conteo?.finalizada)
        return Donacion.ESTADOS.zip(valores) { estado, cantidad -> ConteoEstado(estado, (cantidad ?: 0).coerceAtLeast(0)) }
    }

    /** Sin ninguna donación (en ningún estado): estado vacío que invita a crear la primera. */
    fun esCuentaNueva(dashboard: DashboardDonante): Boolean = estados(dashboard).all { it.cantidad == 0 }

    fun tieneEntregas(dashboard: DashboardDonante): Boolean = dashboard.entregas > 0 || dashboard.alimentosEntregados > 0

    /**
     * Cantidades entregadas, una por unidad (no se suman unidades distintas), en el orden de [Donacion.UNIDADES]
     * y luego las que la app no conoce. Se omiten las que están en 0 o no se pueden leer.
     */
    fun cantidades(dashboard: DashboardDonante): List<CantidadConUnidad> =
        dashboard.cantidadesEntregadas.orEmpty()
            .filter { esPositiva(it.cantidad) }
            .sortedBy { Donacion.UNIDADES.indexOf(it.unidadMedida).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            .map { FormatoCantidad.conUnidad(it.cantidad, it.unidadMedida) }

    /** Los meses tal como llegan (la API manda los 12, del más viejo al más nuevo). */
    fun meses(dashboard: DashboardDonante): List<BarraMes> = dashboard.porMes.orEmpty().map { mes ->
        val partes = formatoMes.matchEntire(mes.mes.trim())?.destructured
        val anio = partes?.component1()?.toIntOrNull()
        val numero = partes?.component2()?.toIntOrNull()?.takeIf { it in 1..12 }
        BarraMes(
            inicial = numero?.let { INICIALES[it - 1].toString() } ?: "?",
            anio = anio.takeIf { numero != null },
            mes = numero,
            donaciones = mes.donaciones.coerceAtLeast(0),
            entregas = mes.entregas.coerceAtLeast(0),
        )
    }

    /** Todos los meses en 0: en lugar de una gráfica vacía, un mensaje. */
    fun sinActividad(meses: List<BarraMes>): Boolean = meses.all { it.donaciones == 0 && it.entregas == 0 }

    /** Categorías con algo que mostrar. */
    fun categorias(dashboard: DashboardDonante): List<CategoriaSerie> =
        dashboard.porCategoria.orEmpty().filter { it.alimentosDonados > 0 || it.alimentosEntregados > 0 }

    private fun esPositiva(cantidad: String): Boolean = try {
        BigDecimal(cantidad.trim()).signum() > 0
    } catch (_: NumberFormatException) {
        false
    }
}

/** Escala de las barras: proporción de [valor] respecto al [maximo] de la serie, entre 0 y 1. */
object EscalaGrafica {

    fun maximo(valores: Iterable<Int>): Int = valores.maxOrNull()?.coerceAtLeast(0) ?: 0

    fun proporcion(valor: Int, maximo: Int): Float {
        if (maximo <= 0 || valor <= 0) return 0f
        return (valor.toFloat() / maximo).coerceIn(0f, 1f)
    }
}
