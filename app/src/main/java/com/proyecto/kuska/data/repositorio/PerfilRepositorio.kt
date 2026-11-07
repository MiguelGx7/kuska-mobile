package com.proyecto.kuska.data.repositorio

import android.content.Context
import com.google.gson.JsonObject
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.KumoraApi
import com.proyecto.kuska.data.api.modelos.CambiarContrasenaRequest
import com.proyecto.kuska.data.api.modelos.Perfil
import com.proyecto.kuska.data.sesion.SesionManager
import com.proyecto.kuska.ui.editarperfil.CambiosPerfil
import com.proyecto.kuska.util.Resultado
import com.proyecto.kuska.util.extraerErroresPorCampo
import com.proyecto.kuska.util.extraerMensajeError
import com.proyecto.kuska.util.mensajeDeError
import com.proyecto.kuska.util.mensajeDeExcepcion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

/** Cómo quedó la sesión después de cambiar la contraseña. */
enum class SesionTrasCambio {
    /** El login por detrás con la contraseña nueva funcionó: hay tokens nuevos. */
    RENOVADA,

    /** No se pudo volver a entrar: se cerró la sesión con el aviso de iniciar con la nueva contraseña. */
    CERRADA,
}

/** Perfil del usuario (RN-04a), común a Donante y Beneficiario. */
class PerfilRepositorio(
    context: Context,
    private val api: KumoraApi,
    private val sesion: SesionManager,
    private val auth: AuthRepositorio,
) {
    private val context = context.applicationContext

    suspend fun obtenerPerfil(): Resultado<Perfil> = ejecutar({ api.obtenerPerfil() })

    /** PATCH con solo lo que cambió ([CambiosPerfil]); la dirección vaciada viaja como null. */
    suspend fun editarPerfil(cambios: JsonObject): Resultado<Perfil> {
        val cuerpo = CambiosPerfil.aJson(cambios).toRequestBody(TIPO_JSON)
        val resultado = ejecutar({ api.editarPerfil(cuerpo) })
        if (resultado is Resultado.Exito) sesion.actualizarNombres(resultado.datos.nombres, resultado.datos.apellidos)
        return resultado
    }

    /**
     * El backend invalida todos los refresh tokens, también el de este celular. Para que la sesión no se cierre sola
     * cuando venza el access, se vuelve a iniciar sesión por detrás con el correo y la contraseña nueva (que solo
     * vive en memoria durante esta llamada). Si eso falla, se cierra la sesión de una vez con un aviso.
     * Un 401 aquí es la contraseña actual incorrecta: va a su campo (el refresh no lo toca, ver Respuestas401).
     */
    suspend fun cambiarContrasena(actual: String, nueva: String): Resultado<SesionTrasCambio> {
        val cambio = ejecutar({ api.cambiarContrasena(CambiarContrasenaRequest(actual, nueva)) }, campo401 = CAMPO_ACTUAL)
        if (cambio is Resultado.Error) return cambio

        val correo = sesion.sesion.first()?.usuario?.correo
        val login = correo?.let { auth.login(it, nueva) }
        if (login is Resultado.Exito) return Resultado.Exito(SesionTrasCambio.RENOVADA)

        sesion.notificarAviso(R.string.perfil_contrasena_relogin)
        sesion.cerrarSesion()
        return Resultado.Exito(SesionTrasCambio.CERRADA)
    }

    /**
     * Hace la llamada y convierte cualquier fallo (HTTP o de red) en [Resultado.Error].
     * [campo401]: si llega un 401, su mensaje va a ese campo (solo cambiar contraseña).
     */
    private suspend fun <T : Any> ejecutar(
        llamada: suspend () -> Response<T>,
        campo401: String? = null,
    ): Resultado<T> = try {
        val respuesta = llamada()
        val cuerpo = respuesta.body()
        if (respuesta.isSuccessful && cuerpo != null) {
            Resultado.Exito(cuerpo)
        } else {
            // El cuerpo de error solo se puede leer una vez.
            val cuerpoError = respuesta.errorBody()?.string()
            val codigo = respuesta.code()
            val mensaje = if (codigo == 429) {
                context.getString(R.string.error_demasiados_intentos)
            } else {
                mensajeDeError(context, codigo, cuerpoError)
            }
            val erroresCampo = if (codigo == 401 && campo401 != null) {
                mapOf(campo401 to (extraerMensajeError(cuerpoError) ?: context.getString(R.string.perfil_error_actual_incorrecta)))
            } else {
                extraerErroresPorCampo(cuerpoError)
            }
            Resultado.Error(mensaje, codigo, erroresCampo)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Resultado.Error(mensajeDeExcepcion(context, e))
    }

    companion object {
        /** Clave del error de la contraseña actual en la respuesta (y del 401). */
        const val CAMPO_ACTUAL = "contrasena_actual"
        private val TIPO_JSON = "application/json; charset=utf-8".toMediaType()
    }
}
