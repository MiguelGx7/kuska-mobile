package com.proyecto.kuska.data.repositorio

import android.content.Context
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.KumoraApi
import com.proyecto.kuska.data.api.modelos.LoginRequest
import com.proyecto.kuska.data.api.modelos.Usuario
import com.proyecto.kuska.data.sesion.SesionManager
import com.proyecto.kuska.util.Resultado
import com.proyecto.kuska.util.mensajeDeError
import com.proyecto.kuska.util.mensajeDeExcepcion
import kotlinx.coroutines.CancellationException

class AuthRepositorio(
    context: Context,
    private val api: KumoraApi,
    private val sesion: SesionManager,
) {
    private val context = context.applicationContext

    /**
     * Inicia sesión. Solo guarda la sesión si el rol es Donante o Beneficiario.
     * 401 → "Correo o contraseña incorrectos." · 403 → el "detail" de la API tal cual.
     */
    suspend fun login(correo: String, contrasena: String): Resultado<Usuario> = try {
        val respuesta = api.login(LoginRequest(correo.trim(), contrasena))
        val cuerpo = respuesta.body()
        when {
            respuesta.isSuccessful && cuerpo != null -> when (cuerpo.usuario.rol) {
                Usuario.ROL_DONANTE, Usuario.ROL_BENEFICIARIO -> {
                    sesion.guardarSesion(cuerpo.access, cuerpo.refresh, cuerpo.usuario)
                    Resultado.Exito(cuerpo.usuario)
                }
                Usuario.ROL_ADMINISTRADOR ->
                    Resultado.Error(context.getString(R.string.error_cuenta_administrador))
                else -> Resultado.Error(context.getString(R.string.error_rol_no_soportado))
            }
            respuesta.code() == 401 ->
                Resultado.Error(context.getString(R.string.error_credenciales), 401)
            else -> Resultado.Error(mensajeDeError(context, respuesta), respuesta.code())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Resultado.Error(mensajeDeExcepcion(context, e))
    }

    suspend fun cerrarSesion() = sesion.cerrarSesion()
}
