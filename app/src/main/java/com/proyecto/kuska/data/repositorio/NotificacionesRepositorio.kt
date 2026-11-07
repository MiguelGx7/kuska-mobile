package com.proyecto.kuska.data.repositorio

import android.content.Context
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.KumoraApi
import com.proyecto.kuska.data.api.modelos.LeerTodas
import com.proyecto.kuska.data.api.modelos.Notificacion
import com.proyecto.kuska.data.api.modelos.PaginaNotificaciones
import com.proyecto.kuska.util.Resultado
import com.proyecto.kuska.util.extraerErroresPorCampo
import com.proyecto.kuska.util.mensajeDeError
import com.proyecto.kuska.util.mensajeDeExcepcion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import retrofit2.Response

/**
 * Notificaciones del usuario (HU-119, HU-120). Guarda el contador de no leídas para la campana: se actualiza con
 * cada respuesta que trae no_leidas. Es uno solo para toda la app (vive en KumoraApp).
 */
class NotificacionesRepositorio(
    context: Context,
    private val api: KumoraApi,
) {
    private val context = context.applicationContext

    private val _noLeidas = MutableStateFlow<Int?>(null)

    /** Null = todavía no se sabe (o no hay sesión). */
    val noLeidas: StateFlow<Int?> = _noLeidas.asStateFlow()

    /** De la más nueva a la más vieja. [soloNoLeidas] false = todas. [pagina] empieza en 1. */
    suspend fun listar(soloNoLeidas: Boolean, pagina: Int): Resultado<PaginaNotificaciones> {
        val resultado = ejecutar { api.listarNotificaciones(leida = if (soloNoLeidas) false else null, pagina = pagina) }
        if (resultado is Resultado.Exito) _noLeidas.value = resultado.datos.noLeidas
        return resultado
    }

    /** Si es de otro usuario, 404. */
    suspend fun marcarLeida(id: Int): Resultado<Notificacion> = ejecutar { api.marcarNotificacionLeida(id) }

    suspend fun marcarTodas(): Resultado<LeerTodas> {
        val resultado = ejecutar { api.marcarTodasLeidas() }
        if (resultado is Resultado.Exito) _noLeidas.value = resultado.datos.noLeidas
        return resultado
    }

    /** Solo para el contador: la primera página de las no leídas trae no_leidas. Si falla, queda el último valor. */
    suspend fun refrescarContador() {
        listar(soloNoLeidas = true, pagina = 1)
    }

    /** Cambio optimista del contador (al marcar una como leída o al deshacerlo). Nunca baja de 0. */
    fun ajustarContador(cambio: Int) {
        _noLeidas.update { actual -> actual?.let { (it + cambio).coerceAtLeast(0) } }
    }

    /** Otro endpoint trajo el total de no leídas (p. ej. el resumen del beneficiario). */
    fun fijarContador(noLeidas: Int) {
        _noLeidas.value = noLeidas.coerceAtLeast(0)
    }

    /** Al cerrar sesión o cambiar de cuenta. */
    fun limpiar() {
        _noLeidas.value = null
    }

    /** Hace la llamada y convierte cualquier fallo (HTTP o de red) en [Resultado.Error]. */
    private suspend fun <T : Any> ejecutar(llamada: suspend () -> Response<T>): Resultado<T> = try {
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
            Resultado.Error(mensaje, codigo, extraerErroresPorCampo(cuerpoError))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Resultado.Error(mensajeDeExcepcion(context, e))
    }
}
