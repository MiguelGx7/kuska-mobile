package com.proyecto.kuska.ui.creardonacion

import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.ui.comun.TextoUi
import java.math.BigDecimal

/** Alimento tal como se edita en el formulario. La fecha es la medianoche UTC del día elegido (MaterialDatePicker). */
data class AlimentoBorrador(
    /** Ruta de la foto ya reducida, en cache/fotos/. */
    val rutaFoto: String? = null,
    val nombre: String = "",
    val descripcion: String = "",
    val categoriaId: Int? = null,
    val categoriaNombre: String? = null,
    val unidadMedida: String? = null,
    /** Texto tal como lo escribió el usuario (acepta coma o punto). */
    val cantidad: String = "",
    val fechaVencimiento: Long? = null,
)

enum class CampoAlimento { FOTO, NOMBRE, DESCRIPCION, CATEGORIA, UNIDAD, CANTIDAD, FECHA }

/** Validación local de un alimento (RN-10a, RN-11a). El margen de vencimiento (RN-11) lo valida el servidor. */
object ValidacionAlimento {

    /** Largos máximos de AlimentoNuevoRequest. */
    const val LARGO_NOMBRE = 100
    const val LARGO_DESCRIPCION = 255

    /** cantidad_total: ^-?\d{0,8}(?:\.\d{0,2})?$ → máximo 8 cifras enteras. */
    const val CIFRAS_ENTERAS = 8
    const val DECIMALES = 2

    private val formatoNumero = Regex("^\\d+([.,]\\d+)?$")

    /** [hoyUtc]: medianoche UTC de hoy (MaterialDatePicker.todayInUtcMilliseconds()). */
    fun validar(alimento: AlimentoBorrador, hoyUtc: Long): Map<CampoAlimento, TextoUi> = buildMap {
        if (alimento.rutaFoto == null) put(CampoAlimento.FOTO, TextoUi.Recurso(R.string.error_foto_obligatoria))
        validarTexto(alimento.nombre, LARGO_NOMBRE, obligatorio = true)?.let { put(CampoAlimento.NOMBRE, it) }
        validarTexto(alimento.descripcion, LARGO_DESCRIPCION, obligatorio = false)
            ?.let { put(CampoAlimento.DESCRIPCION, it) }
        if (alimento.categoriaId == null) put(CampoAlimento.CATEGORIA, TextoUi.Recurso(R.string.error_categoria))
        if (alimento.unidadMedida == null) put(CampoAlimento.UNIDAD, TextoUi.Recurso(R.string.error_unidad))
        validarCantidad(alimento.cantidad, alimento.unidadMedida)?.let { put(CampoAlimento.CANTIDAD, it) }
        validarFecha(alimento.fechaVencimiento, hoyUtc)?.let { put(CampoAlimento.FECHA, it) }
    }

    /** Cantidad > 0; entera para unidad y paquete, hasta 2 decimales en las demás (RN-10a). */
    fun validarCantidad(texto: String, unidad: String?): TextoUi? {
        val limpio = texto.trim()
        if (limpio.isEmpty()) return TextoUi.Recurso(R.string.error_cantidad_vacia)
        if (!formatoNumero.matches(limpio)) return TextoUi.Recurso(R.string.error_cantidad_invalida)
        val valor = BigDecimal(limpio.replace(',', '.')).stripTrailingZeros()
        return when {
            valor.signum() <= 0 -> TextoUi.Recurso(R.string.error_cantidad_mayor_cero)
            valor.precision() - valor.scale() > CIFRAS_ENTERAS ->
                TextoUi.Recurso(R.string.error_cantidad_maxima, listOf(CIFRAS_ENTERAS))
            unidad in Donacion.UNIDADES_ENTERAS && valor.scale() > 0 ->
                TextoUi.Recurso(R.string.error_cantidad_entera)
            valor.scale() > DECIMALES -> TextoUi.Recurso(R.string.error_cantidad_decimales, listOf(DECIMALES))
            else -> null
        }
    }

    /** La fecha debe ser posterior a hoy. */
    fun validarFecha(fecha: Long?, hoyUtc: Long): TextoUi? = when {
        fecha == null -> TextoUi.Recurso(R.string.error_fecha_vacia)
        fecha <= hoyUtc -> TextoUi.Recurso(R.string.error_fecha_pasada)
        else -> null
    }

    /** Cantidad para la API: con punto y sin ceros sobrantes ("2,50" → "2.5", "5.0" → "5"). Supone que es válida. */
    fun normalizarCantidad(texto: String): String =
        BigDecimal(texto.trim().replace(',', '.')).stripTrailingZeros().toPlainString()
}

internal fun validarTexto(texto: String, largoMaximo: Int, obligatorio: Boolean): TextoUi? {
    val limpio = texto.trim()
    return when {
        obligatorio && limpio.isEmpty() -> TextoUi.Recurso(R.string.error_campo_obligatorio)
        limpio.length > largoMaximo -> TextoUi.Recurso(R.string.error_largo_maximo, listOf(largoMaximo))
        else -> null
    }
}
