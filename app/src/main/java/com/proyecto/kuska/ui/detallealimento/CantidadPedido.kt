package com.proyecto.kuska.ui.detallealimento

import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.AlimentoDisponible
import com.proyecto.kuska.data.api.modelos.CarritoAgregarRequest
import com.proyecto.kuska.data.api.modelos.CarritoCantidadRequest
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.ui.comun.ErroresAccion
import com.proyecto.kuska.ui.comun.FormatoCantidad
import com.proyecto.kuska.ui.comun.TextoUi
import com.proyecto.kuska.ui.creardonacion.ValidacionAlimento
import com.proyecto.kuska.util.Resultado
import java.math.BigDecimal
import java.math.RoundingMode

/** Dónde se muestra un error al agregar al carrito. */
sealed interface FalloCarrito {
    /** Error de la cantidad: en su campo. */
    data class EnCampo(val mensaje: String) : FalloCarrito

    /**
     * Aviso rojo. [recargarLista]: una regla del servidor (p. ej. ya tienes una solicitud activa en esa donación o el
     * alimento ya no está disponible) o un 404; la lista se recarga al volver.
     */
    data class Aviso(val mensaje: String, val recargarLista: Boolean) : FalloCarrito
}

/**
 * Cuánto se puede pedir de un alimento (HU-043, RN-20): como máximo el menor entre max_por_solicitud y lo
 * disponible; entero si la unidad es unidad o paquete, hasta 2 decimales en las demás.
 */
object CantidadPedido {

    private const val CAMPO_CANTIDAD = "cantidad"

    fun admiteDecimales(unidad: String): Boolean = unidad !in Donacion.UNIDADES_ENTERAS

    /** Null si no hay nada que pedir (o las cantidades no se pueden leer). */
    fun maximo(alimento: AlimentoDisponible): BigDecimal? {
        val disponible = leer(alimento.cantidadDisponible) ?: return null
        val tope = alimento.maxPorSolicitud?.let(::leer)?.let { minOf(it, disponible) } ?: disponible
        val ajustado = if (admiteDecimales(alimento.unidadMedida)) {
            tope.setScale(ValidacionAlimento.DECIMALES, RoundingMode.DOWN)
        } else {
            tope.setScale(0, RoundingMode.DOWN)
        }
        return ajustado.stripTrailingZeros().takeIf { it.signum() > 0 }
    }

    /** Empieza en 1, o en el máximo si es menor (p. ej. 0,5 kg). */
    fun inicial(maximo: BigDecimal): String = texto(minOf(BigDecimal.ONE, maximo))

    /** "+": de a 1 sin pasar del máximo. Si lo escrito no se puede leer, parte del inicial. */
    fun sumar(actual: String, maximo: BigDecimal): String {
        val valor = leer(actual) ?: return inicial(maximo)
        return texto(minOf(valor + BigDecimal.ONE, maximo))
    }

    /** "−": de a 1 sin bajar del mínimo (1, o el máximo si es menor). */
    fun restar(actual: String, maximo: BigDecimal): String {
        val valor = leer(actual) ?: return inicial(maximo)
        return texto(maxOf(valor - BigDecimal.ONE, minimo(maximo)))
    }

    fun puedeSumar(actual: String, maximo: BigDecimal): Boolean = (leer(actual) ?: BigDecimal.ZERO) < maximo

    fun puedeRestar(actual: String, maximo: BigDecimal): Boolean {
        val valor = leer(actual) ?: return false
        return valor > minimo(maximo)
    }

    /** Las reglas de siempre (mayor que 0, entera o hasta 2 decimales) y que no pase del máximo. */
    fun validar(texto: String, unidad: String, maximo: BigDecimal): TextoUi? {
        ValidacionAlimento.validarCantidad(texto, unidad)?.let { return it }
        val valor = leer(texto) ?: return TextoUi.Recurso(R.string.error_cantidad_invalida)
        return if (valor > maximo) TextoUi.Recurso(R.string.carrito_error_maximo, listOf(texto(maximo))) else null
    }

    /** Supone que [texto] es válido. La cantidad va con punto y sin ceros sobrantes ("2,50" → "2.5"). */
    fun cuerpo(alimentoId: Int, texto: String) =
        CarritoAgregarRequest(alimentoId, ValidacionAlimento.normalizarCantidad(texto))

    /** PATCH carrito/{id}/ (HU-045). Supone que [texto] es válido, como [cuerpo]. */
    fun cuerpoCambio(texto: String) = CarritoCantidadRequest(ValidacionAlimento.normalizarCantidad(texto))

    fun clasificar(error: Resultado.Error): FalloCarrito {
        val separados = ErroresAccion.separar(error.erroresCampo, mapOf(CAMPO_CANTIDAD to CAMPO_CANTIDAD))
        separados.porCampo[CAMPO_CANTIDAD]?.let { return FalloCarrito.EnCampo(it) }
        val regla = error.codigo == 404 || error.codigo == 400
        return FalloCarrito.Aviso(ErroresAccion.aviso(error, separados) ?: error.mensaje, recargarLista = regla)
    }

    /** "2,5" o "2.5" → 2.5. Null si no es un número. */
    fun leer(texto: String): BigDecimal? = texto.trim().replace(',', '.').toBigDecimalOrNull()

    /** Con coma y sin ceros sobrantes, como lo escribe el usuario ("2,5", "3"). */
    fun texto(valor: BigDecimal): String = FormatoCantidad.numero(valor.toPlainString())

    private fun minimo(maximo: BigDecimal) = minOf(BigDecimal.ONE, maximo)
}
