package com.proyecto.kuska.ui.editarperfil

import com.google.gson.GsonBuilder
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Perfil
import com.proyecto.kuska.ui.comun.TextoUi

/** Campos editables del perfil (RN-04a), con su clave en la API y su largo máximo según el schema. */
enum class CampoPerfil(val clave: String, val largoMaximo: Int, val obligatorio: Boolean) {
    NOMBRES("nombres", 80, true),
    APELLIDOS("apellidos", 80, true),
    TELEFONO("telefono", 20, true),
    DIRECCION("direccion", 200, false),
}

/** Lo escrito en los campos de Editar perfil. */
data class ValoresPerfil(
    val nombres: String = "",
    val apellidos: String = "",
    val telefono: String = "",
    val direccion: String = "",
) {
    fun valor(campo: CampoPerfil) = when (campo) {
        CampoPerfil.NOMBRES -> nombres
        CampoPerfil.APELLIDOS -> apellidos
        CampoPerfil.TELEFONO -> telefono
        CampoPerfil.DIRECCION -> direccion
    }

    fun con(campo: CampoPerfil, valor: String) = when (campo) {
        CampoPerfil.NOMBRES -> copy(nombres = valor)
        CampoPerfil.APELLIDOS -> copy(apellidos = valor)
        CampoPerfil.TELEFONO -> copy(telefono = valor)
        CampoPerfil.DIRECCION -> copy(direccion = valor)
    }

    companion object {
        fun de(perfil: Perfil) = ValoresPerfil(
            nombres = perfil.nombres.orEmpty(),
            apellidos = perfil.apellidos.orEmpty(),
            telefono = perfil.telefono.orEmpty(),
            direccion = perfil.direccion.orEmpty(),
        )
    }
}

/** Cuerpo de PATCH auth/perfil/ (PatchedPerfilRequest) con solo los campos que cambiaron. */
object CambiosPerfil {

    /** Gson propio para el PATCH: el de Retrofit omite los null y una dirección vaciada no llegaría al servidor. */
    private val gsonConNulls = GsonBuilder().serializeNulls().create()

    /**
     * Compara lo escrito con [original] sin espacios en los extremos. En la dirección, vacío y null son lo mismo;
     * si el usuario vació una que tenía valor, se envía null. Sin cambios, el objeto queda vacío.
     */
    fun construir(original: Perfil, valores: ValoresPerfil): JsonObject = JsonObject().apply {
        listOf(
            CampoPerfil.NOMBRES to original.nombres,
            CampoPerfil.APELLIDOS to original.apellidos,
            CampoPerfil.TELEFONO to original.telefono,
        ).forEach { (campo, anterior) ->
            val nuevo = valores.valor(campo).trim()
            if (nuevo != anterior?.trim().orEmpty()) addProperty(campo.clave, nuevo)
        }
        val direccion = valores.direccion.trim().ifEmpty { null }
        val direccionAnterior = original.direccion?.trim()?.ifEmpty { null }
        if (direccion != direccionAnterior) {
            add(CampoPerfil.DIRECCION.clave, direccion?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
        }
    }

    fun aJson(cambios: JsonObject): String = gsonConNulls.toJson(cambios)

    /** Obligatorios (nombres, apellidos y teléfono) y largos máximos. El resto lo valida el servidor. */
    fun validar(valores: ValoresPerfil): Map<CampoPerfil, TextoUi> = buildMap {
        CampoPerfil.entries.forEach { campo ->
            val limpio = valores.valor(campo).trim()
            when {
                campo.obligatorio && limpio.isEmpty() -> put(campo, TextoUi.Recurso(R.string.error_campo_obligatorio))
                limpio.length > campo.largoMaximo ->
                    put(campo, TextoUi.Recurso(R.string.error_largo_maximo, listOf(campo.largoMaximo)))
            }
        }
    }

    /** Clave de la API → campo, para repartir los errores de la respuesta. */
    val CAMPOS_API: Map<String, CampoPerfil> = CampoPerfil.entries.associateBy { it.clave }
}
