package com.proyecto.kuska.ui.detalledonacion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.proyecto.kuska.R
import com.proyecto.kuska.databinding.FragmentDetalleDonacionBinding
import com.proyecto.kuska.ui.comun.mostrar
import com.proyecto.kuska.ui.misdonaciones.MisDonacionesFragment
import kotlinx.coroutines.launch

/** Detalle de una donación del donante (RN-12, RN-15, RN-16). Editar, entregas y novedades llegan después. */
class DetalleDonacionFragment : Fragment() {

    private var _binding: FragmentDetalleDonacionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetalleDonacionViewModel by viewModels { DetalleDonacionViewModel.Factory }

    private lateinit var adapter: DetalleDonacionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDetalleDonacionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DetalleDonacionAdapter(
            alReintentarSolicitudes = viewModel::reintentarSolicitudes,
            alEliminar = ::confirmarEliminar,
        )
        binding.listaDetalle.adapter = adapter
        binding.deslizarRefrescar.setOnRefreshListener { viewModel.refrescar() }
        binding.estadoPantalla.botonReintentar.setOnClickListener { viewModel.reintentar() }
        binding.barra.setNavigationOnClickListener { findNavController().navigateUp() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.estado.collect(::mostrar) }
                launch { viewModel.eventos.collect(::atenderEvento) }
            }
        }
    }

    private fun confirmarEliminar() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.detalle_eliminar_titulo)
            .setMessage(R.string.detalle_eliminar_mensaje)
            .setPositiveButton(R.string.detalle_eliminar_confirmar) { _, _ -> viewModel.eliminar() }
            .setNegativeButton(R.string.accion_cancelar, null)
            .show()
    }

    private fun atenderEvento(evento: EventoDetalle) {
        when (evento) {
            // Mis donaciones lee la marca de su SavedStateHandle, muestra el aviso y recarga la lista.
            EventoDetalle.Eliminada -> {
                val navegacion = findNavController()
                navegacion.previousBackStackEntry?.savedStateHandle
                    ?.set(MisDonacionesFragment.RESULTADO_DONACION_ELIMINADA, true)
                navegacion.popBackStack()
            }
            is EventoDetalle.Mensaje -> Snackbar.make(binding.root, evento.texto, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun mostrar(estado: DetalleDonacionEstado) = with(binding) {
        adapter.submitList(
            construirFilas(
                estado,
                tituloAlimentos = R.string.detalle_alimentos,
                tituloSolicitudes = R.string.detalle_solicitudes,
                sinSolicitudes = getString(R.string.detalle_sin_solicitudes),
            )
        )
        deslizarRefrescar.isRefreshing = estado.refrescando
        deslizarRefrescar.isEnabled = !estado.eliminando
        estadoPantalla.mostrar(
            cargando = estado.cargando && !estado.refrescando,
            mensaje = estado.error,
            conReintentar = estado.error != null,
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Argumento de navegación: id de la donación. */
        const val ARG_ID = "id"
    }
}
