package com.proyecto.kuska.ui.creardonacion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.proyecto.kuska.R
import com.proyecto.kuska.databinding.FragmentCrearDonacionBinding
import com.proyecto.kuska.ui.comun.mostrar
import com.proyecto.kuska.ui.comun.mostrarAviso
import com.proyecto.kuska.ui.misdonaciones.MisDonacionesFragment
import kotlinx.coroutines.launch

/** Registrar donación (RN-10): datos de la donación y sus alimentos. Se envía todo en una sola petición. */
class CrearDonacionFragment : Fragment() {

    private var _binding: FragmentCrearDonacionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CrearDonacionViewModel by navGraphViewModels(R.id.nav_crear_donacion) {
        CrearDonacionViewModel.Factory
    }

    private lateinit var vistaDatos: VistaDatosDonacion
    private lateinit var adapter: AlimentosAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCrearDonacionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Al volver del formulario del alimento la vista se recrea: se llena desde el ViewModel.
        vistaDatos = VistaDatosDonacion(binding.datos)
        vistaDatos.configurar(
            iniciales = viewModel.estado.value.valores(),
            alCambiar = viewModel::cambiarCampo,
            alElegirModalidad = viewModel::elegirModalidad,
            alPulsarListo = ::ocultarTeclado,
        )

        adapter = AlimentosAdapter(
            alEditar = { indice -> abrirAlimento(indice) },
            alQuitar = viewModel::quitarAlimento,
        )
        binding.listaAlimentos.adapter = adapter

        binding.botonAgregarAlimento.setOnClickListener { abrirAlimento(-1) }
        binding.botonCarga.boton.setOnClickListener {
            ocultarTeclado()
            viewModel.enviar()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { salir() }
        binding.barra.setNavigationOnClickListener { salir() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.estado.collect(::mostrar) }
                launch { viewModel.donacionEnviada.collect { cerrarEnviada() } }
            }
        }
    }

    private fun CrearDonacionEstado.valores() = ValoresDatosDonacion(titulo, descripcion, direccion, modalidad)

    private fun abrirAlimento(indice: Int) {
        if (viewModel.estado.value.enviando) return
        ocultarTeclado()
        findNavController().navigate(
            R.id.action_crear_donacion_a_alimento,
            Bundle().apply { putInt(AlimentoFragment.ARG_INDICE, indice) },
        )
    }

    /** Atrás o flecha: si hay datos sin enviar pregunta antes de descartar. Mientras se envía no hace nada. */
    private fun salir() {
        val estado = viewModel.estado.value
        when {
            estado.enviando -> Unit
            estado.tieneDatosSinEnviar -> MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.crear_donacion_descartar_titulo)
                .setMessage(R.string.crear_donacion_descartar_mensaje)
                .setPositiveButton(R.string.crear_donacion_descartar) { _, _ -> cerrarFlujo() }
                .setNegativeButton(R.string.crear_donacion_seguir_editando, null)
                .show()
            else -> cerrarFlujo()
        }
    }

    private fun cerrarFlujo() {
        viewModel.descartar()
        findNavController().popBackStack(R.id.nav_crear_donacion, true)
    }

    /** Mis donaciones lee la marca de su SavedStateHandle y muestra el aviso. */
    private fun cerrarEnviada() {
        val navegacion = findNavController()
        runCatching { navegacion.getBackStackEntry(R.id.misDonacionesFragment) }.getOrNull()
            ?.savedStateHandle?.set(MisDonacionesFragment.RESULTADO_DONACION_ENVIADA, true)
        navegacion.popBackStack(R.id.nav_crear_donacion, true)
    }

    private fun ocultarTeclado() {
        WindowCompat.getInsetsController(requireActivity().window, binding.root)
            .hide(WindowInsetsCompat.Type.ime())
    }

    private fun mostrar(estado: CrearDonacionEstado) = with(binding) {
        val editable = !estado.enviando
        // Textos que cambia el ViewModel (p. ej. la dirección que llega del perfil).
        vistaDatos.mostrar(estado.valores(), estado.errores, editable)

        adapter.submitList(estado.alimentos)
        textoSinAlimentos.isVisible = estado.alimentos.isEmpty()
        botonAgregarAlimento.isEnabled = editable

        avisoError.mostrarAviso(estado.errorApi)
        botonCarga.mostrar(getString(R.string.crear_donacion_enviar), estado.enviando, habilitado = estado.puedeEnviar)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
