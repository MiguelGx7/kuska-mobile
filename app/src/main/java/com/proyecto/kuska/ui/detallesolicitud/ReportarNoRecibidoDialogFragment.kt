package com.proyecto.kuska.ui.detallesolicitud

import android.app.Dialog
import android.os.Bundle
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.proyecto.kuska.R
import com.proyecto.kuska.databinding.DialogNoRecibidoBinding
import com.proyecto.kuska.ui.comun.alPulsarListo
import com.proyecto.kuska.ui.comun.mostrar
import com.proyecto.kuska.ui.comun.mostrarAviso
import com.proyecto.kuska.ui.comun.resolver
import kotlinx.coroutines.launch

/**
 * "No me llegó" (HU-058, RN-26b): comentario obligatorio. Comparte el ViewModel del detalle, así que sobrevive a
 * girar la pantalla sin perder lo escrito. No se cierra solo: el detalle lo cierra al terminar o cuando el error
 * va al aviso rojo.
 */
class ReportarNoRecibidoDialogFragment : DialogFragment() {

    private val viewModel: DetalleSolicitudViewModel by viewModels(
        ownerProducer = { requireParentFragment() },
        factoryProducer = { DetalleSolicitudViewModel.Factory },
    )

    private var binding: DialogNoRecibidoBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estado.collect(::mostrar)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val vista = DialogNoRecibidoBinding.inflate(layoutInflater)
        binding = vista
        val solicitud = viewModel.estado.value.solicitud
        vista.textoPedido.text = getString(
            R.string.entrega_pedido_beneficiario,
            solicitud?.codigoPedido.orEmpty(),
            solicitud?.donacionTitulo.orEmpty(),
        )
        vista.entradaComentario.doAfterTextChanged {
            val estado = viewModel.estado.value
            if (estado.errorComentario != null || estado.errorReclamo != null) viewModel.limpiarErroresReclamo()
        }
        vista.entradaComentario.alPulsarListo(::enviar)
        vista.botonEnviar.boton.setOnClickListener { enviar() }
        vista.botonCancelar.setOnClickListener { dismiss() }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.no_recibido_titulo)
            .setView(vista.root)
            .create()
    }

    private fun enviar() {
        viewModel.reclamar(binding?.entradaComentario?.text?.toString().orEmpty())
    }

    private fun mostrar(estado: DetalleSolicitudEstado) {
        val vista = binding ?: return
        val enviando = estado.confirmando == RespuestaEntrega.RECLAMO
        // Mientras se envía no se puede cerrar (ni con atrás ni tocando fuera).
        isCancelable = !enviando
        vista.botonEnviar.mostrar(getString(R.string.no_recibido_enviar), cargando = enviando, habilitado = !estado.ocupado)
        vista.botonCancelar.isEnabled = !enviando
        vista.entradaComentario.isEnabled = !enviando
        vista.campoComentario.error = estado.errorComentario?.resolver(requireContext())
        vista.avisoReclamo.mostrarAviso(estado.errorReclamo)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    companion object {
        const val TAG = "reportar_no_recibido"
    }
}
