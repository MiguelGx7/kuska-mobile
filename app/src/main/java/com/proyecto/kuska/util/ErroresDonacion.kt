package com.proyecto.kuska.util

import com.google.gson.JsonElement
import com.google.gson.JsonParser

/** Clave para los errores que no son de un campo ("detail", "non_field_errors"). */
const val CLAVE_ERROR_GENERAL = "non_field_errors"

/** Prefijo de la clave de un alimento: "alimentos.0", "alimentos.1"... */
const val PREFIJO_ERROR_ALIMENTO = "alimentos."

/**
 * Errores de POST donaciones/ con el primer mensaje de cada clave. El schema no documenta la forma de
 * los errores por alimento; se asume la de DRF:
 * - {"titulo": ["mensaje"]} → "titulo"
 * - {"alimentos": [{}, {"fecha_vencimiento": ["mensaje"]}]} → "alimentos.1"
 *   (también {"alimentos": {"1": {...}}})
 * - {"alimentos": ["mensaje"]} → "alimentos" (error de la lista, no de un alimento)
 * - {"foto_1": ["mensaje"]} → "foto_1"
 * - {"detail": "..."} o {"non_field_errors": [...]} → [CLAVE_ERROR_GENERAL]
 * - Si todo viene dentro de {"datos": {...}}, se lee lo de adentro.
 * Para una lista ["mensaje"] o un cuerpo que no es JSON devuelve un mapa vacío.
 */
fun extraerErroresDonacion(cuerpo: String?): Map<String, String> {
    if (cuerpo.isNullOrBlank()) return emptyMap()
    val json = try {
        JsonParser.parseString(cuerpo)
    } catch (_: Exception) {
        return emptyMap()
    }
    if (!json.isJsonObject) return emptyMap()
    val objeto = json.asJsonObject
    val datos = objeto.get("datos")
    val raiz = if (objeto.size() == 1 && datos != null && datos.isJsonObject) datos.asJsonObject else objeto

    val errores = linkedMapOf<String, String>()
    for ((clave, valor) in raiz.entrySet()) {
        when (clave) {
            "detail", CLAVE_ERROR_GENERAL ->
                primerMensaje(valor)?.let { errores.putIfAbsent(CLAVE_ERROR_GENERAL, it) }
            "alimentos" -> agregarErroresAlimentos(valor, errores)
            else -> primerMensaje(valor)?.let { errores[clave] = it }
        }
    }
    return errores
}

private fun agregarErroresAlimentos(valor: JsonElement, errores: MutableMap<String, String>) {
    when {
        // Lista con un error por alimento (vacío {} si ese alimento está bien).
        valor.isJsonArray && valor.asJsonArray.any { it.isJsonObject } ->
            valor.asJsonArray.forEachIndexed { indice, error ->
                primerMensaje(error)?.let { errores["$PREFIJO_ERROR_ALIMENTO$indice"] = it }
            }
        // {"1": {...}}: solo los alimentos con error, por índice.
        valor.isJsonObject && valor.asJsonObject.keySet().all { it.toIntOrNull() != null } ->
            valor.asJsonObject.entrySet().forEach { (indice, error) ->
                primerMensaje(error)?.let { errores["$PREFIJO_ERROR_ALIMENTO$indice"] = it }
            }
        else -> primerMensaje(valor)?.let { errores["alimentos"] = it }
    }
}

private fun primerMensaje(json: JsonElement): String? = when {
    json.isJsonPrimitive -> json.asString.takeIf { it.isNotBlank() }
    json.isJsonArray -> json.asJsonArray.firstNotNullOfOrNull { primerMensaje(it) }
    json.isJsonObject -> json.asJsonObject.entrySet().firstNotNullOfOrNull { (_, valor) -> primerMensaje(valor) }
    else -> null
}
