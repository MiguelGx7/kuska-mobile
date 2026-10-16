package com.proyecto.kuska.ui.creardonacion

import android.content.Context
import androidx.annotation.StringRes
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.ui.comun.textoCantidad
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Textos de un alimento para mostrar en pantalla. */
object FormatoAlimento {

    @StringRes
    fun etiquetaUnidad(unidad: String): Int = when (unidad) {
        Donacion.UNIDAD_KG -> R.string.unidad_kg
        Donacion.UNIDAD_G -> R.string.unidad_g
        Donacion.UNIDAD_L -> R.string.unidad_l
        Donacion.UNIDAD_ML -> R.string.unidad_ml
        Donacion.UNIDAD_UNIDAD -> R.string.unidad_unidad
        else -> R.string.unidad_paquete
    }

    /** "2,5 kg", "12 unidades", "1 paquete". */
    fun cantidad(context: Context, cantidad: String, unidad: String): String = context.textoCantidad(cantidad, unidad)

    /** dd/MM/yyyy de una fecha del MaterialDatePicker (medianoche UTC). */
    fun fecha(fechaUtc: Long): String =
        SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(fechaUtc)
}
