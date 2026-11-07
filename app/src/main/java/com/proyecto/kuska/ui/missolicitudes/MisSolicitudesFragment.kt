package com.proyecto.kuska.ui.missolicitudes

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
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.MiSolicitud
import com.proyecto.kuska.databinding.FragmentMisSolicitudesBinding
import com.proyecto.kuska.ui.carrito.irAPestana
import com.proyecto.kuska.ui.comun.EstadosVisuales
import com.proyecto.kuska.ui.comun.PieLista
import com.proyecto.kuska.ui.comun.PieListaAdapter
import com.proyecto.kuska.ui.comun.mostrar
import com.proyecto.kuska.ui.detallesolicitud.DetalleSolicitudFragment
import kotlinx.coroutines.launch

/**
 * Mis solicitudes (HU-051, HU-052): lista paginada con filtro por estado. Se recarga cada vez que se vuelve a la
 * pestaña (del detalle, de otra pestaña o de segundo plano), así se ven los cambios de estado (p. ej. RN-27).
 */
class MisSolicitudesFragment : Fragment() {

    private var _binding: FragmentMisSolicitudesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MisSolicitudesViewModel by viewModels { MisSolicitudesViewModel.Factory }

    private lateinit var adapter: SolicitudesAdapter
    private lateinit var pieAdapter: PieListaAdapter

    /** Id de cada chip → estado (null = Todas). */
    private val filtros = mutableMapOf<Int, String?>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMisSolicitudesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        crearChips()
        binding.grupoFiltros.setOnCheckedStateChangeListener { _, ids ->
            ids.firstOrNull()?.let { viewModel.cambiarFiltro(filtros[it]) }
        }

        adapter = SolicitudesAdapter(::abrirDetalle)
        pieAdapter = PieListaAdapter { viewModel.reintentarMas() }
        binding.listaSolicitudes.adapter = ConcatAdapter(adapter, pieAdapter)
        binding.listaSolicitudes.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(lista: RecyclerView, dx: Int, dy: Int) = revisarFinal()
        })

        binding.deslizarRefrescar.setOnRefreshListener { viewModel.refrescar() }
        // Sin pedidos el botón lleva a Inicio; con un error, reintenta.
        binding.estadoPantalla.botonReintentar.setOnClickListener {
            val estado = viewModel.estado.value
            if (estado.vacia && estado.filtro == null) {
                requireActivity().irAPestana(R.id.inicioBeneficiarioFragment)
            } else {
                viewModel.reintentar()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.estado.collect(::mostrar) }
                launch {
                    viewModel.mensajes.collect { mensaje ->
                        Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (consumirMostrarTodas()) {
            viewModel.mostrarTodas()
            marcarChip(null)
        }
        viewModel.alVolver()
    }

    override fun onStop() {
        super.onStop()
        // Al girar la pantalla no se recarga; en cualquier otra salida, sí al volver.
        if (!requireActivity().isChangingConfigurations) viewModel.marcarSalida()
    }

    private fun crearChips() {
        filtros.clear()
        // Los ids se generan en cada vista: el estado de los chips sale del ViewModel, no del guardado.
        binding.grupoFiltros.isSaveEnabled = false
        val opciones = listOf<String?>(null) + MiSolicitud.ESTADOS
        val elegido = viewModel.estado.value.filtro
        opciones.forEach { estado ->
            val chip = layoutInflater.inflate(R.layout.chip_filtro, binding.grupoFiltros, false) as Chip
            chip.id = View.generateViewId()
            chip.isSaveEnabled = false
            chip.text = estado?.let { EstadosVisuales.etiqueta(it)?.let(::getString) ?: it }
                ?: getString(R.string.mis_donaciones_filtro_todas)
            binding.grupoFiltros.addView(chip)
            filtros[chip.id] = estado
            if (estado == elegido) chip.isChecked = true
        }
    }

    /** Marca el chip de [estado] sin volver a pedir la lista (cambiarFiltro ignora el filtro actual). */
    private fun marcarChip(estado: String?) {
        val id = filtros.entries.firstOrNull { it.value == estado }?.key ?: return
        binding.grupoFiltros.check(id)
    }

    private fun abrirDetalle(solicitud: MiSolicitud) {
        findNavController().navigate(
            R.id.action_mis_solicitudes_a_detalle,
            Bundle().apply { putInt(DetalleSolicitudFragment.ARG_ID, solicitud.id) },
        )
    }

    /** Pide la siguiente página cuando faltan pocas tarjetas para el final. */
    private fun revisarFinal() {
        val gestor = binding.listaSolicitudes.layoutManager as? LinearLayoutManager ?: return
        if (adapter.itemCount > 0 && gestor.findLastVisibleItemPosition() >= adapter.itemCount - MARGEN_PAGINA) {
            viewModel.cargarMas()
        }
    }

    private fun mostrar(estado: MisSolicitudesEstado) = with(binding) {
        // Revisa el final cuando la lista ya se dibujó (si la primera página no llena la pantalla).
        adapter.submitList(estado.solicitudes) { listaSolicitudes.post { if (_binding != null) revisarFinal() } }
        pieAdapter.pie = when {
            estado.cargandoMas -> PieLista.Cargando
            estado.errorMas != null -> PieLista.Error(estado.errorMas)
            else -> null
        }

        deslizarRefrescar.isRefreshing = estado.refrescando
        val sinPedidos = estado.vacia && estado.filtro == null
        val mensaje = when {
            estado.error != null -> estado.error
            sinPedidos -> getString(R.string.mis_solicitudes_vacia)
            estado.vacia -> getString(R.string.mis_solicitudes_vacia_filtro)
            else -> null
        }
        estadoPantalla.mostrar(
            cargando = estado.cargando && !estado.refrescando,
            mensaje = mensaje,
            conReintentar = estado.error != null || sinPedidos,
        )
        estadoPantalla.botonReintentar.setText(
            if (estado.error == null && sinPedidos) R.string.carrito_ir_inicio else R.string.accion_reintentar
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val MARGEN_PAGINA = 3

        /** Lo pide "Ver mis solicitudes" (carrito) antes de cambiar de pestaña. Se atiende en el siguiente onStart. */
        @Volatile
        private var mostrarTodasAlEntrar = false

        fun pedirMostrarTodas() {
            mostrarTodasAlEntrar = true
        }

        private fun consumirMostrarTodas(): Boolean {
            val pedido = mostrarTodasAlEntrar
            mostrarTodasAlEntrar = false
            return pedido
        }
    }
}
