package com.proyecto.kuska.ui.agregaralimento

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.proyecto.kuska.R
import com.proyecto.kuska.ui.comun.formularioalimento.EditorAlimento
import com.proyecto.kuska.ui.comun.formularioalimento.FormularioAlimentoFragment
import com.proyecto.kuska.ui.detalledonacion.DetalleDonacionFragment
import kotlinx.coroutines.launch

/** Agregar un alimento a una donación Pendiente. Usa el mismo formulario que crear donación. */
class AgregarAlimentoFragment : FormularioAlimentoFragment() {

    private val viewModel: AgregarAlimentoViewModel by viewModels { AgregarAlimentoViewModel.Factory }

    override val editor: EditorAlimento get() = viewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.yaNoEditable.collect { mensaje ->
                    volverAlDetalle(DetalleDonacionFragment.RESULTADO_AVISO, mensaje)
                }
            }
        }
    }

    /** El detalle muestra "Alimento agregado." y se recarga. */
    override fun alGuardar() =
        volverAlDetalle(DetalleDonacionFragment.RESULTADO_EXITO, getString(R.string.detalle_alimento_agregado))

    /** Con datos sin guardar pregunta antes de descartar. */
    override fun alSalir() {
        when {
            viewModel.estadoGuardado.value.guardando -> Unit
            viewModel.tieneDatos -> MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.descartar_cambios_titulo)
                .setMessage(R.string.descartar_cambios_mensaje)
                .setPositiveButton(R.string.crear_donacion_descartar) { _, _ -> cancelar() }
                .setNegativeButton(R.string.crear_donacion_seguir_editando, null)
                .show()
            else -> cancelar()
        }
    }

    private fun volverAlDetalle(clave: String, mensaje: String) {
        val navegacion = findNavController()
        navegacion.previousBackStackEntry?.savedStateHandle?.set(clave, mensaje)
        navegacion.popBackStack()
    }
}
