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
import com.proyecto.kuska.databinding.DialogReportarNovedadBinding
import com.proyecto.kuska.ui.comun.alPulsarListo
import com.proyecto.kuska.ui.comun.mostrar
import com.proyecto.kuska.ui.comun.mostrarAviso
import com.proyecto.kuska.ui.comun.resolver
import kotlinx.coroutines.launch

/**
 * "Reportar novedad" sobre un alimento o sobre toda la donación (RN-30). Comparte el ViewModel del detalle, así que
 * sobrevive a girar la pantalla. No se cierra solo: el detalle lo cierra al terminar o cuando el error va al aviso rojo.
 */
class ReportarNovedadDialogFragment : DialogFragment() {

    private val viewModel: DetalleDonacionViewModel by viewModels(
        ownerProducer = { requireParentFragment() },
        factoryProducer = { DetalleDonacionViewModel.Factory },
    )

    private var binding: DialogReportarNovedadBinding? = null

    /** Null = toda la donación. */
    private val alimentoId: Int?
        get() = requireArguments().takeIf { it.containsKey(ARG_ALIMENTO) }?.getInt(ARG_ALIMENTO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estado.collect(::mostrar)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val vista = DialogReportarNovedadBinding.inflate(layoutInflater)
        binding = vista
        vista.textoContexto.text = requireArguments().getString(ARG_CONTEXTO).orEmpty()
        vista.entradaDescripcion.doAfterTextChanged {
            val estado = viewModel.estado.value
            if (estado.errorDescripcion != null || estado.errorNovedad != null) viewModel.limpiarErroresNovedad()
        }
        vista.entradaDescripcion.alPulsarListo(::enviar)
        vista.botonEnviar.boton.setOnClickListener { enviar() }
        vista.botonCancelar.setOnClickListener { dismiss() }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.novedad_titulo)
            .setView(vista.root)
            .create()
    }

    private fun enviar() {
        val texto = binding?.entradaDescripcion?.text?.toString().orEmpty()
        viewModel.reportarNovedad(alimentoId, texto)
    }

    private fun mostrar(estado: DetalleDonacionEstado) {
        val vista = binding ?: return
        val enviando = estado.enviandoNovedad == ObjetivoNovedad(alimentoId)
        // Mientras se envía no se puede cerrar (ni con atrás ni tocando fuera).
        isCancelable = !enviando
        vista.botonEnviar.mostrar(getString(R.string.novedad_enviar), cargando = enviando, habilitado = !estado.ocupado)
        vista.botonCancelar.isEnabled = !enviando
        vista.entradaDescripcion.isEnabled = !enviando
        vista.campoDescripcion.error = estado.errorDescripcion?.resolver(requireContext())
        vista.avisoNovedad.mostrarAviso(estado.errorNovedad)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    companion object {
        const val TAG = "reportar_novedad"
        private const val ARG_ALIMENTO = "alimento"
        private const val ARG_CONTEXTO = "contexto"

        /** [alimentoId] null = toda la donación. [contexto]: nombre del alimento o "Toda la donación". */
        fun nuevo(alimentoId: Int?, contexto: String) = ReportarNovedadDialogFragment().apply {
            arguments = bundleOf(ARG_CONTEXTO to contexto).apply {
                if (alimentoId != null) putInt(ARG_ALIMENTO, alimentoId)
            }
        }
    }
}
