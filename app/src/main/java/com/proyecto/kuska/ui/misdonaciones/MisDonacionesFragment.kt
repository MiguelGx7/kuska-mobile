package com.proyecto.kuska.ui.misdonaciones

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
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.databinding.FragmentMisDonacionesBinding
import com.proyecto.kuska.ui.comun.EstadosVisuales
import com.proyecto.kuska.ui.comun.PieLista
import com.proyecto.kuska.ui.comun.PieListaAdapter
import com.proyecto.kuska.ui.comun.mostrar
import com.proyecto.kuska.ui.detalledonacion.DetalleDonacionFragment
import kotlinx.coroutines.launch

/** Mis donaciones: lista paginada con filtro por estado. Desde aquí se crea una donación o se abre su detalle. */
class MisDonacionesFragment : Fragment() {

    private var _binding: FragmentMisDonacionesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MisDonacionesViewModel by viewModels { MisDonacionesViewModel.Factory }

    private lateinit var adapter: DonacionesAdapter
    private lateinit var pieAdapter: PieListaAdapter

    /** Id de cada chip → estado (null = Todas). */
    private val filtros = mutableMapOf<Int, String?>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMisDonacionesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.alVolver()

        crearChips()
        binding.grupoFiltros.setOnCheckedStateChangeListener { _, ids ->
            ids.firstOrNull()?.let { viewModel.cambiarFiltro(filtros[it]) }
        }

        adapter = DonacionesAdapter { donacion -> abrirDetalle(donacion) }
        pieAdapter = PieListaAdapter { viewModel.reintentarMas() }
        binding.listaDonaciones.adapter = ConcatAdapter(adapter, pieAdapter)
        binding.listaDonaciones.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(lista: RecyclerView, dx: Int, dy: Int) = revisarFinal()
        })

        binding.deslizarRefrescar.setOnRefreshListener { viewModel.refrescar() }
        binding.estadoPantalla.botonReintentar.setOnClickListener { viewModel.reintentar() }

        binding.botonNuevaDonacion.setOnClickListener {
            viewModel.marcarSalida()
            findNavController().navigate(R.id.action_mis_donaciones_a_crear_donacion)
        }

        observarResultados()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.estado.collect(::mostrar) }
                launch {
                    viewModel.mensajes.collect { mensaje ->
                        Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_LONG)
                            .setAnchorView(binding.botonNuevaDonacion)
                            .show()
                    }
                }
            }
        }
    }

    private fun crearChips() {
        filtros.clear()
        // Los ids se generan en cada vista: el estado de los chips sale del ViewModel, no del guardado.
        binding.grupoFiltros.isSaveEnabled = false
        val opciones = listOf<String?>(null) + Donacion.ESTADOS
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

    private fun abrirDetalle(donacion: Donacion) {
        viewModel.marcarSalida()
        findNavController().navigate(
            R.id.action_mis_donaciones_a_detalle,
            Bundle().apply { putInt(DetalleDonacionFragment.ARG_ID, donacion.id) },
        )
    }

    /** Pide la siguiente página cuando faltan pocas tarjetas para el final. */
    private fun revisarFinal() {
        val gestor = binding.listaDonaciones.layoutManager as? LinearLayoutManager ?: return
        if (adapter.itemCount > 0 && gestor.findLastVisibleItemPosition() >= adapter.itemCount - MARGEN_PAGINA) {
            viewModel.cargarMas()
        }
    }

    /** Avisos que dejan las otras pantallas en el SavedStateHandle de esta: donación enviada o eliminada. */
    private fun observarResultados() {
        val estadoGuardado = findNavController().currentBackStackEntry?.savedStateHandle ?: return
        mapOf(
            RESULTADO_DONACION_ENVIADA to R.string.mis_donaciones_enviada,
            RESULTADO_DONACION_ELIMINADA to R.string.mis_donaciones_eliminada,
        ).forEach { (clave, mensaje) ->
            estadoGuardado.getLiveData<Boolean>(clave).observe(viewLifecycleOwner) { listo ->
                if (listo != true) return@observe
                estadoGuardado.remove<Boolean>(clave)
                Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_LONG)
                    .setAnchorView(binding.botonNuevaDonacion)
                    .show()
            }
        }
    }

    private fun mostrar(estado: MisDonacionesEstado) = with(binding) {
        // Revisa el final cuando la lista ya se dibujó (si la primera página no llena la pantalla).
        adapter.submitList(estado.donaciones) { listaDonaciones.post { if (_binding != null) revisarFinal() } }
        pieAdapter.pie = when {
            estado.cargandoMas -> PieLista.Cargando
            estado.errorMas != null -> PieLista.Error(estado.errorMas)
            else -> null
        }

        deslizarRefrescar.isRefreshing = estado.refrescando
        val mensaje = when {
            estado.error != null -> estado.error
            estado.vacia && estado.filtro == null -> getString(R.string.mis_donaciones_vacia)
            estado.vacia -> getString(R.string.mis_donaciones_vacia_filtro)
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

    companion object {
        /** Claves en el SavedStateHandle de esta pantalla para los avisos de otras pantallas. */
        const val RESULTADO_DONACION_ENVIADA = "donacion_enviada"
        const val RESULTADO_DONACION_ELIMINADA = "donacion_eliminada"

        private const val MARGEN_PAGINA = 3
    }
}
