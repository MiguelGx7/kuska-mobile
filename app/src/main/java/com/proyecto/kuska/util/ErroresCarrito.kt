package com.proyecto.kuska.util

import com.google.gson.JsonElement
import com.google.gson.JsonParser

/** Prefijos de las claves que devuelve [extraerErroresCarrito]. */
const val PREFIJO_ERROR_CARRITO = "carrito."
const val PREFIJO_ERROR_ITEM = "items."
const val PREFIJO_ERROR_DONACION = "donaciones."

/**
 * Errores del 400 de POST carrito/enviar/ (apps/solicitudes/services.py):
 * {"carrito": ["msg"], "items": {"<id del CarritoItem>": ["msg"]}, "donaciones": {"<id de la donación>": ["msg"]}}
 * - cada mensaje de "carrito" → "carrito.0", "carrito.1"...
 * - "items" y "donaciones" → "items.<id>" y "donaciones.<id>", con todos sus mensajes separados por salto de línea
 * - una lista suelta ["msg"] → como "carrito"
 * - cualquier otra clave (o "items" / "donaciones" con otra forma) se deja con su nombre: la pantalla la muestra
 *   en el aviso general para no perderla.
 * Para un cuerpo vacío o que no es JSON devuelve un mapa vacío.
 */
fun extraerErroresCarrito(cuerpo: String?): Map<String, String> {
    if (cuerpo.isNullOrBlank()) return emptyMap()
    val json = try {
        JsonParser.parseString(cuerpo)
    } catch (_: Exception) {
        return emptyMap()
    }
    val errores = linkedMapOf<String, String>()
    when {
        json.isJsonArray -> agregarGenerales(json, errores)
        json.isJsonObject -> for ((clave, valor) in json.asJsonObject.entrySet()) {
            when {
                clave == "carrito" -> agregarGenerales(valor, errores)
                (clave == "items" || clave == "donaciones") && valor.isJsonObject ->
                    valor.asJsonObject.entrySet().forEach { (id, mensajes) ->
                        val texto = mensajes(mensajes).joinToString("\n")
                        if (texto.isNotEmpty()) errores["$clave.$id"] = texto
                    }
                else -> mensajes(valor).joinToString("\n").takeIf { it.isNotEmpty() }?.let { errores[clave] = it }
            }
        }
    }
    return errores
}

private fun agregarGenerales(valor: JsonElement, errores: MutableMap<String, String>) {
    val inicio = errores.keys.count { it.startsWith(PREFIJO_ERROR_CARRITO) }
    mensajes(valor).forEachIndexed { indice, mensaje -> errores["$PREFIJO_ERROR_CARRITO${inicio + indice}"] = mensaje }
}

/** Todos los textos de un valor (texto, lista o un objeto con listas adentro). */
private fun mensajes(json: JsonElement): List<String> = when {
    json.isJsonPrimitive -> listOfNotNull(json.asString.takeIf { it.isNotBlank() })
    json.isJsonArray -> json.asJsonArray.flatMap { mensajes(it) }
    json.isJsonObject -> json.asJsonObject.entrySet().flatMap { (_, valor) -> mensajes(valor) }
    else -> emptyList()
}
