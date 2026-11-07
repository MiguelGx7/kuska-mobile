package com.proyecto.kuska.ui.carrito

import com.proyecto.kuska.ui.comun.IlustracionEstado
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.CarritoItem
import com.proyecto.kuska.databinding.FragmentCarritoBinding
import com.proyecto.kuska.ui.comun.mostrar
import com.proyecto.kuska.ui.comun.resolver
import kotlinx.coroutines.launch

/**
 * Pestaña Carrito del beneficiario (HU-045 a HU-050): ver el carrito agrupado por donación, cambiar cantidades,
 * quitar, elegir modalidad en las donaciones "Ambas" y enviar el pedido (una solicitud por donación, RN-22).
 */
class CarritoFragment : Fragment() {

    private var _binding: FragmentCarritoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CarritoViewModel by viewModels { CarritoViewModel.Factory }

    private lateinit var adapter: CarritoAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCarritoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = CarritoAdapter(
            alElegirModalidad = viewModel::elegirModalidad,
            alCambiarDireccion = viewModel::cambiarDireccion,
            alCambiarCantidad = viewModel::abrirCambio,
            alQuitar = ::confirmarQuitar,
        )
        with(binding) {
            listaCarrito.adapter = adapter
            // Sin animación de cambio: la fila se vuelve a dibujar en la misma vista y el campo no pierde el foco.
            (listaCarrito.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
            deslizarRefrescar.setOnRefreshListener { viewModel.refrescar() }
            estadoPantalla.botonReintentar.setOnClickListener {
                if (viewModel.estado.value.vacio) requireActivity().irAPestana(R.id.inicioBeneficiarioFragment)
                else viewModel.reintentar()
            }
            botonEnviar.boton.setOnClickListener {
                ocultarTeclado()
                viewModel.pedirEnvio()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.estado.collect(::mostrar) }
                launch { viewModel.eventos.collect(::atenderEvento) }
            }
        }
        viewModel.alMostrar()
    }

    private fun mostrar(estado: CarritoEstado) = with(binding) {
        adapter.submitList(FilasCarrito.armar(estado))
        deslizarRefrescar.isRefreshing = estado.refrescando

        val mensaje = when {
            estado.error != null -> estado.error
            estado.vacio -> getString(R.string.carrito_vacio)
            else -> null
        }
        estadoPantalla.mostrar(
            cargando = estado.cargando && !estado.refrescando,
            mensaje = mensaje,
            conReintentar = estado.error != null || estado.vacio,
            ilustracion = when {
                estado.error != null -> IlustracionEstado.deError(estado.codigoError)
                estado.vacio -> IlustracionEstado.PLATO_VACIO
                else -> null
            },
        )
        estadoPantalla.botonReintentar.setText(if (estado.vacio) R.string.carrito_ir_inicio else R.string.accion_reintentar)

        contenedorEnviar.isVisible = estado.carrito?.grupos?.isNotEmpty() == true
        botonEnviar.mostrar(
            getString(R.string.carrito_enviar),
            cargando = estado.enviando,
            habilitado = EnvioCarrito.puedeEnviar(estado.carrito, ocupado = estado.quitandoId != null || estado.cambio?.guardando == true),
        )

        mostrarDialogos(estado)
    }

    /** Los diálogos con estado en el ViewModel: se abren aquí y cada uno se cierra solo cuando su estado se va. */
    private fun mostrarDialogos(estado: CarritoEstado) {
        val gestor = childFragmentManager
        if (gestor.isStateSaved) return
        if (estado.cambio != null && gestor.findFragmentByTag(CambiarCantidadDialogFragment.TAG) == null) {
            CambiarCantidadDialogFragment().showNow(gestor, CambiarCantidadDialogFragment.TAG)
        }
        if (estado.pedidoEnviado != null && gestor.findFragmentByTag(PedidoEnviadoDialogFragment.TAG) == null) {
            PedidoEnviadoDialogFragment().showNow(gestor, PedidoEnviadoDialogFragment.TAG)
        }
    }

    private fun atenderEvento(evento: EventoCarrito) {
        when (evento) {
            is EventoCarrito.Mensaje -> {
                val barra = Snackbar.make(binding.root, evento.texto.resolver(requireContext()), Snackbar.LENGTH_LONG)
                if (binding.contenedorEnviar.isVisible) barra.setAnchorView(binding.contenedorEnviar)
                barra.show()
            }
            is EventoCarrito.ConfirmarEnvio -> confirmarEnvio(evento.solicitudes)
        }
    }

    /** "Se crearán N solicitudes, una por donación." */
    private fun confirmarEnvio(solicitudes: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.carrito_enviar_titulo)
            .setMessage(resources.getQuantityString(R.plurals.carrito_enviar_mensaje, solicitudes, solicitudes))
            .setNegativeButton(R.string.accion_cancelar, null)
            .setPositiveButton(R.string.carrito_enviar_confirmar) { _, _ -> viewModel.enviar() }
            .show()
    }

    private fun confirmarQuitar(item: CarritoItem) {
        if (viewModel.estado.value.ocupado) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.carrito_quitar_titulo)
            .setMessage(getString(R.string.carrito_quitar_mensaje, item.alimento?.nombre.orEmpty()))
            .setNegativeButton(R.string.accion_cancelar, null)
            .setPositiveButton(R.string.carrito_quitar) { _, _ -> viewModel.quitar(item.id) }
            .show()
    }

    private fun ocultarTeclado() {
        WindowCompat.getInsetsController(requireActivity().window, binding.root).hide(WindowInsetsCompat.Type.ime())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/** Igual que tocar esa pestaña de la barra inferior. */
fun FragmentActivity.irAPestana(@IdRes destino: Int) {
    findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = destino
}
