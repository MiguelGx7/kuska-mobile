package com.proyecto.kuska.data.repositorio

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.KumoraApi
import com.proyecto.kuska.data.api.modelos.Alimento
import com.proyecto.kuska.data.api.modelos.AlimentoConSolicitudes
import com.proyecto.kuska.data.api.modelos.CategoriaPublica
import com.proyecto.kuska.data.api.modelos.DatosAlimento
import com.proyecto.kuska.data.api.modelos.DatosDonacion
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.data.api.modelos.Entrega
import com.proyecto.kuska.data.api.modelos.EntregarRequest
import com.proyecto.kuska.data.api.modelos.Novedad
import com.proyecto.kuska.data.api.modelos.NovedadCrearRequest
import com.proyecto.kuska.data.api.modelos.PaginaDonaciones
import com.proyecto.kuska.ui.creardonacion.ConstructorDatos
import com.proyecto.kuska.ui.editardonacion.CambiosDonacion
import com.proyecto.kuska.util.Resultado
import com.proyecto.kuska.util.extraerErroresDonacion
import com.proyecto.kuska.util.extraerErroresPorCampo
import com.proyecto.kuska.util.mensajeDeError
import com.proyecto.kuska.util.mensajeDeExcepcion
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
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

    /** [estado] null = todas. [pagina] empieza en 1. */
    suspend fun listarDonaciones(estado: String?, pagina: Int): Resultado<PaginaDonaciones> =
        ejecutar({ api.listarDonaciones(estado, pagina) })

    suspend fun obtenerDonacion(id: Int): Resultado<Donacion> = ejecutar({ api.obtenerDonacion(id) })

    suspend fun solicitudesRecibidas(id: Int): Resultado<List<AlimentoConSolicitudes>> =
        ejecutar({ api.solicitudesRecibidas(id) })

    /** Solo Pendiente (RN-12); el servidor también borra las fotos. */
    suspend fun eliminarDonacion(id: Int): Resultado<Unit> =
        ejecutar({ api.eliminarDonacion(id) }, sinCuerpo = Unit)

    /** PATCH con solo lo que cambió ([CambiosDonacion]); los opcionales vaciados viajan como null. */
    suspend fun editarDonacion(id: Int, cambios: JsonObject): Resultado<Donacion> {
        val cuerpo = CambiosDonacion.aJson(cambios).toRequestBody(TIPO_JSON)
        return ejecutar({ api.editarDonacion(id, cuerpo) })
    }

    /** Multipart con los campos de AlimentoNuevoRequest y la foto obligatoria (RN-11a). */
    suspend fun agregarAlimento(id: Int, alimento: DatosAlimento, foto: File): Resultado<Alimento> {
        val partes = buildList {
            ConstructorDatos.camposMultipart(alimento).forEach { (campo, valor) ->
                add(MultipartBody.Part.createFormData(campo, valor))
            }
            add(MultipartBody.Part.createFormData(CAMPO_FOTO, foto.name, foto.asRequestBody(TIPO_JPEG)))
        }
        return ejecutar({ api.agregarAlimento(id, partes) })
    }

    /** No se puede quitar el último alimento: en ese caso se elimina la donación. */
    suspend fun quitarAlimento(id: Int, alimentoId: Int): Resultado<Unit> =
        ejecutar({ api.quitarAlimento(id, alimentoId) }, sinCuerpo = Unit)

    /** Entrega total de una solicitud Aprobada (RN-26). El error de la observación llega en erroresCampo["observacion"]. */
    suspend fun registrarEntrega(solicitudId: Int, cuerpo: EntregarRequest): Resultado<Entrega> =
        ejecutar({ api.registrarEntrega(solicitudId, cuerpo) })

    /** Todas las novedades de la donación: recorre las páginas mientras haya "next". */
    suspend fun novedadesDeDonacion(id: Int): Resultado<List<Novedad>> {
        val todas = mutableListOf<Novedad>()
        var pagina = 1
        while (pagina <= MAX_PAGINAS_NOVEDADES) {
            when (val resultado = ejecutar({ api.listarNovedades(id, pagina) })) {
                is Resultado.Error -> return resultado
                is Resultado.Exito -> {
                    todas += resultado.datos.resultados
                    if (resultado.datos.siguiente == null) break
                }
            }
            pagina++
        }
        return Resultado.Exito(todas)
    }

    /** RN-30. El error de la descripción llega en erroresCampo["descripcion"]. */
    suspend fun reportarNovedad(cuerpo: NovedadCrearRequest): Resultado<Novedad> =
        ejecutar({ api.reportarNovedad(cuerpo) })

    /**
     * Hace la llamada y convierte cualquier fallo (HTTP o de red) en [Resultado.Error].
     * [sinCuerpo]: valor de éxito para las respuestas sin cuerpo (204).
     */
    private suspend fun <T : Any> ejecutar(
        llamada: suspend () -> Response<T>,
        erroresPorCampo: (String?) -> Map<String, String> = ::extraerErroresPorCampo,
        sinCuerpo: T? = null,
    ): Resultado<T> = try {
        val respuesta = llamada()
        val cuerpo = respuesta.body() ?: sinCuerpo
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
        const val CAMPO_FOTO = "foto"

        /** Tope de seguridad: una donación tiene pocas novedades. */
        const val MAX_PAGINAS_NOVEDADES = 20
        val TIPO_JPEG = "image/jpeg".toMediaType()
        val TIPO_JSON = "application/json; charset=utf-8".toMediaType()
    }
}
