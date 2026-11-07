package com.proyecto.kuska.ui.detallesolicitud

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.proyecto.kuska.R

/**
 * Confirmación de "Sí, lo recibí" (HU-057, RN-26b). Se cierra al aceptar: la carga va en el botón de la tarjeta y
 * los errores en el aviso rojo. Usa el ViewModel del detalle, así sobrevive a girar la pantalla.
 */
class ConfirmarRecibidoDialogFragment : DialogFragment() {

    private val viewModel: DetalleSolicitudViewModel by viewModels(
        ownerProducer = { requireParentFragment() },
        factoryProducer = { DetalleSolicitudViewModel.Factory },
    )

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val solicitud = viewModel.estado.value.solicitud
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.recibido_titulo)
            .setMessage(
                getString(
                    R.string.recibido_mensaje,
                    solicitud?.codigoPedido.orEmpty(),
                    solicitud?.donacionTitulo.orEmpty(),
                )
            )
            .setPositiveButton(R.string.recibido_confirmar) { _, _ -> viewModel.confirmarRecibido() }
            .setNegativeButton(R.string.accion_volver, null)
            .create()
    }

    companion object {
        const val TAG = "confirmar_recibido"
    }
}
