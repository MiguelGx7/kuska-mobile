package com.proyecto.kuska.ui.detalledonacion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Alimento
import com.proyecto.kuska.data.api.modelos.SolicitudRecibida
import com.proyecto.kuska.databinding.FragmentDetalleDonacionBinding
import com.proyecto.kuska.ui.comun.mostrar
import com.proyecto.kuska.ui.misdonaciones.MisDonacionesFragment
import kotlinx.coroutines.launch

/**
 * Detalle de una donación del donante (RN-12, RN-15, RN-16). Si está Pendiente se pueden editar sus datos,
 * agregar y quitar alimentos, o eliminarla. Si no, en sus solicitudes recibidas se registra la entrega de las Aprobadas (RN-26). En una Validada o Agotada se reportan novedades (RN-30) y se ven sus respuestas.
 */
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
            alEditar = { abrir(R.id.action_detalle_a_editar) },
            alAgregarAlimento = { abrir(R.id.action_detalle_a_agregar_alimento) },
            alQuitarAlimento = ::confirmarQuitar,
            alRegistrarEntrega = ::abrirEntrega,
            alReportarNovedad = ::abrirNovedad,
            alReintentarNovedades = viewModel::reintentarNovedades,
        )
        binding.listaDetalle.adapter = adapter
        binding.deslizarRefrescar.setOnRefreshListener { viewModel.refrescar() }
        binding.estadoPantalla.botonReintentar.setOnClickListener { viewModel.reintentar() }
        binding.barra.setNavigationOnClickListener { findNavController().navigateUp() }
        observarResultados()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.estado.collect(::mostrar) }
                launch { viewModel.eventos.collect(::atenderEvento) }
            }
        }
    }

    /** Editar datos o agregar alimento (las dos reciben el id de la donación). */
    private fun abrir(accion: Int) {
        if (viewModel.estado.value.ocupado) return
        viewModel.limpiarAviso()
        findNavController().navigate(accion, Bundle().apply { putInt(ARG_ID, viewModel.estado.value.donacion?.id ?: return) })
    }

    private fun confirmarQuitar(alimento: Alimento) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.detalle_quitar_titulo)
            .setMessage(getString(R.string.detalle_quitar_mensaje, alimento.nombre))
            .setPositiveButton(R.string.detalle_quitar_confirmar) { _, _ -> viewModel.quitarAlimento(alimento.id) }
            .setNegativeButton(R.string.accion_cancelar, null)
            .show()
    }

    /** Diálogo de entrega total (RN-26). Todas las tarjetas de la misma solicitud abren el mismo. */
    private fun abrirEntrega(solicitud: SolicitudRecibida) {
        if (viewModel.estado.value.ocupado || childFragmentManager.findFragmentByTag(RegistrarEntregaDialogFragment.TAG) != null) return
        viewModel.limpiarErroresEntrega()
        RegistrarEntregaDialogFragment.nuevo(solicitud.solicitud, solicitud.codigoPedido, solicitud.beneficiario.nombre)
            .show(childFragmentManager, RegistrarEntregaDialogFragment.TAG)
    }

    private fun cerrarDialogoEntrega() {
        (childFragmentManager.findFragmentByTag(RegistrarEntregaDialogFragment.TAG) as? DialogFragment)?.dismiss()
    }

    /** Diálogo de novedad (RN-30): [alimento] null = toda la donación. */
    private fun abrirNovedad(alimento: Alimento?) {
        if (viewModel.estado.value.ocupado || childFragmentManager.findFragmentByTag(ReportarNovedadDialogFragment.TAG) != null) return
        viewModel.limpiarErroresNovedad()
        val contexto = alimento?.nombre ?: getString(R.string.novedad_toda_donacion)
        ReportarNovedadDialogFragment.nuevo(alimento?.id, contexto)
            .show(childFragmentManager, ReportarNovedadDialogFragment.TAG)
    }

    private fun cerrarDialogoNovedad() {
        (childFragmentManager.findFragmentByTag(ReportarNovedadDialogFragment.TAG) as? DialogFragment)?.dismiss()
    }

    /** Editar y agregar dejan en el SavedStateHandle de esta pantalla un mensaje de éxito o un aviso. */
    private fun observarResultados() {
        val estadoGuardado = findNavController().currentBackStackEntry?.savedStateHandle ?: return
        listOf(RESULTADO_EXITO, RESULTADO_AVISO).forEach { clave ->
            estadoGuardado.getLiveData<String>(clave).observe(viewLifecycleOwner) { mensaje ->
                if (mensaje == null) return@observe
                estadoGuardado.remove<String>(clave)
                if (clave == RESULTADO_EXITO) {
                    viewModel.recibirResultado(exito = mensaje, aviso = null)
                } else {
                    viewModel.recibirResultado(exito = null, aviso = mensaje)
                }
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
            EventoDetalle.EntregaRegistrada -> {
                cerrarDialogoEntrega()
                Snackbar.make(binding.root, R.string.entrega_registrada, Snackbar.LENGTH_LONG).show()
            }
            EventoDetalle.CerrarDialogoEntrega -> cerrarDialogoEntrega()
            EventoDetalle.NovedadEnviada -> {
                cerrarDialogoNovedad()
                Snackbar.make(binding.root, R.string.novedad_enviada, Snackbar.LENGTH_LONG).show()
            }
            EventoDetalle.CerrarDialogoNovedad -> cerrarDialogoNovedad()
        }
    }

    private fun mostrar(estado: DetalleDonacionEstado) = with(binding) {
        adapter.submitList(
            construirFilas(
                estado,
                tituloAlimentos = R.string.detalle_alimentos,
                tituloNovedades = R.string.detalle_novedades,
                tituloSolicitudes = R.string.detalle_solicitudes,
                sinSolicitudes = getString(R.string.detalle_sin_solicitudes),
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
        /** Argumento de navegación: id de la donación (también lo usan editar y agregar alimento). */
        const val ARG_ID = "id"

        /** Claves en el SavedStateHandle de esta pantalla: Snackbar de éxito, o aviso rojo si ya no se podía editar. */
        const val RESULTADO_EXITO = "resultado_exito"
        const val RESULTADO_AVISO = "resultado_aviso"
    }
}
