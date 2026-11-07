package com.proyecto.kuska.ui.editarperfil

import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Perfil
import com.proyecto.kuska.ui.comun.TextoUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CambiosPerfilTest {

    private val original = Perfil(
        id = 1, correo = "laura@correo.com", rol = "Donante", nombres = "Laura", apellidos = "Gómez",
        tipoDocumento = "CC", numeroDocumento = "1020304050", telefono = "3001234567", direccion = "Calle 1 # 2-3",
    )

    private fun json(valores: ValoresPerfil) = CambiosPerfil.aJson(CambiosPerfil.construir(original, valores))

    @Test
    fun sinCambiosElCuerpoQuedaVacio() {
        assertEquals("{}", json(ValoresPerfil.de(original)))
    }

    @Test
    fun espaciosEnLosExtremosNoCuentanComoCambio() {
        assertEquals("{}", json(ValoresPerfil(" Laura ", "Gómez  ", " 3001234567", "Calle 1 # 2-3 ")))
    }

    @Test
    fun soloViajaLoQueCambio() {
        val valores = ValoresPerfil.de(original).copy(telefono = " 3109998877 ", direccion = "Carrera 9 # 10-11")
        assertEquals("""{"telefono":"3109998877","direccion":"Carrera 9 # 10-11"}""", json(valores))
    }

    @Test
    fun direccionVaciadaViajaComoNull() {
        assertEquals("""{"direccion":null}""", json(ValoresPerfil.de(original).copy(direccion = "   ")))
    }

    @Test
    fun direccionNullYVaciaSonLoMismo() {
        val sinDireccion = original.copy(direccion = null)
        assertEquals("{}", CambiosPerfil.aJson(CambiosPerfil.construir(sinDireccion, ValoresPerfil.de(sinDireccion))))
    }

    @Test
    fun obligatoriosYLargosMaximos() {
        val errores = CambiosPerfil.validar(ValoresPerfil(nombres = " ", apellidos = "a".repeat(81), telefono = "", direccion = "d".repeat(201)))
        assertEquals(TextoUi.Recurso(R.string.error_campo_obligatorio), errores[CampoPerfil.NOMBRES])
        assertEquals(TextoUi.Recurso(R.string.error_largo_maximo, listOf(80)), errores[CampoPerfil.APELLIDOS])
        assertEquals(TextoUi.Recurso(R.string.error_campo_obligatorio), errores[CampoPerfil.TELEFONO])
        assertEquals(TextoUi.Recurso(R.string.error_largo_maximo, listOf(200)), errores[CampoPerfil.DIRECCION])
    }

    @Test
    fun direccionVaciaEsValida() {
        assertTrue(CambiosPerfil.validar(ValoresPerfil.de(original).copy(direccion = "")).isEmpty())
    }

    @Test
    fun clavesDeLaApi() {
        assertEquals(CampoPerfil.TELEFONO, CambiosPerfil.CAMPOS_API["telefono"])
        assertEquals(4, CambiosPerfil.CAMPOS_API.size)
    }
}
