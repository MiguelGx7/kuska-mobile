package com.proyecto.kuska.ui.creardonacion

import androidx.navigation.navGraphViewModels
import com.proyecto.kuska.R
import com.proyecto.kuska.ui.comun.formularioalimento.EditorAlimento
import com.proyecto.kuska.ui.comun.formularioalimento.FormularioAlimentoFragment

/** Agregar (indice = -1) o editar un alimento de la donación que se está creando. Comparte el ViewModel del flujo. */
class AlimentoFragment : FormularioAlimentoFragment() {

    private val viewModel: CrearDonacionViewModel by navGraphViewModels(R.id.nav_crear_donacion) {
        CrearDonacionViewModel.Factory
    }

    override val editor: EditorAlimento get() = viewModel

    override fun indiceInicial() = requireArguments().getInt(ARG_INDICE, -1)

    companion object {
        /** Argumento de navegación: posición del alimento a editar, o -1 para uno nuevo. */
        const val ARG_INDICE = "indice"
    }
}
