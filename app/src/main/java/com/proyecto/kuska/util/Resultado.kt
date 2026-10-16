package com.proyecto.kuska.util

/** Resultado de una operación de repositorio, con el mensaje listo para mostrar si falla. */
sealed interface Resultado<out T> {
    data class Exito<T>(val datos: T) : Resultado<T>
    data class Error(val mensaje: String, val codigo: Int? = null) : Resultado<Nothing>
}
