package com.proyecto.kuska.ui.misdonaciones

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.proyecto.kuska.R
import com.proyecto.kuska.databinding.FragmentMisDonacionesBinding

/** Mis donaciones (provisional): el listado llegará después. Por ahora abre el flujo de registrar donación. */
class MisDonacionesFragment : Fragment() {

    private var _binding: FragmentMisDonacionesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMisDonacionesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.botonNuevaDonacion.setOnClickListener {
            findNavController().navigate(R.id.action_mis_donaciones_a_crear_donacion)
        }

        // CrearDonacionFragment deja esta marca al enviar la donación.
        val estadoGuardado = findNavController().currentBackStackEntry?.savedStateHandle ?: return
        estadoGuardado.getLiveData<Boolean>(RESULTADO_DONACION_ENVIADA).observe(viewLifecycleOwner) { enviada ->
            if (enviada != true) return@observe
            estadoGuardado.remove<Boolean>(RESULTADO_DONACION_ENVIADA)
            Snackbar.make(binding.root, R.string.mis_donaciones_enviada, Snackbar.LENGTH_LONG)
                .setAnchorView(binding.botonNuevaDonacion)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Clave en el SavedStateHandle de esta pantalla para avisar que se envió una donación. */
        const val RESULTADO_DONACION_ENVIADA = "donacion_enviada"
    }
}
