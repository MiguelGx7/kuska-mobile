package com.proyecto.kuska.ui.creardonacion

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.databinding.FragmentCrearDonacionBinding
import com.proyecto.kuska.ui.comun.alPulsarListo
import com.proyecto.kuska.ui.comun.mostrar
import com.proyecto.kuska.ui.comun.mostrarAviso
import com.proyecto.kuska.ui.comun.resolver
import com.proyecto.kuska.ui.misdonaciones.MisDonacionesFragment
import kotlinx.coroutines.launch

/** Registrar donación (RN-10): datos de la donación y sus alimentos. Se envía todo en una sola petición. */
class CrearDonacionFragment : Fragment() {

    private var _binding: FragmentCrearDonacionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CrearDonacionViewModel by navGraphViewModels(R.id.nav_crear_donacion) {
        CrearDonacionViewModel.Factory
    }

    private lateinit var campos: Map<CampoDonacion, Pair<TextInputLayout, EditText>>
    private lateinit var chipsModalidad: Map<Int, String>
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

        campos = with(binding) {
            mapOf(
                CampoDonacion.TITULO to (campoTitulo to entradaTitulo),
                CampoDonacion.DESCRIPCION to (campoDescripcion to entradaDescripcion),
                CampoDonacion.DIRECCION to (campoDireccion to entradaDireccion),
            )
        }
        chipsModalidad = mapOf(
            R.id.chip_recoger to Donacion.MODALIDAD_RECOGER,
            R.id.chip_domicilio to Donacion.MODALIDAD_DOMICILIO,
            R.id.chip_ambas to Donacion.MODALIDAD_AMBAS,
        )

        // Al volver del formulario del alimento la vista se recrea: se llena desde el ViewModel.
        val actual = viewModel.estado.value
        campos.forEach { (campo, par) ->
            val valor = valorDe(actual, campo)
            if (par.second.text.toString() != valor) par.second.setText(valor)
            par.second.doAfterTextChanged { viewModel.cambiarCampo(campo, it?.toString().orEmpty()) }
        }
        binding.entradaDireccion.alPulsarListo { ocultarTeclado() }
        binding.grupoModalidad.setOnCheckedStateChangeListener { _, ids ->
            viewModel.elegirModalidad(ids.firstOrNull()?.let(chipsModalidad::get))
        }

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

    private fun valorDe(estado: CrearDonacionEstado, campo: CampoDonacion) = when (campo) {
        CampoDonacion.TITULO -> estado.titulo
        CampoDonacion.DESCRIPCION -> estado.descripcion
        else -> estado.direccion
    }

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
        val contexto = requireContext()

        // Textos que cambia el ViewModel (p. ej. la dirección que llega del perfil).
        campos.forEach { (campo, par) ->
            val valor = valorDe(estado, campo)
            if (par.second.text.toString() != valor) par.second.setText(valor)
            par.first.error = estado.errores[campo]?.resolver(contexto)
        }

        val chipElegido = chipsModalidad.entries.firstOrNull { it.value == estado.modalidad }?.key
        if (chipElegido == null) grupoModalidad.clearCheck() else grupoModalidad.check(chipElegido)
        errorModalidad.mostrarAviso(estado.errores[CampoDonacion.MODALIDAD]?.resolver(contexto))

        adapter.submitList(estado.alimentos)
        textoSinAlimentos.isVisible = estado.alimentos.isEmpty()

        val editable = !estado.enviando
        campos.values.forEach { it.second.isEnabled = editable }
        chipsModalidad.keys.forEach { grupoModalidad.findViewById<View>(it).isEnabled = editable }
        botonAgregarAlimento.isEnabled = editable

        avisoError.mostrarAviso(estado.errorApi)
        botonCarga.mostrar(getString(R.string.crear_donacion_enviar), estado.enviando, habilitado = estado.puedeEnviar)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
