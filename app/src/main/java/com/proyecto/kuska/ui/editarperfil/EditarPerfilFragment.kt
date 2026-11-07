package com.proyecto.kuska.ui.editarperfil

import com.proyecto.kuska.ui.comun.IlustracionEstado
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Perfil
import com.proyecto.kuska.databinding.FragmentEditarPerfilBinding
import com.proyecto.kuska.ui.comun.alPulsarListo
import com.proyecto.kuska.ui.comun.mostrar
import com.proyecto.kuska.ui.comun.mostrarAviso
import com.proyecto.kuska.ui.comun.resolver
import com.proyecto.kuska.ui.perfil.DatosPerfil
import com.proyecto.kuska.ui.perfil.PerfilFragment
import com.proyecto.kuska.ui.perfil.mostrarDatos
import kotlinx.coroutines.launch

/** Editar perfil (HU-013): "Guardar cambios" solo con cambios y "¿Descartar los cambios?" al salir. */
class EditarPerfilFragment : Fragment() {

    private var _binding: FragmentEditarPerfilBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EditarPerfilViewModel by viewModels { EditarPerfilViewModel.Factory }

    private lateinit var campos: Map<CampoPerfil, Pair<TextInputLayout, EditText>>

    /** Los textos se llenan una sola vez, al llegar el perfil (después los maneja el usuario). */
    private var perfilMostrado: Perfil? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentEditarPerfilBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        perfilMostrado = null
        campos = with(binding) {
            mapOf(
                CampoPerfil.NOMBRES to (campoNombres to entradaNombres),
                CampoPerfil.APELLIDOS to (campoApellidos to entradaApellidos),
                CampoPerfil.TELEFONO to (campoTelefono to entradaTelefono),
                CampoPerfil.DIRECCION to (campoDireccion to entradaDireccion),
            )
        }
        binding.entradaDireccion.alPulsarListo(::ocultarTeclado)
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
                launch {
                    viewModel.eventos.collect { evento ->
                        when (evento) {
                            EventoEditarPerfil.Guardado -> {
                                val navegacion = findNavController()
                                navegacion.previousBackStackEntry?.savedStateHandle
                                    ?.set(PerfilFragment.RESULTADO_EXITO, getString(R.string.perfil_cambios_guardados))
                                navegacion.popBackStack()
                            }
                        }
                    }
                }
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

    private fun ocultarTeclado() {
        WindowCompat.getInsetsController(requireActivity().window, binding.root)
            .hide(WindowInsetsCompat.Type.ime())
    }

    private fun mostrar(estado: EditarPerfilEstado) = with(binding) {
        val original = estado.original
        desplazamiento.isVisible = original != null
        estadoPantalla.mostrar(
            cargando = estado.cargando,
            mensaje = estado.errorCarga,
            conReintentar = estado.errorCarga != null,
            ilustracion = estado.errorCarga?.let { IlustracionEstado.deError(estado.codigoError) },
        )
        if (original == null) return@with

        if (original != perfilMostrado) {
            perfilMostrado = original
            contenedorSoloLectura.mostrarDatos(DatosPerfil.soloLectura(original))
            // Al girar, el EditText ya restauró su texto (igual al del ViewModel); solo se llena si difiere.
            campos.forEach { (campo, par) ->
                val valor = estado.valores.valor(campo)
                if (par.second.text.toString() != valor) par.second.setText(valor)
                par.second.doAfterTextChanged { viewModel.cambiarCampo(campo, it?.toString().orEmpty()) }
            }
        }

        campos.forEach { (campo, par) ->
            par.first.error = estado.errores[campo]?.resolver(requireContext())
            par.second.isEnabled = !estado.guardando
        }
        avisoError.mostrarAviso(estado.errorApi)
        botonCarga.mostrar(getString(R.string.editar_guardar), estado.guardando, habilitado = estado.hayCambios)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
