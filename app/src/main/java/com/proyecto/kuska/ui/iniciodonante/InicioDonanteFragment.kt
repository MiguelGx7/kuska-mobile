package com.proyecto.kuska.ui.iniciodonante

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.CategoriaSerie
import com.proyecto.kuska.data.api.modelos.DashboardDonante
import com.proyecto.kuska.databinding.FragmentInicioDonanteBinding
import com.proyecto.kuska.databinding.ItemCategoriaSerieBinding
import com.proyecto.kuska.databinding.ItemConteoEstadoBinding
import com.proyecto.kuska.databinding.VistaCifraBinding
import com.proyecto.kuska.ui.carrito.irAPestana
import com.proyecto.kuska.ui.comun.EstadosVisuales
import com.proyecto.kuska.ui.comun.mostrar
import com.proyecto.kuska.ui.comun.mostrarEstadoDonacion
import com.proyecto.kuska.ui.comun.resolver
import com.proyecto.kuska.ui.misdonaciones.MisDonacionesFragment
import kotlinx.coroutines.launch

/**
 * Inicio del donante (HU-035 a HU-037): saludo, "Nueva donación", donaciones por estado (tocar una abre Mis
 * donaciones con ese filtro), lo entregado con las cantidades por unidad y las series por mes y por categoría.
 * Una cuenta sin donaciones ve una tarjeta que invita a crear la primera. Se recarga al volver a la pestaña.
 */
class InicioDonanteFragment : Fragment() {

    private var _binding: FragmentInicioDonanteBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InicioDonanteViewModel by viewModels { InicioDonanteViewModel.Factory }

    /** Datos con los que se armaron las filas (se rearman solo si cambian). */
    private var mostrado: DashboardDonante? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentInicioDonanteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mostrado = null

        binding.botonNuevaDonacion.setOnClickListener { nuevaDonacion() }
        binding.botonPrimeraDonacion.setOnClickListener { nuevaDonacion() }
        binding.deslizarRefrescar.setOnRefreshListener { viewModel.refrescar() }
        binding.estadoPantalla.botonReintentar.setOnClickListener { viewModel.reintentar() }
        binding.leyendaMeses.textoPrimera.setText(R.string.inicio_donante_serie_donaciones)
        binding.leyendaMeses.textoSegunda.setText(R.string.inicio_donante_serie_entregas)
        binding.leyendaCategorias.textoPrimera.setText(R.string.inicio_donante_serie_donados)
        binding.leyendaCategorias.textoSegunda.setText(R.string.inicio_donante_serie_entregados)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.estado.collect(::mostrar) }
                launch {
                    viewModel.nombre.collect { nombre ->
                        binding.textoSaludo.text = nombre?.let { getString(R.string.inicio_hola_nombre, it) }
                            ?: getString(R.string.inicio_hola)
                    }
                }
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
        viewModel.alVolver()
    }

    override fun onStop() {
        super.onStop()
        // Al girar la pantalla no se recarga; en cualquier otra salida, sí al volver.
        if (!requireActivity().isChangingConfigurations) viewModel.marcarSalida()
    }

    /** Se abre desde Mis donaciones: así, al terminar, sale allí el aviso "Donación enviada". */
    private fun nuevaDonacion() {
        requireActivity().irAPestana(R.id.misDonacionesFragment)
        val navegacion = findNavController()
        // La pestaña puede volver con un detalle abierto: entonces se abre el flujo encima de él.
        if (navegacion.currentDestination?.id == R.id.misDonacionesFragment) {
            navegacion.navigate(R.id.action_mis_donaciones_a_crear_donacion)
        } else {
            navegacion.navigate(R.id.nav_crear_donacion)
        }
    }

    private fun abrirMisDonaciones(estado: String) {
        MisDonacionesFragment.pedirFiltro(estado)
        requireActivity().irAPestana(R.id.misDonacionesFragment)
    }

    private fun mostrar(estado: InicioDonanteEstado) = with(binding) {
        deslizarRefrescar.isRefreshing = estado.refrescando
        estadoPantalla.mostrar(
            cargando = estado.cargando && !estado.refrescando,
            mensaje = estado.error,
            conReintentar = estado.error != null,
        )
        val dashboard = estado.dashboard
        contenido.isVisible = dashboard != null
        if (dashboard == null || dashboard == mostrado) return@with
        mostrado = dashboard

        val nueva = ReglasInicioDonante.esCuentaNueva(dashboard)
        tarjetaVacia.isVisible = nueva
        seccionDatos.isVisible = !nueva
        if (nueva) return@with

        mostrarEstados(dashboard)
        mostrarEntregado(dashboard)
        mostrarMeses(dashboard)
        mostrarCategorias(ReglasInicioDonante.categorias(dashboard))
    }

    private fun mostrarEstados(dashboard: DashboardDonante) {
        val contenedor = binding.contenedorEstados
        contenedor.removeAllViews()
        ReglasInicioDonante.estados(dashboard).forEach { conteo ->
            val fila = ItemConteoEstadoBinding.inflate(layoutInflater, contenedor, true)
            val nombre = EstadosVisuales.etiqueta(conteo.estado)?.let(::getString) ?: conteo.estado
            fila.etiquetaEstado.mostrarEstadoDonacion(conteo.estado)
            fila.textoCantidad.text = conteo.cantidad.toString()
            fila.root.contentDescription = getString(R.string.inicio_donante_estado_descripcion, nombre, conteo.cantidad)
            fila.root.setOnClickListener { abrirMisDonaciones(conteo.estado) }
        }
    }

    private fun mostrarEntregado(dashboard: DashboardDonante) = with(binding) {
        cifra(cifraEntregas, dashboard.entregas, R.plurals.inicio_donante_entregas)
        cifra(cifraAlimentos, dashboard.alimentosEntregados, R.plurals.inicio_donante_alimentos)
        cifra(cifraPersonas, dashboard.personasAyudadas, R.plurals.inicio_donante_personas)

        val conEntregas = ReglasInicioDonante.tieneEntregas(dashboard)
        textoSinEntregas.isVisible = !conEntregas
        val cantidades = ReglasInicioDonante.cantidades(dashboard).map { it.resolver(requireContext()) }
        textoCantidades.isVisible = conEntregas && cantidades.isNotEmpty()
        textoCantidades.text = getString(R.string.inicio_donante_cantidades, cantidades.joinToString(SEPARADOR))
    }

    private fun cifra(vista: VistaCifraBinding, valor: Int, plural: Int) {
        val etiqueta = resources.getQuantityString(plural, valor)
        vista.numero.text = valor.toString()
        vista.etiqueta.text = etiqueta
        vista.root.contentDescription = "$valor $etiqueta"
    }

    private fun mostrarMeses(dashboard: DashboardDonante) = with(binding) {
        val meses = ReglasInicioDonante.meses(dashboard)
        val sinActividad = ReglasInicioDonante.sinActividad(meses)
        textoSinActividad.isVisible = sinActividad
        graficaMeses.isVisible = !sinActividad
        leyendaMeses.root.isVisible = !sinActividad
        graficaMeses.mostrar(meses)
        val nombres = resources.getStringArray(R.array.meses)
        graficaMeses.contentDescription = getString(
            R.string.inicio_donante_meses_descripcion,
            meses.joinToString(". ") { mes ->
                val nombre = mes.mes?.let { "${nombres[it - 1]} ${mes.anio}" } ?: mes.inicial
                getString(R.string.inicio_donante_mes_descripcion, nombre, mes.donaciones, mes.entregas)
            },
        )
    }

    private fun mostrarCategorias(categorias: List<CategoriaSerie>) = with(binding) {
        tituloCategorias.isVisible = categorias.isNotEmpty()
        tarjetaCategorias.isVisible = categorias.isNotEmpty()
        contenedorCategorias.removeAllViews()
        val maximo = EscalaGrafica.maximo(categorias.flatMap { listOf(it.alimentosDonados, it.alimentosEntregados) })
        categorias.forEach { categoria ->
            val fila = ItemCategoriaSerieBinding.inflate(layoutInflater, contenedorCategorias, true)
            fila.textoCategoria.text = categoria.categoria
            barra(fila.barraDonados, EscalaGrafica.proporcion(categoria.alimentosDonados, maximo))
            barra(fila.barraEntregados, EscalaGrafica.proporcion(categoria.alimentosEntregados, maximo))
            fila.numeroDonados.text = categoria.alimentosDonados.toString()
            fila.numeroEntregados.text = categoria.alimentosEntregados.toString()
            fila.root.contentDescription = getString(
                R.string.inicio_donante_categoria_descripcion,
                categoria.categoria, categoria.alimentosDonados, categoria.alimentosEntregados,
            )
        }
    }

    /** El ancho de la barra es su peso dentro de un contenedor con weightSum 1. */
    private fun barra(vista: View, proporcion: Float) {
        vista.layoutParams = (vista.layoutParams as LinearLayout.LayoutParams).apply { weight = proporcion }
        vista.isVisible = proporcion > 0f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val SEPARADOR = " · "
    }
}
