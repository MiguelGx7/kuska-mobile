package com.proyecto.kuska.ui.perfil

import androidx.annotation.StringRes
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Perfil
import com.proyecto.kuska.data.api.modelos.Usuario
import com.proyecto.kuska.ui.comun.TextoUi
import com.proyecto.kuska.util.FormatoFecha

/** Un par etiqueta–valor de solo lectura. */
data class DatoPerfil(@param:StringRes val etiqueta: Int, val valor: TextoUi)

/** Qué datos del perfil se muestran (HU-012). Nunca la contraseña ni el estado de la cuenta. */
object DatosPerfil {

    /** Negocio y NIT solo para un donante tipo Negocio. */
    fun esNegocio(perfil: Perfil): Boolean =
        perfil.rol == Usuario.ROL_DONANTE && perfil.tipoDonante == Perfil.TIPO_NEGOCIO

    /** Pantalla Perfil: todo. */
    fun todos(perfil: Perfil): List<DatoPerfil> = buildList {
        add(DatoPerfil(R.string.registro_nombres, texto(perfil.nombres)))
        add(DatoPerfil(R.string.registro_apellidos, texto(perfil.apellidos)))
        addAll(soloLectura(perfil))
        add(DatoPerfil(R.string.registro_telefono, texto(perfil.telefono)))
        add(DatoPerfil(R.string.perfil_direccion, texto(perfil.direccion)))
        add(DatoPerfil(R.string.perfil_fecha_registro, texto(FormatoFecha.fechaDeFechaHora(perfil.fechaRegistro))))
    }

    /** Editar perfil: lo que se ve pero no se edita (RN-04a). */
    fun soloLectura(perfil: Perfil): List<DatoPerfil> = buildList {
        add(DatoPerfil(R.string.login_etiqueta_correo, texto(perfil.correo)))
        add(DatoPerfil(R.string.perfil_rol, texto(perfil.rol)))
        if (esNegocio(perfil)) {
            add(DatoPerfil(R.string.perfil_nombre_negocio, texto(perfil.nombreNegocio)))
            add(DatoPerfil(R.string.registro_nit, texto(perfil.nit)))
        }
        add(DatoPerfil(R.string.registro_tipo_documento, tipoDocumento(perfil.tipoDocumento)))
        add(DatoPerfil(R.string.registro_numero_documento, texto(perfil.numeroDocumento)))
    }

    /** "CC" → "Cédula de ciudadanía". Uno desconocido se muestra tal cual. */
    fun tipoDocumento(tipo: String?): TextoUi = when (tipo) {
        Perfil.DOCUMENTO_CC -> TextoUi.Recurso(R.string.tipo_documento_cc)
        Perfil.DOCUMENTO_CE -> TextoUi.Recurso(R.string.tipo_documento_ce)
        Perfil.DOCUMENTO_PAS -> TextoUi.Recurso(R.string.tipo_documento_pas)
        Perfil.DOCUMENTO_PPT -> TextoUi.Recurso(R.string.tipo_documento_ppt)
        else -> texto(tipo)
    }

    private fun texto(valor: String?): TextoUi =
        valor?.trim()?.takeIf { it.isNotEmpty() }?.let { TextoUi.Texto(it) } ?: TextoUi.Recurso(R.string.perfil_sin_dato)
}
