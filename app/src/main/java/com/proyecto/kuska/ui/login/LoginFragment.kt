package com.proyecto.kuska.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
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
import com.proyecto.kuska.R
import com.proyecto.kuska.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch

/** Inicio de sesión. Al guardar la sesión, MainActivity cambia a la navegación del rol. */
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels { LoginViewModel.Factory }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.entradaCorreo.doAfterTextChanged { viewModel.limpiarErrorCorreo() }
        binding.entradaContrasena.doAfterTextChanged { viewModel.limpiarErrorContrasena() }
        binding.entradaContrasena.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_DONE) {
                enviar()
                true
            } else {
                false
            }
        }

        binding.botonIniciarSesion.setOnClickListener { enviar() }
        binding.botonOlvidaste.setOnClickListener {
            findNavController().navigate(R.id.action_login_a_recuperar)
        }
        binding.botonCrearCuenta.setOnClickListener {
            findNavController().navigate(R.id.action_login_a_registro)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estado.collect(::mostrar)
            }
        }
    }

    private fun enviar() {
        WindowCompat.getInsetsController(requireActivity().window, binding.root)
            .hide(WindowInsetsCompat.Type.ime())
        viewModel.iniciarSesion(
            binding.entradaCorreo.text?.toString().orEmpty(),
            binding.entradaContrasena.text?.toString().orEmpty(),
        )
    }

    private fun mostrar(estado: LoginEstado) = with(binding) {
        campoCorreo.error = estado.errorCorreo?.let(::getString)
        campoContrasena.error = estado.errorContrasena?.let(::getString)

        avisoError.text = estado.errorApi
        avisoError.isVisible = estado.errorApi != null

        botonIniciarSesion.isEnabled = !estado.cargando
        botonIniciarSesion.text = if (estado.cargando) null else getString(R.string.login_boton)
        botonIniciarSesion.contentDescription =
            if (estado.cargando) getString(R.string.login_cargando) else null
        indicadorCarga.isVisible = estado.cargando
        entradaCorreo.isEnabled = !estado.cargando
        entradaContrasena.isEnabled = !estado.cargando
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
