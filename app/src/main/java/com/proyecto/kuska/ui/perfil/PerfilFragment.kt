package com.proyecto.kuska.ui.perfil

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Perfil
import com.proyecto.kuska.databinding.FragmentPerfilBinding
import com.proyecto.kuska.ui.comun.mostrar
import kotlinx.coroutines.launch

/**
 * Perfil común a Donante y Beneficiario (HU-012 a HU-015). Editar y cambiar contraseña dejan su mensaje de éxito en
 * el SavedStateHandle de esta pantalla. Cerrar sesión solo borra la sesión local (la API no tiene logout) y
 * MainActivity vuelve al login.
 */
class PerfilFragment : Fragment() {

    private var _binding: FragmentPerfilBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PerfilViewModel by viewModels { PerfilViewModel.Factory }

    /** Para no volver a inflar los datos si el perfil no cambió. */
    private var perfilMostrado: Perfil? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPerfilBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        perfilMostrado = null

        binding.deslizarRefrescar.setOnRefreshListener { viewModel.refrescar() }
        binding.estadoPantalla.botonReintentar.setOnClickListener { viewModel.reintentar() }
        binding.botonEditar.setOnClickListener { abrir(R.id.action_perfil_a_editar) }
        binding.botonCambiarContrasena.setOnClickListener { abrir(R.id.action_perfil_a_cambiar_contrasena) }
        binding.botonCerrarSesion.setOnClickListener { confirmarCerrarSesion() }
        observarResultado()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.estado.collect(::mostrar) }
                launch {
                    viewModel.eventos.collect { evento ->
                        when (evento) {
                            is EventoPerfil.Mensaje -> Snackbar.make(binding.root, evento.texto, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun abrir(accion: Int) {
        if (viewModel.estado.value.cerrandoSesion) return
        findNavController().navigate(accion)
    }

    private fun observarResultado() {
        val estadoGuardado = findNavController().currentBackStackEntry?.savedStateHandle ?: return
        estadoGuardado.getLiveData<String>(RESULTADO_EXITO).observe(viewLifecycleOwner) { mensaje ->
            if (mensaje == null) return@observe
            estadoGuardado.remove<String>(RESULTADO_EXITO)
            viewModel.recibirResultado(mensaje)
        }
    }

    private fun confirmarCerrarSesion() {
        if (viewModel.estado.value.cerrandoSesion) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.perfil_cerrar_sesion_titulo)
            .setMessage(R.string.perfil_cerrar_sesion_mensaje)
            .setPositiveButton(R.string.perfil_cerrar_sesion) { _, _ -> viewModel.cerrarSesion() }
            .setNegativeButton(R.string.accion_cancelar, null)
            .show()
    }

    private fun mostrar(estado: PerfilEstado) = with(binding) {
        val perfil = estado.perfil
        contenido.isVisible = perfil != null
        if (perfil != null && perfil != perfilMostrado) {
            contenedorDatos.mostrarDatos(DatosPerfil.todos(perfil))
            perfilMostrado = perfil
        }
        deslizarRefrescar.isRefreshing = estado.refrescando
        deslizarRefrescar.isEnabled = !estado.cerrandoSesion
        estadoPantalla.mostrar(
            cargando = estado.cargando && !estado.refrescando,
            mensaje = estado.error,
            conReintentar = estado.error != null,
        )

        botonEditar.isEnabled = !estado.cerrandoSesion
        botonCambiarContrasena.isEnabled = !estado.cerrandoSesion
        botonCerrarSesion.isEnabled = !estado.cerrandoSesion
        botonCerrarSesion.text = if (estado.cerrandoSesion) null else getString(R.string.perfil_cerrar_sesion)
        botonCerrarSesion.contentDescription = if (estado.cerrandoSesion) getString(R.string.cargando) else null
        indicadorCerrarSesion.isVisible = estado.cerrandoSesion
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Clave en el SavedStateHandle de esta pantalla: mensaje de éxito de editar o de cambiar la contraseña. */
        const val RESULTADO_EXITO = "resultado_exito"
    }
}
