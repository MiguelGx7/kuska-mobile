package com.proyecto.kuska.ui.detallealimento

import android.text.method.DigitsKeyListener
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.databinding.VistaSelectorCantidadBinding
import com.proyecto.kuska.ui.comun.TextoUi
import com.proyecto.kuska.ui.comun.resolver
import com.proyecto.kuska.ui.comun.textoCantidad
import java.math.BigDecimal

/*
 * Selector de cantidad (vista_selector_cantidad) del detalle del alimento y de "Cambiar cantidad" del carrito.
 * Los límites y los pasos de "+" y "−" son los de CantidadPedido.
 */

/** Lo que no cambia: el teclado y el sufijo de la unidad. Unidad y paquete: solo enteros (RN-20). */
fun VistaSelectorCantidadBinding.configurar(unidad: String) {
    // Las demás unidades aceptan coma o punto (android:digits del layout).
    if (!CantidadPedido.admiteDecimales(unidad)) {
        entradaCantidad.keyListener = DigitsKeyListener.getInstance("0123456789")
    }
    val recursos = root.resources
    campoCantidad.suffixText = when (unidad) {
        Donacion.UNIDAD_UNIDAD -> recursos.getQuantityString(R.plurals.cantidad_unidades, 2, "").trim()
        Donacion.UNIDAD_PAQUETE -> recursos.getQuantityString(R.plurals.cantidad_paquetes, 2, "").trim()
        else -> unidad
    }
}

/** [ocupado]: se está guardando; nada se puede tocar. */
fun VistaSelectorCantidadBinding.mostrarCantidad(
    cantidad: String,
    maximo: BigDecimal,
    unidad: String,
    error: TextoUi?,
    ocupado: Boolean,
) {
    val contexto = root.context
    if (entradaCantidad.text.toString() != cantidad) {
        entradaCantidad.setText(cantidad)
        entradaCantidad.setSelection(cantidad.length)
    }
    campoCantidad.error = error?.resolver(contexto)
    textoPuedesPedir.text = contexto.getString(
        R.string.carrito_puedes_pedir,
        contexto.textoCantidad(maximo.toPlainString(), unidad),
    )
    entradaCantidad.isEnabled = !ocupado
    botonRestar.isEnabled = !ocupado && CantidadPedido.puedeRestar(cantidad, maximo)
    botonSumar.isEnabled = !ocupado && CantidadPedido.puedeSumar(cantidad, maximo)
}
