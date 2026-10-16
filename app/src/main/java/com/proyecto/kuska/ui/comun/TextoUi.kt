package com.proyecto.kuska.ui.comun

import android.content.Context
import androidx.annotation.StringRes

/** Texto para mostrar en pantalla: un recurso local (validación) o un mensaje que mandó la API. */
sealed interface TextoUi {
    data class Recurso(@param:StringRes val id: Int, val argumentos: List<Any> = emptyList()) : TextoUi
    data class Texto(val texto: String) : TextoUi
}

fun TextoUi.resolver(context: Context): String = when (this) {
    is TextoUi.Recurso -> context.getString(id, *argumentos.toTypedArray())
    is TextoUi.Texto -> texto
}
