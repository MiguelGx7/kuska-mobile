package com.proyecto.kuska.ui.comun

import android.content.Context
import androidx.annotation.PluralsRes
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Donacion
import java.math.BigDecimal

/** Cantidad con su unidad, lista para pasar a texto: "2,5 kg" o un plural ("12 unidades"). */
sealed interface CantidadConUnidad {
    data class ConSimbolo(val numero: String, val simbolo: String) : CantidadConUnidad
    data class EnPlural(val numero: String, val cuantos: Int, @param:PluralsRes val plural: Int) : CantidadConUnidad
}

/** Formato de las cantidades de la API ("5.50", "12.00") para mostrarlas en pantalla. */
object FormatoCantidad {

    /** "5.50" → "5,5"; "12.00" → "12". Si no es un número, se devuelve tal cual. */
    fun numero(cantidad: String): String = try {
        BigDecimal(cantidad.trim().replace(',', '.')).stripTrailingZeros().toPlainString().replace('.', ',')
    } catch (_: NumberFormatException) {
        cantidad
    }

    /** unidad y paquete van en plural ("1 paquete", "3 paquetes"); las demás con su símbolo ("2,5 kg"). */
    fun conUnidad(cantidad: String, unidad: String): CantidadConUnidad {
        val texto = numero(cantidad)
        val plural = when (unidad) {
            Donacion.UNIDAD_UNIDAD -> R.plurals.cantidad_unidades
            Donacion.UNIDAD_PAQUETE -> R.plurals.cantidad_paquetes
            else -> return CantidadConUnidad.ConSimbolo(texto, unidad)
        }
        // Solo "1" va en singular; cualquier otro valor (incluso con decimales) en plural.
        val cuantos = texto.toIntOrNull() ?: 0
        return CantidadConUnidad.EnPlural(texto, cuantos, plural)
    }
}

fun CantidadConUnidad.resolver(context: Context): String = when (this) {
    is CantidadConUnidad.ConSimbolo -> context.getString(R.string.cantidad_con_simbolo, numero, simbolo)
    is CantidadConUnidad.EnPlural -> context.resources.getQuantityString(plural, cuantos, numero)
}

/** "2,5 kg", "12 unidades", "1 paquete". */
fun Context.textoCantidad(cantidad: String, unidad: String): String =
    FormatoCantidad.conUnidad(cantidad, unidad).resolver(this)
