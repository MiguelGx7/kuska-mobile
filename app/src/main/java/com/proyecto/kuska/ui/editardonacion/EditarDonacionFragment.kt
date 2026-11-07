package com.proyecto.kuska.ui.editardonacion

import com.proyecto.kuska.ui.comun.IlustracionEstado
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.proyecto.kuska.R
import com.proyecto.kuska.databinding.FragmentEditarDonacionBinding
import com.proyecto.kuska.ui.comun.mostrar
import com.proyecto.kuska.ui.comun.mostrarAviso
import com.proyecto.kuska.ui.creardonacion.VistaDatosDonacion
import com.proyecto.kuska.ui.detalledonacion.DetalleDonacionFragment
import kotlinx.coroutines.launch

/** Editar los datos de una donación Pendiente. Al guardar vuelve al detalle, que se recarga. */
class EditarDonacionFragment : Fragment() {

    private var _binding: FragmentEditarDonacionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EditarDonacionViewModel by viewModels { EditarDonacionViewModel.Factory }

    private lateinit var vistaDatos: VistaDatosDonacion

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentEditarDonacionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vistaDatos = VistaDatosDonacion(binding.datos)
        vistaDatos.configurar(
            iniciales = viewModel.estado.value.valores,
            alCambiar = viewModel::cambiarCampo,
            alElegirModalidad = viewModel::elegirModalidad,
            alPulsarListo = ::ocultarTeclado,
        )
        binding.botonCarga.boton.setOnClickListener {
            ocultarTeclado()
            viewModel.guardar()
        }
        binding.estadoPantalla.botonReintentar.setOnClickListener { viewModel.cargar() }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { salir() }
        binding.barra.setNavigationOnClickListener { salir() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.estado.collect(::mostrar) }
                launch { viewModel.eventos.collect(::atenderEvento) }
            }
        }
    }

    /** Atrás o flecha: si hay cambios sin guardar pregunta antes. Mientras se guarda no hace nada. */
    private fun salir() {
        val estado = viewModel.estado.value
        when {
            estado.guardando -> Unit
            estado.hayCambios -> MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.descartar_cambios_titulo)
                .setMessage(R.string.descartar_cambios_mensaje)
                .setPositiveButton(R.string.crear_donacion_descartar) { _, _ -> findNavController().popBackStack() }
                .setNegativeButton(R.string.crear_donacion_seguir_editando, null)
                .show()
            else -> findNavController().popBackStack()
        }
    }

    private fun atenderEvento(evento: EventoEditar) {
        // El detalle lee el resultado de su SavedStateHandle, lo muestra y se recarga.
        val navegacion = findNavController()
        val detalle = navegacion.previousBackStackEntry?.savedStateHandle
        when (evento) {
            EventoEditar.Guardada ->
                detalle?.set(DetalleDonacionFragment.RESULTADO_EXITO, getString(R.string.detalle_cambios_guardados))
            is EventoEditar.YaNoEditable -> detalle?.set(DetalleDonacionFragment.RESULTADO_AVISO, evento.mensaje)
        }
        navegacion.popBackStack()
    }

    private fun ocultarTeclado() {
        WindowCompat.getInsetsController(requireActivity().window, binding.root)
            .hide(WindowInsetsCompat.Type.ime())
    }

    private fun mostrar(estado: EditarDonacionEstado) = with(binding) {
        val cargada = estado.original != null
        desplazamiento.isVisible = cargada
        estadoPantalla.mostrar(
            cargando = estado.cargando,
            mensaje = estado.errorCarga,
            conReintentar = estado.errorCarga != null,
            ilustracion = estado.errorCarga?.let { IlustracionEstado.deError(estado.codigoError) },
        )
        if (!cargada) return@with

        vistaDatos.mostrar(estado.valores, estado.errores, editable = !estado.guardando)
        avisoError.mostrarAviso(estado.errorApi)
        botonCarga.mostrar(getString(R.string.editar_guardar), estado.guardando, habilitado = estado.hayCambios)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
