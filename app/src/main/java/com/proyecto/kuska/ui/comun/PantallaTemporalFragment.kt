package com.proyecto.kuska.ui.comun

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.proyecto.kuska.databinding.FragmentTemporalBinding

/** Pantalla provisional que solo muestra un título. Se reemplaza al implementar cada pantalla. */
abstract class PantallaTemporalFragment(@param:StringRes private val titulo: Int) : Fragment() {

    private var _binding: FragmentTemporalBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTemporalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.titulo.setText(titulo)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
