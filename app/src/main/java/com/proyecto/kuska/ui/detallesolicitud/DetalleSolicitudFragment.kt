package com.proyecto.kuska.ui.detallesolicitud

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import com.google.android.material.snackbar.Snackbar
import com.proyecto.kuska.R
import com.proyecto.kuska.databinding.FragmentDetalleSolicitudBinding
import com.proyecto.kuska.ui.comun.mostrar
import kotlinx.coroutines.launch

/**
 * Detalle de una solicitud del beneficiario (HU-053 a HU-056): estado, donación, modalidad, fechas, datos del
 * donante según el estado (RN-29), alimentos (los cancelados tachados), entrega si la hay y, en una Pendiente,
 * "Cancelar solicitud" (RN-23). Al volver, Mis solicitudes se recarga sola.
 */
class DetalleSolicitudFragment : Fragment() {

    private var _binding: FragmentDetalleSolicitudBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetalleSolicitudViewModel by viewModels { DetalleSolicitudViewModel.Factory }

    private lateinit var adapter: DetalleSolicitudAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDetalleSolicitudBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DetalleSolicitudAdapter(
            alLlamar = ::llamar,
            alCancelar = ::confirmarCancelar,
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

    /** La confirmación es un DialogFragment: sigue abierta si se gira la pantalla. */
    private fun confirmarCancelar() {
        val estado = viewModel.estado.value
        if (estado.ocupado || estado.solicitud == null) return
        if (childFragmentManager.findFragmentByTag(CancelarSolicitudDialogFragment.TAG) != null) return
        CancelarSolicitudDialogFragment().show(childFragmentManager, CancelarSolicitudDialogFragment.TAG)
    }

    /** Abre el marcador con el número (ACTION_DIAL no pide permisos ni llama solo). */
    private fun llamar(numero: String) {
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$numero")))
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.solicitud_sin_marcador, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun atenderEvento(evento: EventoSolicitud) {
        when (evento) {
            is EventoSolicitud.Mensaje -> Snackbar.make(binding.root, evento.texto, Snackbar.LENGTH_LONG).show()
            EventoSolicitud.Cancelada ->
                Snackbar.make(binding.root, R.string.solicitud_cancelada, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun mostrar(estado: DetalleSolicitudEstado) = with(binding) {
        adapter.submitList(
            construirFilasSolicitud(
                estado,
                tituloEntrega = R.string.solicitud_entrega_titulo,
                tituloAlimentos = R.string.solicitud_alimentos,
            )
        )
        deslizarRefrescar.isRefreshing = estado.refrescando
        deslizarRefrescar.isEnabled = !estado.ocupado
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
        /** Argumento de navegación: id de la solicitud. */
        const val ARG_ID = "id"
    }
}
