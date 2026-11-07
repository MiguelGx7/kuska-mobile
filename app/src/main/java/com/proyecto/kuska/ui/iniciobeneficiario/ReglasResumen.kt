package com.proyecto.kuska.ui.iniciobeneficiario

import androidx.annotation.StringRes
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.DashboardBeneficiario
import com.proyecto.kuska.data.api.modelos.MiSolicitud
import com.proyecto.kuska.data.api.modelos.ProximaRecogida

/** Reglas del resumen del inicio del beneficiario (HU-121). */
object ReglasResumen {

    /** Cuántos pedidos más puede enviar: nunca menos de 0. */
    fun cupos(resumen: DashboardBeneficiario): Int = resumen.cuposDisponibles.coerceAtLeast(0)

    /** Llegó al límite de solicitudes activas: aviso naranja. */
    fun enLimite(resumen: DashboardBeneficiario): Boolean = cupos(resumen) == 0

    /** "Recoge antes del …" para Recoger; "Fecha límite: …" para Domicilio (lo lleva el donante). */
    @StringRes
    fun etiquetaFecha(recogida: ProximaRecogida): Int =
        if (esDomicilio(recogida)) R.string.resumen_fecha_limite else R.string.solicitud_recoger_antes

    /** "Tu próxima recogida" o, a domicilio, "Tu próxima entrega". */
    @StringRes
    fun titulo(recogida: ProximaRecogida): Int =
        if (esDomicilio(recogida)) R.string.resumen_proxima_entrega else R.string.resumen_proxima_recogida

    private fun esDomicilio(recogida: ProximaRecogida) = recogida.modalidadEntrega == MiSolicitud.MODALIDAD_DOMICILIO
}
