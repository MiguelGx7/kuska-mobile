package com.proyecto.kuska.ui.iniciobeneficiario

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.AlimentoDisponible
import com.proyecto.kuska.data.api.modelos.CategoriaPublica
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.databinding.FragmentInicioBeneficiarioBinding
import com.proyecto.kuska.ui.comun.PieLista
import com.proyecto.kuska.ui.comun.PieListaAdapter
import com.proyecto.kuska.ui.comun.mostrar
import com.proyecto.kuska.ui.detallealimento.DetalleAlimentoFragment
import kotlinx.coroutines.launch

/**
 * Inicio del beneficiario: alimentos disponibles (HU-038 a HU-041). El buscador y los filtros quedan fijos arriba;
 * contenedor_resumen (paso 15) se desplaza con la lista.
 */
class InicioBeneficiarioFragment : Fragment() {

    private var _binding: FragmentInicioBeneficiarioBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InicioBeneficiarioViewModel by viewModels { InicioBeneficiarioViewModel.Factory }

    private lateinit var adapter: AlimentosDisponiblesAdapter
    private lateinit var pieAdapter: PieListaAdapter

    /** Id de cada chip → valor del filtro (null = Todas). */
    private val chipsCategoria = mutableMapOf<Int, Int?>()
    private val chipsModalidad = mutableMapOf<Int, String?>()

    /** Categorías con las que se armaron los chips (se rearman solo si cambian). */
    private var categoriasMostradas: List<CategoriaPublica>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentInicioBeneficiarioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        categoriasMostradas = null

        val busqueda = viewModel.estado.value.filtros.busqueda
        if (binding.entradaBuscar.text.toString() != busqueda) binding.entradaBuscar.setText(busqueda)
        binding.entradaBuscar.doAfterTextChanged { viewModel.cambiarBusqueda(it?.toString().orEmpty()) }
        binding.entradaBuscar.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_SEARCH) {
                WindowCompat.getInsetsController(requireActivity().window, binding.root).hide(WindowInsetsCompat.Type.ime())
                true
            } else {
                false
            }
        }

        crearChipsModalidad()
        binding.grupoCategorias.setOnCheckedStateChangeListener { _, ids ->
            ids.firstOrNull()?.let { id -> if (id in chipsCategoria) viewModel.cambiarCategoria(chipsCategoria[id]) }
        }
        binding.grupoModalidades.setOnCheckedStateChangeListener { _, ids ->
            ids.firstOrNull()?.let { id -> if (id in chipsModalidad) viewModel.cambiarModalidad(chipsModalidad[id]) }
        }

        adapter = AlimentosDisponiblesAdapter(::abrirDetalle)
        pieAdapter = PieListaAdapter { viewModel.reintentarMas() }
        binding.listaAlimentos.adapter = ConcatAdapter(adapter, pieAdapter)
        binding.listaAlimentos.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(lista: RecyclerView, dx: Int, dy: Int) = revisarFinal()
        })
        binding.deslizarRefrescar.setOnRefreshListener { viewModel.refrescar() }
        binding.estadoPantalla.botonReintentar.setOnClickListener { viewModel.reintentar() }

        observarResultados()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.estado.collect(::mostrar) }
                launch { viewModel.categorias.estado.collect { crearChipsCategoria(it.lista) } }
                launch {
                    viewModel.mensajes.collect { mensaje ->
                        Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun nuevoChip(grupo: ChipGroup, texto: String): Chip {
        val chip = layoutInflater.inflate(R.layout.chip_filtro, grupo, false) as Chip
        // Los ids se generan en cada vista: la selección sale del ViewModel, no del estado guardado.
        chip.id = View.generateViewId()
        chip.isSaveEnabled = false
        chip.text = texto
        grupo.addView(chip)
        return chip
    }

    private fun crearChipsModalidad() {
        chipsModalidad.clear()
        binding.grupoModalidades.isSaveEnabled = false
        val elegida = viewModel.estado.value.filtros.modalidad
        FiltrosAlimentos.MODALIDADES.forEach { modalidad ->
            val texto = when (modalidad) {
                null -> getString(R.string.alimentos_modalidad_todas)
                Donacion.MODALIDAD_RECOGER -> getString(R.string.modalidad_recoger)
                else -> getString(R.string.modalidad_domicilio)
            }
            val chip = nuevoChip(binding.grupoModalidades, texto)
            chipsModalidad[chip.id] = modalidad
            if (modalidad == elegida) chip.isChecked = true
        }
    }

    /** "Todas" + las categorías activas. Si no cargaron, solo "Todas" (se reintentan al deslizar para refrescar). */
    private fun crearChipsCategoria(categorias: List<CategoriaPublica>) {
        if (categorias == categoriasMostradas) return
        categoriasMostradas = categorias
        val grupo = binding.grupoCategorias
        grupo.isSaveEnabled = false
        chipsCategoria.clear()
        grupo.removeAllViews()
        val elegida = viewModel.estado.value.filtros.categoria
        val opciones = listOf<CategoriaPublica?>(null) + categorias
        opciones.forEach { categoria ->
            val chip = nuevoChip(grupo, categoria?.nombre ?: getString(R.string.mis_donaciones_filtro_todas))
            chipsCategoria[chip.id] = categoria?.id
            if (categoria?.id == elegida) chip.isChecked = true
        }
        // La categoría elegida ya no está activa: vuelve a "Todas".
        if (grupo.checkedChipId == View.NO_ID) {
            grupo.check(grupo.getChildAt(0).id)
        }
    }

    private fun abrirDetalle(alimento: AlimentoDisponible) {
        findNavController().navigate(
            R.id.action_inicio_beneficiario_a_detalle_alimento,
            Bundle().apply { putString(DetalleAlimentoFragment.ARG_ALIMENTO, Gson().toJson(alimento)) },
        )
    }

    /** Pide la siguiente página cuando faltan pocas tarjetas para el final. */
    private fun revisarFinal() {
        val gestor = binding.listaAlimentos.layoutManager as? LinearLayoutManager ?: return
        if (adapter.itemCount > 0 && gestor.findLastVisibleItemPosition() >= adapter.itemCount - MARGEN_PAGINA) {
            viewModel.cargarMas()
        }
    }

    /** El detalle deja aquí "agregado" (Snackbar con "Ver carrito") o "recargar" (hubo un error de regla). */
    private fun observarResultados() {
        val estadoGuardado = findNavController().currentBackStackEntry?.savedStateHandle ?: return
        estadoGuardado.getLiveData<Boolean>(RESULTADO_AGREGADO).observe(viewLifecycleOwner) { listo ->
            if (listo != true) return@observe
            estadoGuardado.remove<Boolean>(RESULTADO_AGREGADO)
            Snackbar.make(binding.root, R.string.carrito_agregado, Snackbar.LENGTH_LONG)
                .setAction(R.string.carrito_ver) { verCarrito() }
                .show()
        }
        estadoGuardado.getLiveData<Boolean>(RESULTADO_RECARGAR).observe(viewLifecycleOwner) { recargar ->
            if (recargar != true) return@observe
            estadoGuardado.remove<Boolean>(RESULTADO_RECARGAR)
            viewModel.recargar()
        }
    }

    /** Igual que tocar la pestaña Carrito. */
    private fun verCarrito() {
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = R.id.carritoFragment
    }

    private fun mostrar(estado: InicioBeneficiarioEstado) = with(binding) {
        // Revisa el final cuando la lista ya se dibujó (si la primera página no llena la pantalla).
        adapter.submitList(estado.alimentos) { listaAlimentos.post { if (_binding != null) revisarFinal() } }
        pieAdapter.pie = when {
            estado.cargandoMas -> PieLista.Cargando
            estado.errorMas != null -> PieLista.Error(estado.errorMas)
            else -> null
        }

        deslizarRefrescar.isRefreshing = estado.refrescando
        val mensaje = when {
            estado.error != null -> estado.error
            estado.vacia && estado.filtros.activos -> getString(R.string.alimentos_vacia_filtro)
            estado.vacia -> getString(R.string.alimentos_vacia)
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
        /** Claves en el SavedStateHandle de esta pantalla que deja el detalle del alimento. */
        const val RESULTADO_AGREGADO = "alimento_agregado"
        const val RESULTADO_RECARGAR = "recargar_alimentos"

        private const val MARGEN_PAGINA = 3
    }
}
