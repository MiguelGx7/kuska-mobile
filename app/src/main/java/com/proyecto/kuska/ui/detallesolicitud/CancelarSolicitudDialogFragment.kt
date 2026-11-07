package com.proyecto.kuska.ui.detallesolicitud

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.proyecto.kuska.R

/** Confirmación de "Cancelar solicitud" (RN-23). Usa el ViewModel del detalle, así sobrevive a girar la pantalla. */
class CancelarSolicitudDialogFragment : DialogFragment() {

    private val viewModel: DetalleSolicitudViewModel by viewModels(
        ownerProducer = { requireParentFragment() },
        factoryProducer = { DetalleSolicitudViewModel.Factory },
    )

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val solicitud = viewModel.estado.value.solicitud
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.solicitud_cancelar_titulo)
            .setMessage(
                getString(
                    R.string.solicitud_cancelar_mensaje,
                    solicitud?.codigoPedido.orEmpty(),
                    solicitud?.donacionTitulo.orEmpty(),
                )
            )
            .setPositiveButton(R.string.solicitud_cancelar_confirmar) { _, _ -> viewModel.cancelar() }
            .setNegativeButton(R.string.accion_volver, null)
            .create()
    }

    companion object {
        const val TAG = "cancelar_solicitud"
    }
}
