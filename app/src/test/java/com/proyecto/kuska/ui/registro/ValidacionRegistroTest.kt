package com.proyecto.kuska.ui.registro

import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.RegistroRequest
import com.proyecto.kuska.data.api.modelos.Usuario
import com.proyecto.kuska.ui.comun.TextoUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidacionRegistroTest {

    private val beneficiario = DatosRegistro(
        rol = Usuario.ROL_BENEFICIARIO,
        nombres = "Laura",
        apellidos = "Gómez",
        tipoDocumento = "CC",
        numeroDocumento = "1012345678",
        telefono = "3001234567",
        correo = "laura@correo.com",
        contrasena = "Clave.Segura2026",
        confirmarContrasena = "Clave.Segura2026",
    )

    private val negocio = beneficiario.copy(
        rol = Usuario.ROL_DONANTE,
        tipoDonante = RegistroRequest.TIPO_DONANTE_NEGOCIO,
        nombreNegocio = "Tienda La Esquina",
        nit = "900123456-7",
    )

    private fun errores(datos: DatosRegistro) = ValidacionRegistro.validar(datos)

    private fun recurso(id: Int) = TextoUi.Recurso(id)

    @Test
    fun beneficiarioValido() {
        assertTrue(errores(beneficiario).isEmpty())
    }

    @Test
    fun direccionEsOpcional() {
        assertTrue(errores(beneficiario.copy(direccion = "")).isEmpty())
        assertTrue(errores(beneficiario.copy(direccion = "Calle 1 Sur # 5-20")).isEmpty())
    }

    @Test
    fun beneficiarioConCamposVacios() {
        val resultado = errores(
            beneficiario.copy(
                nombres = " ",
                apellidos = "",
                tipoDocumento = null,
                numeroDocumento = "",
                telefono = "",
            )
        )
        val obligatorio = recurso(R.string.error_campo_obligatorio)
        assertEquals(obligatorio, resultado[CampoRegistro.NOMBRES])
        assertEquals(obligatorio, resultado[CampoRegistro.APELLIDOS])
        assertEquals(recurso(R.string.error_tipo_documento), resultado[CampoRegistro.TIPO_DOCUMENTO])
        assertEquals(obligatorio, resultado[CampoRegistro.NUMERO_DOCUMENTO])
        assertEquals(obligatorio, resultado[CampoRegistro.TELEFONO])
        assertEquals(5, resultado.size)
    }

    @Test
    fun beneficiarioIgnoraDatosDeDonante() {
        // Si eligió Donante, llenó datos de negocio y luego cambió a Beneficiario, no se validan.
        assertTrue(errores(beneficiario.copy(tipoDonante = RegistroRequest.TIPO_DONANTE_NEGOCIO)).isEmpty())
    }

    @Test
    fun donanteSinTipo() {
        val resultado = errores(negocio.copy(tipoDonante = null))
        assertEquals(recurso(R.string.error_tipo_donante), resultado[CampoRegistro.TIPO_DONANTE])
        assertEquals(1, resultado.size)
    }

    @Test
    fun donanteNegocioValido() {
        assertTrue(errores(negocio).isEmpty())
    }

    @Test
    fun donanteNegocioSinNombreNiNit() {
        val resultado = errores(negocio.copy(nombreNegocio = "", nit = "  "))
        assertEquals(recurso(R.string.error_campo_obligatorio), resultado[CampoRegistro.NOMBRE_NEGOCIO])
        assertEquals(recurso(R.string.error_campo_obligatorio), resultado[CampoRegistro.NIT])
        assertEquals(2, resultado.size)
    }

    @Test
    fun donantePersonaNoPideDatosDeNegocio() {
        val persona = negocio.copy(
            tipoDonante = RegistroRequest.TIPO_DONANTE_PERSONA,
            nombreNegocio = "",
            nit = "",
        )
        assertTrue(errores(persona).isEmpty())
    }

    @Test
    fun correoVacioEInvalido() {
        assertEquals(recurso(R.string.error_correo_vacio), errores(beneficiario.copy(correo = ""))[CampoRegistro.CORREO])
        assertEquals(
            recurso(R.string.error_correo_invalido),
            errores(beneficiario.copy(correo = "laura@correo"))[CampoRegistro.CORREO],
        )
    }

    @Test
    fun contrasenaYConfirmacion() {
        assertEquals(
            recurso(R.string.error_contrasena_vacia),
            errores(beneficiario.copy(contrasena = "", confirmarContrasena = ""))[CampoRegistro.CONTRASENA],
        )
        assertEquals(
            recurso(R.string.error_confirmar_vacia),
            errores(beneficiario.copy(confirmarContrasena = ""))[CampoRegistro.CONFIRMAR_CONTRASENA],
        )
        assertEquals(
            recurso(R.string.error_contrasenas_distintas),
            errores(beneficiario.copy(confirmarContrasena = "Otra.Clave"))[CampoRegistro.CONFIRMAR_CONTRASENA],
        )
    }

    @Test
    fun contrasenaSinReglasLocales() {
        // Las reglas (largo mínimo, etc.) las aplica el servidor.
        val corta = beneficiario.copy(contrasena = "1", confirmarContrasena = "1")
        assertTrue(errores(corta).isEmpty())
    }

    @Test
    fun largosMaximos() {
        val resultado = errores(
            negocio.copy(
                nombres = "a".repeat(81),
                apellidos = "a".repeat(80),
                numeroDocumento = "1".repeat(21),
                telefono = "3".repeat(21),
                direccion = "d".repeat(201),
                nombreNegocio = "n".repeat(121),
                nit = "9".repeat(21),
                correo = "a".repeat(110) + "@correo.com",
                contrasena = "c".repeat(129),
                confirmarContrasena = "c".repeat(129),
            )
        )
        fun largo(maximo: Int) = TextoUi.Recurso(R.string.error_largo_maximo, listOf(maximo))
        assertEquals(largo(80), resultado[CampoRegistro.NOMBRES])
        assertNull(resultado[CampoRegistro.APELLIDOS])
        assertEquals(largo(20), resultado[CampoRegistro.NUMERO_DOCUMENTO])
        assertEquals(largo(20), resultado[CampoRegistro.TELEFONO])
        assertEquals(largo(200), resultado[CampoRegistro.DIRECCION])
        assertEquals(largo(120), resultado[CampoRegistro.NOMBRE_NEGOCIO])
        assertEquals(largo(20), resultado[CampoRegistro.NIT])
        assertEquals(largo(120), resultado[CampoRegistro.CORREO])
        assertEquals(largo(128), resultado[CampoRegistro.CONTRASENA])
    }

    @Test
    fun solicitudSoloEnviaLoQueAplica() {
        val solicitudBeneficiario = beneficiario
            .copy(tipoDonante = RegistroRequest.TIPO_DONANTE_NEGOCIO, nombreNegocio = "X", direccion = "  ")
            .aSolicitud("482913")
        assertNull(solicitudBeneficiario.tipoDonante)
        assertNull(solicitudBeneficiario.nombreNegocio)
        assertNull(solicitudBeneficiario.nit)
        assertNull(solicitudBeneficiario.direccion)

        val solicitudPersona = negocio.copy(tipoDonante = RegistroRequest.TIPO_DONANTE_PERSONA).aSolicitud("482913")
        assertEquals(RegistroRequest.TIPO_DONANTE_PERSONA, solicitudPersona.tipoDonante)
        assertNull(solicitudPersona.nombreNegocio)
        assertNull(solicitudPersona.nit)

        val solicitudNegocio = negocio.copy(correo = "  tienda@correo.com ").aSolicitud("105377")
        assertEquals("tienda@correo.com", solicitudNegocio.correo)
        assertEquals("Tienda La Esquina", solicitudNegocio.nombreNegocio)
        assertEquals("900123456-7", solicitudNegocio.nit)
        assertEquals("105377", solicitudNegocio.codigo)
    }
}
