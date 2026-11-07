package com.proyecto.kuska.data.repositorio

import android.content.Context
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.KumoraApi
import com.proyecto.kuska.data.api.modelos.Carrito
import com.proyecto.kuska.data.api.modelos.CarritoAgregarRequest
import com.proyecto.kuska.data.api.modelos.CarritoCantidadRequest
import com.proyecto.kuska.data.api.modelos.CarritoItem
import com.proyecto.kuska.data.api.modelos.CategoriaPublica
import com.proyecto.kuska.data.api.modelos.ConfirmarEntregaRequest
import com.proyecto.kuska.data.api.modelos.EnviarCarritoRequest
import com.proyecto.kuska.data.api.modelos.MiSolicitud
import com.proyecto.kuska.data.api.modelos.PaginaAlimentosDisponibles
import com.proyecto.kuska.data.api.modelos.PaginaMisSolicitudes
import com.proyecto.kuska.data.api.modelos.PedidoEnviado
import com.proyecto.kuska.ui.iniciobeneficiario.FiltrosAlimentos
import com.proyecto.kuska.util.Resultado
import com.proyecto.kuska.util.extraerErroresCarrito
import com.proyecto.kuska.util.extraerErroresPorCampo
import com.proyecto.kuska.util.mensajeDeError
import com.proyecto.kuska.util.mensajeDeExcepcion
import kotlinx.coroutines.CancellationException
import retrofit2.Response

/** Lo que hace el beneficiario: ver alimentos disponibles, armar su carrito, enviarlo y seguir sus solicitudes. */
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

    /** RN-21. */
    suspend fun obtenerCarrito(): Resultado<Carrito> = ejecutar({ api.obtenerCarrito() })

    /** RN-20, como agregar: el error de la cantidad llega en erroresCampo["cantidad"]. */
    suspend fun cambiarCantidad(itemId: Int, cuerpo: CarritoCantidadRequest): Resultado<CarritoItem> =
        ejecutar({ api.cambiarCantidadCarrito(itemId, cuerpo) })

    suspend fun quitarDelCarrito(itemId: Int): Resultado<Unit> =
        ejecutar({ api.quitarDelCarrito(itemId) }, sinCuerpo = Unit)

    /** RN-22. Un 400 trae los errores con las claves de [extraerErroresCarrito]. */
    suspend fun enviarCarrito(cuerpo: EnviarCarritoRequest): Resultado<PedidoEnviado> =
        ejecutar({ api.enviarCarrito(cuerpo) }, erroresPorCampo = ::extraerErroresCarrito)

    /** [estado] null = todas. [pagina] empieza en 1. */
    suspend fun listarMisSolicitudes(estado: String?, pagina: Int): Resultado<PaginaMisSolicitudes> =
        ejecutar({ api.listarMisSolicitudes(estado, pagina) })

    suspend fun obtenerMiSolicitud(id: Int): Resultado<MiSolicitud> = ejecutar({ api.obtenerMiSolicitud(id) })

    /** RN-23. Si ya no está Pendiente, 400 con la regla como mensaje general. */
    suspend fun cancelarMiSolicitud(id: Int): Resultado<MiSolicitud> = ejecutar({ api.cancelarMiSolicitud(id) })

    /** RN-26b. El error del comentario llega en erroresCampo["comentario"]; el plazo vencido, como mensaje general. */
    suspend fun confirmarEntrega(id: Int, cuerpo: ConfirmarEntregaRequest): Resultado<MiSolicitud> =
        ejecutar({ api.confirmarEntrega(id, cuerpo) })

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
}
