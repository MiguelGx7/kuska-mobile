package com.proyecto.kuska.ui.creardonacion

import android.view.View
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputLayout
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.databinding.VistaDatosDonacionBinding
import com.proyecto.kuska.ui.comun.TextoUi
import com.proyecto.kuska.ui.comun.alPulsarListo
import com.proyecto.kuska.ui.comun.mostrarAviso
import com.proyecto.kuska.ui.comun.resolver

/** Lo que muestran los campos de vista_datos_donacion. */
data class ValoresDatosDonacion(
    val titulo: String,
    val descripcion: String,
    val direccion: String,
    val modalidad: String?,
)

/** Liga los campos de vista_datos_donacion (crear y editar donación) con su ViewModel. */
class VistaDatosDonacion(private val binding: VistaDatosDonacionBinding) {

    private val campos: Map<CampoDonacion, Pair<TextInputLayout, EditText>> = with(binding) {
        mapOf(
            CampoDonacion.TITULO to (campoTitulo to entradaTitulo),
            CampoDonacion.DESCRIPCION to (campoDescripcion to entradaDescripcion),
            CampoDonacion.DIRECCION to (campoDireccion to entradaDireccion),
        )
    }

    private val chipsModalidad = mapOf(
        R.id.chip_recoger to Donacion.MODALIDAD_RECOGER,
        R.id.chip_domicilio to Donacion.MODALIDAD_DOMICILIO,
        R.id.chip_ambas to Donacion.MODALIDAD_AMBAS,
    )

    /**
     * Llena los textos con [iniciales] (la vista se recrea al volver de otra pantalla) y después conecta los
     * listeners, para que llenarlos no cuente como un cambio del usuario.
     */
    fun configurar(
        iniciales: ValoresDatosDonacion,
        alCambiar: (CampoDonacion, String) -> Unit,
        alElegirModalidad: (String?) -> Unit,
        alPulsarListo: () -> Unit,
    ) {
        campos.forEach { (campo, par) ->
            val valor = valorDe(iniciales, campo)
            if (par.second.text.toString() != valor) par.second.setText(valor)
            par.second.doAfterTextChanged { alCambiar(campo, it?.toString().orEmpty()) }
        }
        binding.entradaDireccion.alPulsarListo(alPulsarListo)
        binding.grupoModalidad.setOnCheckedStateChangeListener { _, ids ->
            alElegirModalidad(ids.firstOrNull()?.let(chipsModalidad::get))
        }
    }

    /** Textos que cambia el ViewModel (p. ej. la dirección que llega del perfil), errores y si se puede editar. */
    fun mostrar(valores: ValoresDatosDonacion, errores: Map<CampoDonacion, TextoUi>, editable: Boolean) = with(binding) {
        val contexto = root.context
        campos.forEach { (campo, par) ->
            val valor = valorDe(valores, campo)
            if (par.second.text.toString() != valor) par.second.setText(valor)
            par.first.error = errores[campo]?.resolver(contexto)
        }

        val chipElegido = chipsModalidad.entries.firstOrNull { it.value == valores.modalidad }?.key
        if (chipElegido == null) grupoModalidad.clearCheck() else grupoModalidad.check(chipElegido)
        errorModalidad.mostrarAviso(errores[CampoDonacion.MODALIDAD]?.resolver(contexto))

        campos.values.forEach { it.second.isEnabled = editable }
        chipsModalidad.keys.forEach { grupoModalidad.findViewById<View>(it).isEnabled = editable }
    }

    private fun valorDe(valores: ValoresDatosDonacion, campo: CampoDonacion) = when (campo) {
        CampoDonacion.TITULO -> valores.titulo
        CampoDonacion.DESCRIPCION -> valores.descripcion
        else -> valores.direccion
    }
}
