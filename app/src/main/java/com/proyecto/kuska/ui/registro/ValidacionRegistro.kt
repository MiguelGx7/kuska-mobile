package com.proyecto.kuska.ui.registro

import com.proyecto.kuska.R
import com.proyecto.kuska.ui.comun.TextoUi
import com.proyecto.kuska.ui.comun.validarConfirmacion
import com.proyecto.kuska.ui.comun.validarContrasenaNueva
import com.proyecto.kuska.util.validarCorreo

/**
 * Validación local del paso 2 (RN-01, RN-02 y largos del schema). Las reglas de la contraseña
 * las aplica el servidor: aquí solo se revisa que exista, su largo y que la confirmación coincida.
 */
object ValidacionRegistro {

    fun validar(datos: DatosRegistro): Map<CampoRegistro, TextoUi> {
        val errores = linkedMapOf<CampoRegistro, TextoUi>()

        fun largo(campo: CampoRegistro, valor: String) {
            val maximo = campo.largoMaximo ?: return
            if (valor.length > maximo) {
                errores[campo] = TextoUi.Recurso(R.string.error_largo_maximo, listOf(maximo))
            }
        }

        fun obligatorio(campo: CampoRegistro, valor: String) {
            val limpio = valor.trim()
            if (limpio.isEmpty()) {
                errores[campo] = TextoUi.Recurso(R.string.error_campo_obligatorio)
            } else {
                largo(campo, limpio)
            }
        }

        if (datos.esDonante && datos.tipoDonante == null) {
            errores[CampoRegistro.TIPO_DONANTE] = TextoUi.Recurso(R.string.error_tipo_donante)
        }
        if (datos.esNegocio) {
            obligatorio(CampoRegistro.NOMBRE_NEGOCIO, datos.nombreNegocio)
            obligatorio(CampoRegistro.NIT, datos.nit)
        }

        obligatorio(CampoRegistro.NOMBRES, datos.nombres)
        obligatorio(CampoRegistro.APELLIDOS, datos.apellidos)
        if (datos.tipoDocumento == null) {
            errores[CampoRegistro.TIPO_DOCUMENTO] = TextoUi.Recurso(R.string.error_tipo_documento)
        }
        obligatorio(CampoRegistro.NUMERO_DOCUMENTO, datos.numeroDocumento)
        obligatorio(CampoRegistro.TELEFONO, datos.telefono)
        largo(CampoRegistro.DIRECCION, datos.direccion.trim())

        val errorCorreo = validarCorreo(datos.correo)
        if (errorCorreo != null) {
            errores[CampoRegistro.CORREO] = TextoUi.Recurso(errorCorreo)
        } else {
            largo(CampoRegistro.CORREO, datos.correo.trim())
        }

        validarContrasenaNueva(datos.contrasena)?.let { errores[CampoRegistro.CONTRASENA] = it }
        validarConfirmacion(datos.contrasena, datos.confirmarContrasena)
            ?.let { errores[CampoRegistro.CONFIRMAR_CONTRASENA] = it }

        return errores
    }
}
