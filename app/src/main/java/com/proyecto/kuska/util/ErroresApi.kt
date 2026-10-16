package com.proyecto.kuska.util

import android.content.Context
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.proyecto.kuska.R
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Saca el mensaje de un cuerpo de error de la API. Formatos soportados:
 * - {"detail": "mensaje"}
 * - {"campo": ["mensaje"]} o {"campo": "mensaje"}
 * - ["mensaje"]
 * Devuelve null si el cuerpo está vacío o no tiene ninguno de esos formatos.
 */
fun extraerMensajeError(cuerpo: String?): String? {
    if (cuerpo.isNullOrBlank()) return null
    val json = try {
        JsonParser.parseString(cuerpo)
    } catch (_: Exception) {
        return null
    }
    return primerMensaje(json)
}

private fun primerMensaje(json: JsonElement): String? = when {
    json.isJsonPrimitive -> json.asString.takeIf { it.isNotBlank() }
    json.isJsonArray -> json.asJsonArray.firstNotNullOfOrNull { primerMensaje(it) }
    json.isJsonObject -> {
        val objeto = json.asJsonObject
        objeto.get("detail")?.let { primerMensaje(it) }
            ?: objeto.entrySet().firstNotNullOfOrNull { (_, valor) -> primerMensaje(valor) }
    }
    else -> null
}

/** Mensaje para una respuesta HTTP no exitosa. Prioriza el mensaje que manda la API. */
fun mensajeDeError(context: Context, codigo: Int, cuerpo: String?): String =
    extraerMensajeError(cuerpo) ?: context.getString(
        when {
            codigo == 401 -> R.string.sesion_expirada
            codigo >= 500 -> R.string.error_servidor
            else -> R.string.error_desconocido
        }
    )

fun mensajeDeError(context: Context, respuesta: Response<*>): String =
    mensajeDeError(context, respuesta.code(), respuesta.errorBody()?.string())

/** Mensaje para una excepción lanzada al llamar a la API. */
fun mensajeDeExcepcion(context: Context, error: Throwable): String = when (error) {
    is HttpException -> {
        val respuesta = error.response()
        mensajeDeError(context, error.code(), respuesta?.errorBody()?.string())
    }
    is IOException -> context.getString(R.string.error_sin_conexion)
    else -> context.getString(R.string.error_desconocido)
}
