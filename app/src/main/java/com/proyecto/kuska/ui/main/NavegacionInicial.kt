package com.proyecto.kuska.ui.main

import androidx.annotation.IdRes
import androidx.annotation.MenuRes
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Usuario

/** Destino inicial y menú de la barra inferior según la sesión. [menu] null: sin barra (login). */
data class NavegacionInicial(
    @param:IdRes val inicio: Int,
    @param:MenuRes val menu: Int?,
) {
    companion object {
        /** [rol] null = sin sesión. Un rol que la app no atiende (p. ej. administrador) va al login. */
        fun para(rol: String?): NavegacionInicial = when (rol) {
            Usuario.ROL_BENEFICIARIO -> NavegacionInicial(R.id.inicioBeneficiarioFragment, R.menu.menu_beneficiario)
            Usuario.ROL_DONANTE -> NavegacionInicial(R.id.inicioDonanteFragment, R.menu.menu_donante)
            else -> NavegacionInicial(R.id.loginFragment, null)
        }
    }
}
