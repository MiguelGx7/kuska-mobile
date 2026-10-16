package com.proyecto.kuska.ui.recuperar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
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
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.proyecto.kuska.R
import com.proyecto.kuska.databinding.FragmentRecuperarBinding
import com.proyecto.kuska.ui.comun.alPulsarListo
import com.proyecto.kuska.ui.comun.mostrar
import com.proyecto.kuska.ui.comun.mostrarAviso
import com.proyecto.kuska.ui.comun.mostrarReenvio
import com.proyecto.kuska.ui.comun.resolver
import kotlinx.coroutines.launch

/** Recuperar contraseña (RN-04b). Al terminar vuelve al Login con el correo escrito; no inicia sesión. */
class RecuperarFragment : Fragment() {

    private var _binding: FragmentRecuperarBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecuperarViewModel by viewModels { RecuperarViewModel.Factory }

    private lateinit var campos: Map<CampoRecuperar, Pair<TextInputLayout, EditText>>
    private lateinit var atrasPorPasos: OnBackPressedCallback
    private var pasoMostrado = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentRecuperarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // El correo que venía del Login. Después de rotar, el campo restaura su propio texto.
        if (savedInstanceState == null) binding.entradaCorreo.setText(viewModel.estado.value.correo)

        campos = with(binding) {
            mapOf(
                CampoRecuperar.CORREO to (campoCorreo to entradaCorreo),
                CampoRecuperar.CODIGO to (codigo.campoCodigo to codigo.entradaCodigo),
                CampoRecuperar.CONTRASENA_NUEVA to (campoContrasena to entradaContrasena),
                CampoRecuperar.CONFIRMAR_CONTRASENA to (campoConfirmarContrasena to entradaConfirmarContrasena),
            )
        }
        campos.forEach { (campo, par) ->
            par.second.doAfterTextChanged { viewModel.cambiarCampo(campo, it?.toString().orEmpty()) }
        }
        binding.entradaCorreo.alPulsarListo { accionPrincipal() }
        binding.entradaConfirmarContrasena.alPulsarListo { accionPrincipal() }
        binding.codigo.botonReenviar.setOnClickListener { viewModel.reenviarCodigo() }
        binding.botonCarga.boton.setOnClickListener { accionPrincipal() }

        // En el paso 2, "atrás" vuelve al paso 1; en el paso 1 cierra la pantalla.
        atrasPorPasos = requireActivity().onBackPressedDispatcher
            .addCallback(viewLifecycleOwner, enabled = false) { viewModel.atras() }
        binding.barra.setNavigationOnClickListener {
            if (!viewModel.atras()) findNavController().navigateUp()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.estado.collect(::mostrar) }
                launch { viewModel.eventos.collect(::atenderEvento) }
            }
        }
    }

    private fun atenderEvento(evento: EventoRecuperar) {
        when (evento) {
            is EventoRecuperar.CodigoReenviado -> Snackbar.make(
                binding.root,
                evento.mensaje ?: getString(R.string.recuperar_codigo_enviado),
                Snackbar.LENGTH_SHORT,
            ).show()
            // El Login lee el correo de su SavedStateHandle y muestra el aviso.
            is EventoRecuperar.ContrasenaCambiada -> {
                val navegacion = findNavController()
                navegacion.previousBackStackEntry?.savedStateHandle
                    ?.set(RecuperarViewModel.RESULTADO_CORREO, evento.correo)
                navegacion.popBackStack()
            }
        }
    }

    private fun accionPrincipal() {
        WindowCompat.getInsetsController(requireActivity().window, binding.root)
            .hide(WindowInsetsCompat.Type.ime())
        viewModel.accionPrincipal()
    }

    private fun mostrar(estado: RecuperarEstado) = with(binding) {
        val contexto = requireContext()

        barra.subtitle = getString(R.string.recuperar_paso, estado.paso)
        progreso.setProgressCompat(estado.paso, true)
        pasoCorreo.isVisible = estado.paso == 1
        pasoCambio.isVisible = estado.paso == 2
        atrasPorPasos.isEnabled = estado.paso > 1
        if (estado.paso != pasoMostrado) {
            desplazamiento.scrollTo(0, 0)
            pasoMostrado = estado.paso
        }

        campos.forEach { (campo, par) ->
            par.first.error = estado.errores[campo]?.resolver(contexto)
        }

        textoMensajeEnvio.text = estado.mensajeEnvio ?: getString(R.string.recuperar_codigo_enviado)
        textoBandeja.text = getString(R.string.recuperar_revisa_bandeja, estado.correoConCodigo.orEmpty())
        codigo.mostrarReenvio(estado.segundosReenvio, estado.cargando)

        avisoError.mostrarAviso(estado.errorApi)
        val textoBoton = getString(
            when {
                estado.paso == 2 -> R.string.recuperar_cambiar
                estado.codigoYaEnviado -> R.string.accion_continuar
                else -> R.string.accion_enviar_codigo
            }
        )
        botonCarga.mostrar(textoBoton, estado.cargando)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
