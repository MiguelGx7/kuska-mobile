package com.proyecto.kuska.util

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Fechas de la API en dd/MM/yyyy. Sin java.time (minSdk 24 no lo trae sin desugaring).
 * - Fechas (yyyy-MM-dd, como fecha_vencimiento): solo se reordenan; son un día del calendario, sin zona.
 * - Fechas con hora (ISO-8601 con zona, como fecha_registro): se pasan a la zona de Bogotá.
 */
object FormatoFecha {

    val ZONA_BOGOTA: TimeZone = TimeZone.getTimeZone("America/Bogota")

    private val soloFecha = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

    /** Fecha y hora, fracción de segundos opcional (de cualquier largo) y zona Z, ±hh:mm, ±hhmm o sin zona. */
    private val fechaHora = Regex("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2})?)(?:\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?$")

    /** "2027-01-31" → "31/01/2027". Null si no tiene ese formato. */
    fun fecha(texto: String?): String? {
        val (anio, mes, dia) = soloFecha.matchEntire(texto?.trim().orEmpty())?.destructured ?: return null
        return "$dia/$mes/$anio"
    }

    /** "2026-10-17T00:30:00.123456Z" → "16/10/2026" (hora de Bogotá). Sin zona se toma como hora de Bogotá. */
    fun fechaDeFechaHora(texto: String?): String? {
        val milis = milisDeFechaHora(texto) ?: return null
        return SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = ZONA_BOGOTA }.format(milis)
    }

    /** Milisegundos desde 1970 de una fecha ISO-8601 con hora, o null si no se puede leer. */
    internal fun milisDeFechaHora(texto: String?): Long? {
        val coincidencia = fechaHora.matchEntire(texto?.trim().orEmpty()) ?: return null
        val (base, zona) = coincidencia.destructured
        val conSegundos = if (base.length == 16) "$base:00" else base
        val formato = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            isLenient = false
            timeZone = if (zona.isEmpty()) ZONA_BOGOTA else TimeZone.getTimeZone("UTC")
        }
        val milis = try {
            formato.parse(conSegundos)?.time
        } catch (_: ParseException) {
            null
        } ?: return null
        return milis - desfaseMilis(zona)
    }

    /** "Z" o "" → 0; "-05:00" → -5 h; "+0530" → +5 h 30 min. */
    private fun desfaseMilis(zona: String): Long {
        if (zona.isEmpty() || zona == "Z") return 0
        val signo = if (zona[0] == '-') -1 else 1
        val digitos = zona.substring(1).replace(":", "")
        val horas = digitos.substring(0, 2).toLong()
        val minutos = digitos.substring(2, 4).toLong()
        return signo * (horas * 60 + minutos) * 60_000
    }
}
