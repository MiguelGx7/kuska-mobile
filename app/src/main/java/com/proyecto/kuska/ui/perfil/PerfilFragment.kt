package com.proyecto.kuska.ui.perfil

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.proyecto.kuska.KumoraApp
import com.proyecto.kuska.databinding.FragmentPerfilTemporalBinding
import kotlinx.coroutines.launch

/**
 * Perfil provisional: solo permite cerrar sesión para probar los dos roles.
 * La API no tiene auth/logout/, así que solo se borra la sesión local; MainActivity vuelve al login.
 */
class PerfilFragment : Fragment() {

    private var _binding: FragmentPerfilTemporalBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPerfilTemporalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.botonCerrarSesion.setOnClickListener {
            it.isEnabled = false
            val repositorio = (requireActivity().application as KumoraApp).authRepositorio
            viewLifecycleOwner.lifecycleScope.launch { repositorio.cerrarSesion() }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
