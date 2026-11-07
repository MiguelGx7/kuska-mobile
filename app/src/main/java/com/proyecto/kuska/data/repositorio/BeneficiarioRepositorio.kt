package com.proyecto.kuska.data.repositorio

import android.content.Context
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.KumoraApi
import com.proyecto.kuska.data.api.modelos.CarritoAgregarRequest
import com.proyecto.kuska.data.api.modelos.CarritoItem
import com.proyecto.kuska.data.api.modelos.CategoriaPublica
import com.proyecto.kuska.data.api.modelos.PaginaAlimentosDisponibles
import com.proyecto.kuska.ui.iniciobeneficiario.FiltrosAlimentos
import com.proyecto.kuska.util.Resultado
import com.proyecto.kuska.util.extraerErroresPorCampo
import com.proyecto.kuska.util.mensajeDeError
import com.proyecto.kuska.util.mensajeDeExcepcion
import kotlinx.coroutines.CancellationException
import retrofit2.Response

/** Lo que hace el beneficiario: ver alimentos disponibles y armar su carrito. */
class BeneficiarioRepositorio(
    context: Context,
    private val api: KumoraApi,
) {
    private val context = context.applicationContext

    /** Solo las activas (RN-17), para el filtro por categoría. */
    suspend fun obtenerCategorias(): Resultado<List<CategoriaPublica>> = ejecutar({ api.obtenerCategorias() })

    /** Ya vienen ordenados por vencimiento más cercano. [pagina] empieza en 1. */
    suspend fun listarAlimentos(filtros: FiltrosAlimentos, pagina: Int): Resultado<PaginaAlimentosDisponibles> =
        ejecutar({
            api.listarAlimentosDisponibles(
                busqueda = filtros.parametroBusqueda,
                categoria = filtros.categoria,
                modalidad = filtros.modalidad,
                pagina = pagina,
            )
        })

    /** RN-20. El error de la cantidad llega en erroresCampo["cantidad"]; las reglas, como mensaje general. */
    suspend fun agregarAlCarrito(cuerpo: CarritoAgregarRequest): Resultado<CarritoItem> =
        ejecutar({ api.agregarAlCarrito(cuerpo) })

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
