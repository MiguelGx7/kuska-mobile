package com.proyecto.kuska.ui.cambiarcontrasena

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.proyecto.kuska.R
import com.proyecto.kuska.databinding.FragmentCambiarContrasenaBinding
import com.proyecto.kuska.ui.comun.alPulsarListo
import com.proyecto.kuska.ui.comun.mostrar
import com.proyecto.kuska.ui.comun.mostrarAviso
import com.proyecto.kuska.ui.comun.resolver
import com.proyecto.kuska.ui.perfil.PerfilFragment
import kotlinx.coroutines.launch

/**
 * Cambiar contraseña (HU-014). Al terminar vuelve a Perfil con "Contraseña actualizada.". Si no se pudo renovar la
 * sesión, el repositorio la cierra y MainActivity vuelve al login con el aviso.
 */
class CambiarContrasenaFragment : Fragment() {

    private var _binding: FragmentCambiarContrasenaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CambiarContrasenaViewModel by viewModels { CambiarContrasenaViewModel.Factory }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCambiarContrasenaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            entradaActual.doAfterTextChanged { viewModel.cambiarCampo(CampoContrasena.ACTUAL, it?.toString().orEmpty()) }
            entradaNueva.doAfterTextChanged { viewModel.cambiarCampo(CampoContrasena.NUEVA, it?.toString().orEmpty()) }
            entradaConfirmar.doAfterTextChanged {
                viewModel.cambiarCampo(CampoContrasena.CONFIRMAR, it?.toString().orEmpty())
            }
            entradaConfirmar.alPulsarListo(::guardar)
            botonCarga.boton.setOnClickListener { guardar() }
            barra.setNavigationOnClickListener { salir() }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { salir() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.estado.collect(::mostrar) }
                launch {
                    viewModel.eventos.collect { evento ->
                        when (evento) {
                            EventoCambiarContrasena.Cambiada -> {
                                val navegacion = findNavController()
                                navegacion.previousBackStackEntry?.savedStateHandle
                                    ?.set(PerfilFragment.RESULTADO_EXITO, getString(R.string.perfil_contrasena_actualizada))
                                navegacion.popBackStack()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun guardar() {
        WindowCompat.getInsetsController(requireActivity().window, binding.root).hide(WindowInsetsCompat.Type.ime())
        viewModel.guardar()
    }

    /** Mientras se cambia no se puede salir (el login por detrás tiene que terminar). */
    private fun salir() {
        if (viewModel.estado.value.guardando) return
        findNavController().popBackStack()
    }

    private fun mostrar(estado: CambiarContrasenaEstado) = with(binding) {
        val contexto = requireContext()
        campoActual.error = estado.errores[CampoContrasena.ACTUAL]?.resolver(contexto)
        campoNueva.error = estado.errores[CampoContrasena.NUEVA]?.resolver(contexto)
        campoConfirmar.error = estado.errores[CampoContrasena.CONFIRMAR]?.resolver(contexto)
        listOf(entradaActual, entradaNueva, entradaConfirmar).forEach { it.isEnabled = !estado.guardando }
        avisoError.mostrarAviso(estado.errorApi)
        botonCarga.mostrar(getString(R.string.perfil_contrasena_guardar), estado.guardando)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
