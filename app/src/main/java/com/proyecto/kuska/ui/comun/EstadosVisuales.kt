package com.proyecto.kuska.ui.comun

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.data.api.modelos.SolicitudRecibida

/** Las 4 familias de color del tema para las etiquetas de estado. */
enum class FamiliaColor(@param:ColorRes val fondo: Int, @param:ColorRes val texto: Int) {
    /** Espera una acción. */
    NARANJA(R.color.estado_naranja_fondo, R.color.estado_naranja_texto),
    VERDE(R.color.estado_verde_fondo, R.color.estado_verde_texto),
    ROJO(R.color.estado_rojo_fondo, R.color.estado_rojo_texto),
    GRIS(R.color.estado_gris_fondo, R.color.estado_gris_texto),
}

/** Color y texto de cada estado. Un estado desconocido queda en gris con el texto de la API. */
object EstadosVisuales {

    fun familiaDonacion(estado: String): FamiliaColor = when (estado) {
        Donacion.ESTADO_PENDIENTE -> FamiliaColor.NARANJA
        Donacion.ESTADO_VALIDADA -> FamiliaColor.VERDE
        Donacion.ESTADO_RECHAZADA -> FamiliaColor.ROJO
        else -> FamiliaColor.GRIS // Agotada, Finalizada
    }

    /** Pendiente y Aprobada aún esperan una acción (aprobar o entregar). */
    fun familiaSolicitud(estado: String): FamiliaColor = when (estado) {
        SolicitudRecibida.ESTADO_PENDIENTE, SolicitudRecibida.ESTADO_APROBADA -> FamiliaColor.NARANJA
        SolicitudRecibida.ESTADO_ENTREGADA -> FamiliaColor.VERDE
        SolicitudRecibida.ESTADO_RECHAZADA, SolicitudRecibida.ESTADO_NO_RECOGIDA -> FamiliaColor.ROJO
        else -> FamiliaColor.GRIS // Cancelada
    }

    /** Texto de un estado de donación o de solicitud (null si la app no lo conoce). */
    @StringRes
    fun etiqueta(estado: String): Int? = when (estado) {
        Donacion.ESTADO_PENDIENTE -> R.string.estado_pendiente
        Donacion.ESTADO_VALIDADA -> R.string.estado_validada
        Donacion.ESTADO_RECHAZADA -> R.string.estado_rechazada
        Donacion.ESTADO_AGOTADA -> R.string.estado_agotada
        Donacion.ESTADO_FINALIZADA -> R.string.estado_finalizada
        SolicitudRecibida.ESTADO_APROBADA -> R.string.estado_aprobada
        SolicitudRecibida.ESTADO_ENTREGADA -> R.string.estado_entregada
        SolicitudRecibida.ESTADO_NO_RECOGIDA -> R.string.estado_no_recogida
        SolicitudRecibida.ESTADO_CANCELADA -> R.string.estado_cancelada
        else -> null
    }
}

/** Etiqueta de estado (estilo Widget.Kumora.EtiquetaEstado): solo texto, con el color de su familia. */
fun TextView.mostrarEtiqueta(texto: String, familia: FamiliaColor) {
    this.text = texto
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, familia.fondo))
    setTextColor(ContextCompat.getColor(context, familia.texto))
}

fun TextView.mostrarEstadoDonacion(estado: String) = mostrarEtiqueta(
    EstadosVisuales.etiqueta(estado)?.let(context::getString) ?: estado,
    EstadosVisuales.familiaDonacion(estado),
)

fun TextView.mostrarEstadoSolicitud(estado: String) = mostrarEtiqueta(
    EstadosVisuales.etiqueta(estado)?.let(context::getString) ?: estado,
    EstadosVisuales.familiaSolicitud(estado),
)
