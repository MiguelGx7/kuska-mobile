package com.proyecto.kuska.ui.editardonacion

import com.google.gson.GsonBuilder
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.proyecto.kuska.data.api.modelos.Donacion

/** Cuerpo de PATCH donaciones/{id}/ (PatchedDonacionEditarRequest) con solo los campos que cambiaron. */
object CambiosDonacion {

    /** Gson propio para el PATCH: el de Retrofit omite los null y un opcional vaciado no llegaría al servidor. */
    private val gsonConNulls = GsonBuilder().serializeNulls().create()

    /**
     * Compara lo escrito con [original] sin espacios en los extremos. En descripción y dirección, vacío y null
     * son lo mismo; si el usuario vació uno que tenía valor, se envía null. Sin cambios, el objeto queda vacío.
     */
    fun construir(
        original: Donacion,
        titulo: String,
        descripcion: String,
        direccion: String,
        modalidad: String?,
    ): JsonObject = JsonObject().apply {
        val tituloNuevo = titulo.trim()
        if (tituloNuevo != original.titulo.trim()) addProperty("titulo", tituloNuevo)
        opcional("descripcion", descripcion, original.descripcion)
        opcional("direccion_recogida", direccion, original.direccionRecogida)
        if (modalidad != null && modalidad != original.modalidadEntrega) addProperty("modalidad_entrega", modalidad)
    }

    fun aJson(cambios: JsonObject): String = gsonConNulls.toJson(cambios)

    private fun JsonObject.opcional(clave: String, escrito: String, anterior: String?) {
        val nuevo = escrito.trim().ifEmpty { null }
        val viejo = anterior?.trim()?.ifEmpty { null }
        if (nuevo != viejo) add(clave, nuevo?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
    }
}
