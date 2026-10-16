package com.proyecto.kuska.ui.registro

import com.proyecto.kuska.data.api.modelos.RegistroRequest
import com.proyecto.kuska.data.api.modelos.Usuario

/**
 * Campos del formulario de registro. [nombreApi] es la clave que usa auth/registro/ en sus errores
 * (null si el campo solo existe en la app). [largoMaximo] sale del schema.
 */
enum class CampoRegistro(val nombreApi: String?, val largoMaximo: Int? = null) {
    TIPO_DONANTE("tipo_donante"),
    NOMBRE_NEGOCIO("nombre_negocio", 120),
    NIT("nit", 20),
    NOMBRES("nombres", 80),
    APELLIDOS("apellidos", 80),
    TIPO_DOCUMENTO("tipo_documento"),
    NUMERO_DOCUMENTO("numero_documento", 20),
    TELEFONO("telefono", 20),
    DIRECCION("direccion", 200),
    CORREO("correo", 120),
    CONTRASENA("contrasena", 128),
    CONFIRMAR_CONTRASENA(null);

    companion object {
        fun desdeApi(nombre: String): CampoRegistro? = entries.firstOrNull { it.nombreApi == nombre }
    }
}

/** Lo que el usuario lleva escrito o elegido. Se conserva al ir y volver entre pasos. */
data class DatosRegistro(
    val rol: String? = null,
    val tipoDonante: String? = null,
    val nombreNegocio: String = "",
    val nit: String = "",
    val nombres: String = "",
    val apellidos: String = "",
    val tipoDocumento: String? = null,
    val numeroDocumento: String = "",
    val telefono: String = "",
    val direccion: String = "",
    val correo: String = "",
    val contrasena: String = "",
    val confirmarContrasena: String = "",
) {
    val esDonante get() = rol == Usuario.ROL_DONANTE
    val esNegocio get() = esDonante && tipoDonante == RegistroRequest.TIPO_DONANTE_NEGOCIO

    /** Valor de un campo de texto (los de selección devuelven null). */
    fun valor(campo: CampoRegistro): String? = when (campo) {
        CampoRegistro.NOMBRE_NEGOCIO -> nombreNegocio
        CampoRegistro.NIT -> nit
        CampoRegistro.NOMBRES -> nombres
        CampoRegistro.APELLIDOS -> apellidos
        CampoRegistro.NUMERO_DOCUMENTO -> numeroDocumento
        CampoRegistro.TELEFONO -> telefono
        CampoRegistro.DIRECCION -> direccion
        CampoRegistro.CORREO -> correo
        CampoRegistro.CONTRASENA -> contrasena
        CampoRegistro.CONFIRMAR_CONTRASENA -> confirmarContrasena
        CampoRegistro.TIPO_DONANTE, CampoRegistro.TIPO_DOCUMENTO -> null
    }

    /** Copia con un campo de texto cambiado (los de selección se cambian con copy). */
    fun con(campo: CampoRegistro, valor: String): DatosRegistro = when (campo) {
        CampoRegistro.NOMBRE_NEGOCIO -> copy(nombreNegocio = valor)
        CampoRegistro.NIT -> copy(nit = valor)
        CampoRegistro.NOMBRES -> copy(nombres = valor)
        CampoRegistro.APELLIDOS -> copy(apellidos = valor)
        CampoRegistro.NUMERO_DOCUMENTO -> copy(numeroDocumento = valor)
        CampoRegistro.TELEFONO -> copy(telefono = valor)
        CampoRegistro.DIRECCION -> copy(direccion = valor)
        CampoRegistro.CORREO -> copy(correo = valor)
        CampoRegistro.CONTRASENA -> copy(contrasena = valor)
        CampoRegistro.CONFIRMAR_CONTRASENA -> copy(confirmarContrasena = valor)
        CampoRegistro.TIPO_DONANTE, CampoRegistro.TIPO_DOCUMENTO -> this
    }

    /**
     * Cuerpo para auth/registro/. Llamar solo con datos ya validados.
     * Los datos de donante y negocio solo se envían si aplican (RN-02); la confirmación nunca se envía.
     */
    fun aSolicitud(codigo: String) = RegistroRequest(
        correo = correo.trim(),
        contrasena = contrasena,
        rol = requireNotNull(rol),
        nombres = nombres.trim(),
        apellidos = apellidos.trim(),
        tipoDocumento = requireNotNull(tipoDocumento),
        numeroDocumento = numeroDocumento.trim(),
        telefono = telefono.trim(),
        direccion = direccion.trim().ifEmpty { null },
        tipoDonante = if (esDonante) tipoDonante else null,
        nombreNegocio = if (esNegocio) nombreNegocio.trim() else null,
        nit = if (esNegocio) nit.trim() else null,
        codigo = codigo,
    )
}
