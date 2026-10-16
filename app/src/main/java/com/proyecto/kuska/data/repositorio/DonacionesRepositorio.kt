package com.proyecto.kuska.data.repositorio

import android.content.Context
import com.google.gson.Gson
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.KumoraApi
import com.proyecto.kuska.data.api.modelos.CategoriaPublica
import com.proyecto.kuska.data.api.modelos.DatosDonacion
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.util.Resultado
import com.proyecto.kuska.util.extraerErroresDonacion
import com.proyecto.kuska.util.extraerErroresPorCampo
import com.proyecto.kuska.util.mensajeDeError
import com.proyecto.kuska.util.mensajeDeExcepcion
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response
import java.io.File

class DonacionesRepositorio(
    context: Context,
    private val api: KumoraApi,
) {
    private val context = context.applicationContext
    private val gson = Gson()

    suspend fun obtenerCategorias(): Resultado<List<CategoriaPublica>> =
        ejecutar({ api.obtenerCategorias() })

    /** Dirección del perfil del usuario (null si no tiene). */
    suspend fun obtenerDireccionPerfil(): Resultado<String?> =
        when (val resultado = ejecutar({ api.obtenerPerfil() })) {
            is Resultado.Exito -> Resultado.Exito(resultado.datos.direccion)
            is Resultado.Error -> resultado
        }

    /**
     * Crea la donación en una sola petición multipart: "datos" con el JSON y [fotos] como
     * foto_0, foto_1... (mismo orden que datos.alimentos). Los errores por alimento llegan en
     * [Resultado.Error.erroresCampo] con las claves de [extraerErroresDonacion].
     */
    suspend fun crearDonacion(datos: DatosDonacion, fotos: List<File>): Resultado<Donacion> {
        val partes = buildList {
            add(MultipartBody.Part.createFormData(CAMPO_DATOS, gson.toJson(datos)))
            fotos.forEachIndexed { indice, foto ->
                add(MultipartBody.Part.createFormData("foto_$indice", foto.name, foto.asRequestBody(TIPO_JPEG)))
            }
        }
        return ejecutar({ api.crearDonacion(partes) }, ::extraerErroresDonacion)
    }

    /** Hace la llamada y convierte cualquier fallo (HTTP o de red) en [Resultado.Error]. */
    private suspend fun <T> ejecutar(
        llamada: suspend () -> Response<T>,
        erroresPorCampo: (String?) -> Map<String, String> = ::extraerErroresPorCampo,
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
            Resultado.Error(mensaje, codigo, erroresPorCampo(cuerpoError))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Resultado.Error(mensajeDeExcepcion(context, e))
    }

    private companion object {
        const val CAMPO_DATOS = "datos"
        val TIPO_JPEG = "image/jpeg".toMediaType()
    }
}
