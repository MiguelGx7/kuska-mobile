package com.proyecto.kuska.util

/** Resultado de una operación de repositorio, con el mensaje listo para mostrar si falla. */
sealed interface Resultado<out T> {
    data class Exito<T>(val datos: T) : Resultado<T>

    /** [erroresCampo]: primer mensaje de cada campo cuando la API responde {"campo": ["mensaje"]}. */
    data class Error(
        val mensaje: String,
        val codigo: Int? = null,
        val erroresCampo: Map<String, String> = emptyMap(),
    ) : Resultado<Nothing>
}
