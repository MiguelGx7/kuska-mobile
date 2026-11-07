package com.proyecto.kuska.ui.comun

import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import com.proyecto.kuska.R

/**
 * Dibujo de la hormiga en un estado de pantalla completa (sistema de diseño, sección 6). Es decorativo: el texto
 * ya explica el estado. Los vacíos por filtro o búsqueda y los errores pequeños no llevan dibujo.
 */
enum class IlustracionEstado(
    @param:DrawableRes val dibujo: Int,
    @param:DimenRes val ancho: Int,
    @param:DimenRes val alto: Int,
) {
    /** Sin datos (lista vacía, cuenta nueva). */
    PLATO_VACIO(R.drawable.hormiga_vacia, R.dimen.ilustracion_vacia_ancho, R.dimen.ilustracion_vacia_alto),

    /** Sin conexión. */
    ERROR_RED(R.drawable.hormiga_error_red, R.dimen.ilustracion_error_ancho, R.dimen.ilustracion_error_alto),

    /** Cuenta bloqueada o sin permiso. */
    ERROR_403(R.drawable.hormiga_error_403, R.dimen.ilustracion_error_ancho, R.dimen.ilustracion_error_alto),

    /** Error del servidor u otro. */
    ERROR_500(R.drawable.hormiga_error_500, R.dimen.ilustracion_error_ancho, R.dimen.ilustracion_error_alto);

    companion object {
        /**
         * Variante según el código de la respuesta ([com.proyecto.kuska.util.Resultado.Error.codigo]): sin código
         * es que no hubo respuesta (sin conexión); 403 es cuenta bloqueada o sin permiso; el resto, "500".
         */
        fun deError(codigo: Int?): IlustracionEstado = when (codigo) {
            null -> ERROR_RED
            403 -> ERROR_403
            else -> ERROR_500
        }
    }
}
