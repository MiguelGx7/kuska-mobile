package com.proyecto.kuska.ui.notificaciones

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
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.proyecto.kuska.R
import com.proyecto.kuska.databinding.FragmentNotificacionesBinding
import com.proyecto.kuska.ui.comun.PieLista
import com.proyecto.kuska.ui.comun.PieListaAdapter
import com.proyecto.kuska.ui.comun.mostrar
import com.proyecto.kuska.ui.detalledonacion.DetalleDonacionFragment
import com.proyecto.kuska.ui.detallesolicitud.DetalleSolicitudFragment
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Notificaciones (HU-119), para el donante y el beneficiario: filtro Todas / No leídas, lista paginada, tocar para
 * marcar como leída e ir a lo relacionado, y "Marcar todas como leídas". Al volver, la campana se actualiza sola.
 */
class NotificacionesFragment : Fragment() {

    private var _binding: FragmentNotificacionesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NotificacionesViewModel by viewModels { NotificacionesViewModel.Factory }

    private lateinit var adapter: NotificacionesAdapter
    private lateinit var pieAdapter: PieListaAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentNotificacionesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.alVolver()

        binding.barra.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.barra.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.accion_marcar_todas) {
                viewModel.marcarTodas()
                true
            } else {
                false
            }
        }

        configurarFiltros()

        adapter = NotificacionesAdapter(viewModel::tocar)
        pieAdapter = PieListaAdapter { viewModel.reintentarMas() }
        binding.listaNotificaciones.adapter = ConcatAdapter(adapter, pieAdapter)
        binding.listaNotificaciones.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(lista: RecyclerView, dx: Int, dy: Int) = revisarFinal()
        })

        binding.deslizarRefrescar.setOnRefreshListener { viewModel.refrescar() }
        binding.estadoPantalla.botonReintentar.setOnClickListener { viewModel.reintentar() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.estado.collect(::mostrar) }
                launch {
                    viewModel.estado.combine(viewModel.noLeidas) { estado, noLeidas ->
                        !estado.marcandoTodas && (noLeidas ?: 0) > 0
                    }.collect { habilitado ->
                        binding.barra.menu.findItem(R.id.accion_marcar_todas)?.isEnabled = habilitado
                    }
                }
                launch { viewModel.eventos.collect(::atenderEvento) }
            }
        }
    }

    /** Los chips están en el layout; cuál está elegido sale del ViewModel (no del estado guardado de la vista). */
    private fun configurarFiltros() = with(binding) {
        chipTodas.root.setText(R.string.notificaciones_filtro_todas)
        chipNoLeidas.root.setText(R.string.notificaciones_filtro_no_leidas)
        chipTodas.root.isSaveEnabled = false
        chipNoLeidas.root.isSaveEnabled = false
        val elegido = if (viewModel.estado.value.soloNoLeidas) chipNoLeidas.root else chipTodas.root
        grupoFiltros.check(elegido.id)
        grupoFiltros.setOnCheckedStateChangeListener { _, ids ->
            when (ids.firstOrNull()) {
                chipTodas.root.id -> viewModel.cambiarFiltro(soloNoLeidas = false)
                chipNoLeidas.root.id -> viewModel.cambiarFiltro(soloNoLeidas = true)
            }
        }
    }

    private fun atenderEvento(evento: EventoNotificaciones) {
        when (evento) {
            is EventoNotificaciones.Mensaje -> Snackbar.make(binding.root, evento.texto, Snackbar.LENGTH_LONG).show()
            is EventoNotificaciones.Abrir -> abrir(evento.destino)
        }
    }

    private fun abrir(destino: DestinoNotificacion) {
        val navegacion = findNavController()
        // Dos toques seguidos no abren dos detalles.
        if (navegacion.currentDestination?.id != R.id.notificacionesFragment) return
        when (destino) {
            is DestinoNotificacion.Donacion -> navegacion.navigate(
                R.id.action_notificaciones_a_detalle_donacion,
                Bundle().apply { putInt(DetalleDonacionFragment.ARG_ID, destino.id) },
            )
            is DestinoNotificacion.Solicitud -> navegacion.navigate(
                R.id.action_notificaciones_a_detalle_solicitud,
                Bundle().apply { putInt(DetalleSolicitudFragment.ARG_ID, destino.id) },
            )
        }
    }

    /** Pide la siguiente página cuando faltan pocas tarjetas para el final. */
    private fun revisarFinal() {
        val gestor = binding.listaNotificaciones.layoutManager as? LinearLayoutManager ?: return
        if (adapter.itemCount > 0 && gestor.findLastVisibleItemPosition() >= adapter.itemCount - MARGEN_PAGINA) {
            viewModel.cargarMas()
        }
    }

    private fun mostrar(estado: NotificacionesEstado) = with(binding) {
        // Revisa el final cuando la lista ya se dibujó (si la primera página no llena la pantalla).
        adapter.submitList(estado.notificaciones) { listaNotificaciones.post { if (_binding != null) revisarFinal() } }
        pieAdapter.pie = when {
            estado.cargandoMas -> PieLista.Cargando
            estado.errorMas != null -> PieLista.Error(estado.errorMas)
            else -> null
        }

        deslizarRefrescar.isRefreshing = estado.refrescando
        val mensaje = when {
            estado.error != null -> estado.error
            estado.vacia && estado.soloNoLeidas -> getString(R.string.notificaciones_vacia_no_leidas)
            estado.vacia -> getString(R.string.notificaciones_vacia)
            else -> null
        }
        estadoPantalla.mostrar(
            cargando = estado.cargando && !estado.refrescando,
            mensaje = mensaje,
            conReintentar = estado.error != null,
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val MARGEN_PAGINA = 3
    }
}
