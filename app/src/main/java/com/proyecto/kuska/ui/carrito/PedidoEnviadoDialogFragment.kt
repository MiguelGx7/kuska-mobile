package com.proyecto.kuska.ui.carrito

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.proyecto.kuska.R
import kotlinx.coroutines.launch

/**
 * Éxito del envío (HU-050): "Pedido KUM-… enviado. El administrador lo revisará." Se muestra mientras
 * CarritoEstado.pedidoEnviado tenga el código, así que vuelve a aparecer si se gira la pantalla.
 */
class PedidoEnviadoDialogFragment : DialogFragment() {

    private val viewModel: CarritoViewModel by viewModels(
        ownerProducer = { requireParentFragment() },
        factoryProducer = { CarritoViewModel.Factory },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estado.collect { if (it.pedidoEnviado == null) dismissAllowingStateLoss() }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val codigo = viewModel.estado.value.pedidoEnviado.orEmpty()
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.carrito_enviado_titulo)
            .setMessage(getString(R.string.carrito_enviado_mensaje, codigo))
            .setPositiveButton(R.string.carrito_ver_solicitudes) { _, _ ->
                val actividad = requireActivity()
                viewModel.cerrarExito()
                // Se quita ya, para que no quede guardado con la pestaña del carrito.
                dismissNow()
                actividad.irAPestana(R.id.misSolicitudesFragment)
            }
            .setNegativeButton(R.string.accion_aceptar) { _, _ -> viewModel.cerrarExito() }
            .create()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        viewModel.cerrarExito()
    }

    companion object {
        const val TAG = "pedido_enviado"
    }
}
