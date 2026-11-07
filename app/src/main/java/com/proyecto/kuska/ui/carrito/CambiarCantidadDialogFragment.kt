package com.proyecto.kuska.ui.carrito

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.proyecto.kuska.R
import com.proyecto.kuska.databinding.DialogCambiarCantidadBinding
import com.proyecto.kuska.ui.comun.alPulsarListo
import com.proyecto.kuska.ui.comun.mostrar
import com.proyecto.kuska.ui.comun.mostrarAviso
import com.proyecto.kuska.ui.detallealimento.configurar
import com.proyecto.kuska.ui.detallealimento.mostrarCantidad
import kotlinx.coroutines.launch

/**
 * "Cambiar cantidad" de un ítem del carrito (HU-045), con el selector y los límites del detalle del alimento.
 * Comparte el ViewModel del carrito (sobrevive a girar la pantalla): se abre y se cierra según CarritoEstado.cambio.
 */
class CambiarCantidadDialogFragment : DialogFragment() {

    private val viewModel: CarritoViewModel by viewModels(
        ownerProducer = { requireParentFragment() },
        factoryProducer = { CarritoViewModel.Factory },
    )

    private var binding: DialogCambiarCantidadBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estado.collect(::mostrar)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val vista = DialogCambiarCantidadBinding.inflate(layoutInflater)
        binding = vista
        val cambio = viewModel.estado.value.cambio
        if (cambio != null) {
            vista.textoAlimento.text = cambio.alimento.nombre
            vista.selector.configurar(cambio.alimento.unidadMedida)
            vista.selector.entradaCantidad.setText(cambio.cantidad)
        }
        vista.selector.entradaCantidad.doAfterTextChanged { viewModel.cambiarCantidad(it?.toString().orEmpty()) }
        vista.selector.entradaCantidad.alPulsarListo { viewModel.guardarCambio() }
        vista.selector.botonRestar.setOnClickListener { viewModel.restar() }
        vista.selector.botonSumar.setOnClickListener { viewModel.sumar() }
        vista.botonGuardar.boton.setOnClickListener { viewModel.guardarCambio() }
        vista.botonCancelar.setOnClickListener { viewModel.cerrarCambio() }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.carrito_cambiar_cantidad)
            .setView(vista.root)
            .create()
    }

    /** Atrás o tocar fuera (solo si no se está guardando: ver isCancelable). */
    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        viewModel.cerrarCambio()
    }

    private fun mostrar(estado: CarritoEstado) {
        val cambio = estado.cambio
        if (cambio == null) {
            // Se guardó, se canceló o el ítem ya no está (también si Android cerró la app).
            dismissAllowingStateLoss()
            return
        }
        val vista = binding ?: return
        isCancelable = !cambio.guardando
        vista.selector.mostrarCantidad(
            cambio.cantidad, cambio.maximo, cambio.alimento.unidadMedida, cambio.error, ocupado = cambio.guardando,
        )
        vista.avisoCambio.mostrarAviso(cambio.aviso)
        vista.botonCancelar.isEnabled = !cambio.guardando
        vista.botonGuardar.mostrar(getString(R.string.carrito_guardar), cargando = cambio.guardando)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    companion object {
        const val TAG = "cambiar_cantidad"
    }
}
