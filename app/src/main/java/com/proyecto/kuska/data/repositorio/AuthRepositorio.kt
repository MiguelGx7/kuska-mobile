package com.proyecto.kuska.data.repositorio

import android.content.Context
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.KumoraApi
import com.proyecto.kuska.data.api.modelos.LoginRequest
import com.proyecto.kuska.data.api.modelos.LoginResponse
import com.proyecto.kuska.data.api.modelos.RecuperarConfirmarRequest
import com.proyecto.kuska.data.api.modelos.RegistroRequest
import com.proyecto.kuska.data.api.modelos.SolicitarCodigoRequest
import com.proyecto.kuska.data.api.modelos.Usuario
import com.proyecto.kuska.data.sesion.SesionManager
import com.proyecto.kuska.util.Resultado
import com.proyecto.kuska.util.extraerErroresPorCampo
import com.proyecto.kuska.util.extraerMensajeError
import com.proyecto.kuska.util.mensajeDeError
import com.proyecto.kuska.util.mensajeDeExcepcion
import kotlinx.coroutines.CancellationException
import retrofit2.Response

class AuthRepositorio(
    context: Context,
    private val api: KumoraApi,
    private val sesion: SesionManager,
) {
    private val context = context.applicationContext

    /**
     * Inicia sesión. Solo guarda la sesión si el rol es Donante o Beneficiario.
     * 401 y 403 → el "detail" de la API tal cual · 429 → mensaje propio de demasiados intentos.
     */
    suspend fun login(correo: String, contrasena: String): Resultado<Usuario> =
        ejecutar({ api.login(LoginRequest(correo.trim(), contrasena)) }) { guardarSesion(it) }

    /** Pide el código de verificación al correo (RN-04d). Devuelve el mensaje de la API. */
    suspend fun solicitarCodigoRegistro(correo: String): Resultado<String?> =
        ejecutar({ api.solicitarCodigoRegistro(SolicitarCodigoRequest(correo.trim())) }) {
            Resultado.Exito(it.mensaje)
        }

    /** Crea la cuenta con el código. Responde como el login, así que se guarda la sesión igual. */
    suspend fun registrar(solicitud: RegistroRequest): Resultado<Usuario> =
        ejecutar({ api.registrar(solicitud) }) { guardarSesion(it) }

    /** Pide el código para recuperar la contraseña (RN-04b). Devuelve el mensaje de la API, si trae. */
    suspend fun solicitarRecuperacion(correo: String): Resultado<String?> =
        ejecutar({ api.solicitarRecuperacion(SolicitarCodigoRequest(correo.trim())) }) {
            Resultado.Exito(it.mensaje)
        }

    /** Cambia la contraseña con el código. No inicia sesión. */
    suspend fun confirmarRecuperacion(correo: String, codigo: String, contrasenaNueva: String): Resultado<String?> =
        ejecutar({
            api.confirmarRecuperacion(RecuperarConfirmarRequest(correo.trim(), codigo, contrasenaNueva))
        }) { Resultado.Exito(it.mensaje) }

    suspend fun cerrarSesion() = sesion.cerrarSesion()

    private suspend fun guardarSesion(cuerpo: LoginResponse): Resultado<Usuario> =
        when (cuerpo.usuario.rol) {
            Usuario.ROL_DONANTE, Usuario.ROL_BENEFICIARIO -> {
                sesion.guardarSesion(cuerpo.access, cuerpo.refresh, cuerpo.usuario)
                Resultado.Exito(cuerpo.usuario)
            }
            Usuario.ROL_ADMINISTRADOR ->
                Resultado.Error(context.getString(R.string.error_cuenta_administrador))
            else -> Resultado.Error(context.getString(R.string.error_rol_no_soportado))
        }

    /** Hace la llamada y convierte cualquier fallo (HTTP o de red) en [Resultado.Error]. */
    private suspend fun <R, T> ejecutar(
        llamada: suspend () -> Response<R>,
        alExito: suspend (R) -> Resultado<T>,
    ): Resultado<T> = try {
        val respuesta = llamada()
        val cuerpo = respuesta.body()
        if (respuesta.isSuccessful && cuerpo != null) alExito(cuerpo) else errorHttp(respuesta)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Resultado.Error(mensajeDeExcepcion(context, e))
    }

    private fun errorHttp(respuesta: Response<*>): Resultado.Error {
        val codigo = respuesta.code()
        // El cuerpo de error solo se puede leer una vez.
        val cuerpo = respuesta.errorBody()?.string()
        val mensaje = when (codigo) {
            // En rutas públicas solo el login responde 401 (credenciales).
            401 -> extraerMensajeError(cuerpo) ?: context.getString(R.string.error_credenciales)
            429 -> context.getString(R.string.error_demasiados_intentos)
            else -> mensajeDeError(context, codigo, cuerpo)
        }
        return Resultado.Error(mensaje, codigo, extraerErroresPorCampo(cuerpo))
    }
}
