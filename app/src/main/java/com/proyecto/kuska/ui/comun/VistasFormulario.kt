package com.proyecto.kuska.ui.comun

import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.isVisible
import com.proyecto.kuska.R
import com.proyecto.kuska.databinding.VistaBotonCargaBinding
import com.proyecto.kuska.databinding.VistaCodigoVerificacionBinding

/** Botón principal: mientras carga se deshabilita, oculta su texto y muestra el indicador. */
fun VistaBotonCargaBinding.mostrar(texto: String, cargando: Boolean, habilitado: Boolean = true) {
    boton.isEnabled = habilitado && !cargando
    boton.text = if (cargando) null else texto
    boton.contentDescription = if (cargando) root.context.getString(R.string.cargando) else null
    indicador.isVisible = cargando
}

/** "Reenviar código": deshabilitado con la cuenta regresiva mientras quedan segundos. */
fun VistaCodigoVerificacionBinding.mostrarReenvio(segundos: Int, cargando: Boolean) {
    botonReenviar.isEnabled = segundos == 0 && !cargando
    botonReenviar.text = if (segundos > 0) {
        root.context.getString(R.string.codigo_reenviar_en, segundos)
    } else {
        root.context.getString(R.string.codigo_reenviar)
    }
}

/** Aviso (rojo) o texto de error: visible solo si hay mensaje. */
fun TextView.mostrarAviso(mensaje: String?) {
    text = mensaje
    isVisible = mensaje != null
}

/** Ejecuta [accion] cuando se pulsa "Listo" en el teclado. */
fun EditText.alPulsarListo(accion: () -> Unit) {
    setOnEditorActionListener { _, idAccion, _ ->
        if (idAccion == EditorInfo.IME_ACTION_DONE) {
            accion()
            true
        } else {
            false
        }
    }
}
