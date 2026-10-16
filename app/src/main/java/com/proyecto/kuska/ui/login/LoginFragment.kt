package com.proyecto.kuska.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.proyecto.kuska.R
import com.proyecto.kuska.databinding.FragmentLoginBinding
import com.proyecto.kuska.ui.comun.alPulsarListo
import com.proyecto.kuska.ui.comun.mostrar
import com.proyecto.kuska.ui.comun.mostrarAviso
import com.proyecto.kuska.ui.recuperar.RecuperarViewModel
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
        binding.entradaContrasena.alPulsarListo { enviar() }

        binding.botonCarga.boton.setOnClickListener { enviar() }
        binding.botonOlvidaste.setOnClickListener {
            findNavController().navigate(
                R.id.action_login_a_recuperar,
                bundleOf(RecuperarViewModel.ARG_CORREO to binding.entradaCorreo.text?.toString()?.trim().orEmpty()),
            )
        }
        binding.botonCrearCuenta.setOnClickListener {
            findNavController().navigate(R.id.action_login_a_registro)
        }

        recibirContrasenaCambiada()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estado.collect(::mostrar)
            }
        }
    }

    /** Al volver de Recuperar con la contraseña cambiada: deja el correo escrito y avisa. */
    private fun recibirContrasenaCambiada() {
        val estadoGuardado = findNavController().currentBackStackEntry?.savedStateHandle ?: return
        estadoGuardado.getLiveData<String>(RecuperarViewModel.RESULTADO_CORREO)
            .observe(viewLifecycleOwner) { correo ->
                if (correo == null) return@observe
                // Se borra para que no se repita al rotar.
                estadoGuardado.remove<String>(RecuperarViewModel.RESULTADO_CORREO)
                binding.entradaCorreo.setText(correo)
                binding.entradaContrasena.text = null
                binding.entradaContrasena.requestFocus()
                Snackbar.make(binding.root, R.string.recuperar_contrasena_cambiada, Snackbar.LENGTH_LONG).show()
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
        avisoError.mostrarAviso(estado.errorApi)
        botonCarga.mostrar(getString(R.string.login_boton), estado.cargando)
        entradaCorreo.isEnabled = !estado.cargando
        entradaContrasena.isEnabled = !estado.cargando
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
