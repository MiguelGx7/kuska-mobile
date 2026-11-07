package com.proyecto.kuska.data.api

import com.google.gson.JsonParser
import com.proyecto.kuska.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * Un 401 casi siempre es un token vencido, salvo en cambiar contraseña: ahí también puede ser la contraseña actual
 * incorrecta, y eso nunca debe refrescar el token ni cerrar la sesión. Solo se trata como token vencido si
 * simplejwt lo dice ("code": "token_not_valid").
 */
object Respuestas401 {

    private const val RUTA_CAMBIAR_CONTRASENA = "auth/perfil/cambiar-contrasena/"
    private const val CODIGO_TOKEN_INVALIDO = "token_not_valid"

    private val rutaBase = BuildConfig.BASE_URL.toHttpUrl().encodedPath

    fun esCambioContrasena(request: Request): Boolean =
        request.url.encodedPath.removePrefix(rutaBase) == RUTA_CAMBIAR_CONTRASENA

    fun esTokenInvalido(cuerpo: String?): Boolean {
        if (cuerpo.isNullOrBlank()) return false
        return try {
            val json = JsonParser.parseString(cuerpo)
            json.isJsonObject && json.asJsonObject.get("code")?.takeIf { it.isJsonPrimitive }?.asString == CODIGO_TOKEN_INVALIDO
        } catch (_: Exception) {
            false
        }
    }
}
