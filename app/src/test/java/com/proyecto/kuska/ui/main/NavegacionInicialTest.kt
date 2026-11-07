package com.proyecto.kuska.ui.main

import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Usuario
import org.junit.Assert.assertEquals
import org.junit.Test

class NavegacionInicialTest {

    @Test
    fun beneficiarioVaASuInicioConSuMenu() {
        assertEquals(
            NavegacionInicial(R.id.inicioBeneficiarioFragment, R.menu.menu_beneficiario),
            NavegacionInicial.para(Usuario.ROL_BENEFICIARIO),
        )
    }

    @Test
    fun donanteVaASuInicioConSuMenu() {
        assertEquals(
            NavegacionInicial(R.id.inicioDonanteFragment, R.menu.menu_donante),
            NavegacionInicial.para(Usuario.ROL_DONANTE),
        )
    }

    @Test
    fun sinSesionOConOtroRolVaAlLoginSinBarra() {
        val login = NavegacionInicial(R.id.loginFragment, null)
        assertEquals(login, NavegacionInicial.para(null))
        assertEquals(login, NavegacionInicial.para(Usuario.ROL_ADMINISTRADOR))
        assertEquals(login, NavegacionInicial.para("Otro"))
    }
}
