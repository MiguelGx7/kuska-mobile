package com.proyecto.kuska.ui.detallealimento

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil.load
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.AlimentoDisponible
import com.proyecto.kuska.databinding.FragmentDetalleAlimentoBinding
import com.proyecto.kuska.ui.comun.alPulsarListo
import com.proyecto.kuska.ui.comun.mostrar
import com.proyecto.kuska.ui.comun.mostrarAviso
import com.proyecto.kuska.ui.iniciobeneficiario.InicioBeneficiarioFragment
import com.proyecto.kuska.ui.iniciobeneficiario.textoDisponible
import com.proyecto.kuska.ui.iniciobeneficiario.textoDonante
import com.proyecto.kuska.ui.iniciobeneficiario.textoModalidad
import com.proyecto.kuska.util.FormatoFecha
import kotlinx.coroutines.launch

/**
 * Detalle de un alimento disponible (HU-042 a HU-044) con los datos de la lista. Nunca muestra el teléfono ni la
 * dirección del donante (RN-29). Al agregar vuelve a la lista, que muestra "Agregado al carrito.".
 */
class DetalleAlimentoFragment : Fragment() {

    private var _binding: FragmentDetalleAlimentoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetalleAlimentoViewModel by viewModels { DetalleAlimentoViewModel.Factory }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDetalleAlimentoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val estado = viewModel.estado.value
        mostrarAlimento(estado.alimento)

        with(binding) {
            val selector = contenedorCantidad
            if (selector.entradaCantidad.text.toString() != estado.cantidad) selector.entradaCantidad.setText(estado.cantidad)
            selector.entradaCantidad.doAfterTextChanged { viewModel.cambiarCantidad(it?.toString().orEmpty()) }
            selector.entradaCantidad.alPulsarListo(::ocultarTeclado)
            selector.botonRestar.setOnClickListener { viewModel.restar() }
            selector.botonSumar.setOnClickListener { viewModel.sumar() }
            botonCarga.boton.setOnClickListener {
                ocultarTeclado()
                viewModel.agregar()
            }
            barra.setNavigationOnClickListener { salir() }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { salir() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.estado.collect(::mostrar) }
                launch { viewModel.eventos.collect(::atenderEvento) }
            }
        }
    }

    /** Lo que no cambia: los datos del alimento. */
    private fun mostrarAlimento(alimento: AlimentoDisponible) = with(binding) {
        val contexto = requireContext()
        if (alimento.fotoUrl != null) imagenFoto.load(alimento.fotoUrl) { crossfade(true) }
        imagenFoto.contentDescription = getString(R.string.alimento_foto_descripcion, alimento.nombre)
        textoNombre.text = alimento.nombre
        textoCategoria.mostrarAviso(alimento.categoriaNombre?.takeIf { it.isNotBlank() })
        textoDescripcion.mostrarAviso(alimento.descripcion?.takeIf { it.isNotBlank() })
        textoDisponible.text = contexto.textoDisponible(alimento)
        textoVence.mostrarAviso(FormatoFecha.fecha(alimento.fechaVencimiento)?.let { getString(R.string.alimento_vence, it) })
        textoModalidad.text = contexto.textoModalidad(alimento.modalidadEntrega)
        textoDonacion.mostrarAviso(
            alimento.donacionTitulo?.takeIf { it.isNotBlank() }?.let { getString(R.string.alimentos_donacion, it) }
        )
        textoDonante.mostrarAviso(contexto.textoDonante(alimento))

        contenedorCantidad.configurar(alimento.unidadMedida)
    }

    private fun atenderEvento(evento: EventoDetalleAlimento) {
        val lista = findNavController().previousBackStackEntry?.savedStateHandle
        when (evento) {
            EventoDetalleAlimento.Agregado -> {
                lista?.set(InicioBeneficiarioFragment.RESULTADO_AGREGADO, true)
                findNavController().popBackStack()
            }
            EventoDetalleAlimento.RecargarListaAlVolver -> lista?.set(InicioBeneficiarioFragment.RESULTADO_RECARGAR, true)
        }
    }

    /** Mientras se agrega no se puede salir (no hay doble envío ni resultado perdido). */
    private fun salir() {
        if (viewModel.estado.value.agregando) return
        findNavController().popBackStack()
    }

    private fun ocultarTeclado() {
        WindowCompat.getInsetsController(requireActivity().window, binding.root).hide(WindowInsetsCompat.Type.ime())
    }

    private fun mostrar(estado: DetalleAlimentoEstado) = with(binding) {
        val maximo = estado.maximo
        contenedorCantidad.root.isVisible = maximo != null
        textoSinCupo.isVisible = maximo == null
        botonCarga.root.isVisible = maximo != null
        if (maximo == null) return@with

        contenedorCantidad.mostrarCantidad(
            estado.cantidad, maximo, estado.alimento.unidadMedida, estado.errorCantidad, ocupado = estado.agregando,
        )
        avisoError.mostrarAviso(estado.aviso)
        botonCarga.mostrar(getString(R.string.carrito_agregar), estado.agregando)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Argumento de navegación: el AlimentoDisponible como JSON. */
        const val ARG_ALIMENTO = "alimento"
    }
}
