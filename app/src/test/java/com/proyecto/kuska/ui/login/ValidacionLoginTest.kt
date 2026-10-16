package com.proyecto.kuska.ui.login

import com.proyecto.kuska.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidacionLoginTest {

    @Test
    fun correoVacio() {
        assertEquals(R.string.error_correo_vacio, ValidacionLogin.validarCorreo(""))
        assertEquals(R.string.error_correo_vacio, ValidacionLogin.validarCorreo("   "))
    }

    @Test
    fun correoConFormatoInvalido() {
        listOf("laura", "laura@", "@correo.com", "laura@correo", "laura @correo.com", "laura@@correo.com")
            .forEach { assertEquals(it, R.string.error_correo_invalido, ValidacionLogin.validarCorreo(it)) }
    }

    @Test
    fun correoValido() {
        assertNull(ValidacionLogin.validarCorreo("laura@correo.com"))
        assertNull(ValidacionLogin.validarCorreo("  laura.gomez+kumora@mi-correo.com.co  "))
    }

    @Test
    fun contrasenaVacia() {
        assertEquals(R.string.error_contrasena_vacia, ValidacionLogin.validarContrasena(""))
    }

    @Test
    fun contrasenaConTexto() {
        // No se recorta: los espacios pueden ser parte de la contraseña.
        assertNull(ValidacionLogin.validarContrasena(" "))
        assertNull(ValidacionLogin.validarContrasena("Clave.Segura2026"))
    }
}
