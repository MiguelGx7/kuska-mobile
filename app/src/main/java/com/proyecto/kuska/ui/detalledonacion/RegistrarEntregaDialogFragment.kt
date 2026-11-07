package com.proyecto.kuska.ui.detalledonacion

import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.proyecto.kuska.R
import com.proyecto.kuska.databinding.DialogRegistrarEntregaBinding
import com.proyecto.kuska.ui.comun.alPulsarListo
import com.proyecto.kuska.ui.comun.mostrar
import com.proyecto.kuska.ui.comun.mostrarAviso
import kotlinx.coroutines.launch

/**
 * "Registrar entrega" de una solicitud (RN-26). Comparte el ViewModel del detalle, así que sobrevive a girar la
 * pantalla. No se cierra solo: el detalle lo cierra al terminar o cuando el error va al aviso rojo.
 */
class RegistrarEntregaDialogFragment : DialogFragment() {

    private val viewModel: DetalleDonacionViewModel by viewModels(
        ownerProducer = { requireParentFragment() },
        factoryProducer = { DetalleDonacionViewModel.Factory },
    )

    private var binding: DialogRegistrarEntregaBinding? = null

    private val solicitudId get() = requireArguments().getInt(ARG_SOLICITUD)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estado.collect(::mostrar)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val vista = DialogRegistrarEntregaBinding.inflate(layoutInflater)
        binding = vista
        val argumentos = requireArguments()
        vista.textoPedido.text = getString(
            R.string.entrega_pedido_beneficiario,
            argumentos.getString(ARG_CODIGO).orEmpty(),
            argumentos.getString(ARG_BENEFICIARIO).orEmpty(),
        )
        vista.entradaObservacion.doAfterTextChanged {
            if (viewModel.estado.value.errorObservacion != null || viewModel.estado.value.errorEntrega != null) {
                viewModel.limpiarErroresEntrega()
            }
        }
        vista.entradaObservacion.alPulsarListo(::registrar)
        vista.botonRegistrar.boton.setOnClickListener { registrar() }
        vista.botonCancelar.setOnClickListener { dismiss() }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.entrega_titulo)
            .setView(vista.root)
            .create()
    }

    private fun registrar() {
        val texto = binding?.entradaObservacion?.text?.toString().orEmpty()
        viewModel.registrarEntrega(solicitudId, texto)
    }

    private fun mostrar(estado: DetalleDonacionEstado) {
        val vista = binding ?: return
        val enviando = estado.entregandoId == solicitudId
        // Mientras se envía no se puede cerrar (ni con atrás ni tocando fuera).
        isCancelable = !enviando
        vista.botonRegistrar.mostrar(getString(R.string.entrega_registrar), cargando = enviando, habilitado = !estado.ocupado)
        vista.botonCancelar.isEnabled = !enviando
        vista.entradaObservacion.isEnabled = !enviando
        vista.campoObservacion.error = estado.errorObservacion
        vista.avisoEntrega.mostrarAviso(estado.errorEntrega)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    companion object {
        const val TAG = "registrar_entrega"
        private const val ARG_SOLICITUD = "solicitud"
        private const val ARG_CODIGO = "codigo"
        private const val ARG_BENEFICIARIO = "beneficiario"

        fun nuevo(solicitudId: Int, codigoPedido: String, beneficiario: String) = RegistrarEntregaDialogFragment().apply {
            arguments = bundleOf(
                ARG_SOLICITUD to solicitudId,
                ARG_CODIGO to codigoPedido,
                ARG_BENEFICIARIO to beneficiario,
            )
        }
    }
}
