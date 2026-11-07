package com.proyecto.kuska.ui.carrito

import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Carrito
import com.proyecto.kuska.data.api.modelos.CarritoGrupo
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.data.api.modelos.EleccionDonacion
import com.proyecto.kuska.data.api.modelos.EnviarCarritoRequest
import com.proyecto.kuska.ui.comun.TextoUi
import com.proyecto.kuska.util.PREFIJO_ERROR_DONACION
import com.proyecto.kuska.util.PREFIJO_ERROR_ITEM
import com.proyecto.kuska.util.Resultado

/**
 * Cómo quiere recibir el beneficiario una donación "Ambas" (HU-049). Empieza sin modalidad: hay que elegirla.
 * [direccionEditada]: el usuario ya tocó la dirección, así que no se vuelve a llenar con la del perfil.
 */
data class EleccionEntrega(
    val modalidad: String? = null,
    val direccion: String = "",
    val direccionEditada: Boolean = false,
)

/** Errores del 400 de enviar, repartidos entre ítems, grupos (donaciones) y el aviso general. */
data class ErroresEnvio(
    val porItem: Map<Int, String> = emptyMap(),
    val porDonacion: Map<Int, String> = emptyMap(),
    val generales: List<String> = emptyList(),
)

/** Lógica del envío del carrito (HU-049, HU-050, RN-22), sin Android. */
object EnvioCarrito {

    const val LARGO_DIRECCION = 200

    fun pideEleccion(grupo: CarritoGrupo): Boolean = grupo.modalidadEntrega == Donacion.MODALIDAD_AMBAS

    /** Una solicitud por donación (RN-22). */
    fun solicitudesACrear(carrito: Carrito): Int = carrito.grupos.size

    /** El servidor manda (puede_enviar); además tiene que haber algo y ninguna otra acción en curso. */
    fun puedeEnviar(carrito: Carrito?, ocupado: Boolean): Boolean =
        carrito != null && carrito.puedeEnviar && carrito.grupos.isNotEmpty() && !ocupado

    /**
     * Elige Recoger o Domicilio. Al pasar a Domicilio, si la dirección está vacía y el usuario no la tocó, se llena
     * con la del perfil.
     */
    fun elegir(eleccion: EleccionEntrega, modalidad: String, direccionPerfil: String?): EleccionEntrega {
        val llenar = modalidad == Donacion.MODALIDAD_DOMICILIO && eleccion.direccion.isBlank() &&
            !eleccion.direccionEditada && !direccionPerfil.isNullOrBlank()
        return eleccion.copy(modalidad = modalidad, direccion = if (llenar) direccionPerfil!!.trim() else eleccion.direccion)
    }

    /** El perfil llegó después de elegir Domicilio: llena las direcciones vacías que el usuario no tocó. */
    fun conDireccionPerfil(elecciones: Map<Int, EleccionEntrega>, direccionPerfil: String?): Map<Int, EleccionEntrega> {
        if (direccionPerfil.isNullOrBlank()) return elecciones
        return elecciones.mapValues { (_, eleccion) ->
            if (eleccion.modalidad == Donacion.MODALIDAD_DOMICILIO) elegir(eleccion, eleccion.modalidad, direccionPerfil)
            else eleccion
        }
    }

    /** Solo las elecciones de las donaciones "Ambas" que siguen en el carrito. */
    fun conservar(elecciones: Map<Int, EleccionEntrega>, carrito: Carrito): Map<Int, EleccionEntrega> {
        val ambas = carrito.grupos.filter(::pideEleccion).mapTo(HashSet()) { it.donacion }
        return elecciones.filterKeys { it in ambas }
    }

    /** Error de cada donación "Ambas" que no está lista: sin modalidad, o Domicilio sin dirección. */
    fun validar(carrito: Carrito, elecciones: Map<Int, EleccionEntrega>): Map<Int, TextoUi> = buildMap {
        carrito.grupos.filter(::pideEleccion).forEach { grupo ->
            val eleccion = elecciones[grupo.donacion] ?: EleccionEntrega()
            val error = when {
                eleccion.modalidad == null -> TextoUi.Recurso(R.string.carrito_error_modalidad)
                eleccion.modalidad == Donacion.MODALIDAD_DOMICILIO -> errorDireccion(eleccion.direccion)
                else -> null
            }
            if (error != null) put(grupo.donacion, error)
        }
    }

    fun errorDireccion(direccion: String): TextoUi? {
        val limpia = direccion.trim()
        return when {
            limpia.isEmpty() -> TextoUi.Recurso(R.string.carrito_error_direccion)
            limpia.length > LARGO_DIRECCION -> TextoUi.Recurso(R.string.error_largo_maximo, listOf(LARGO_DIRECCION))
            else -> null
        }
    }

    /**
     * Supone que [validar] no devolvió errores. Solo van las donaciones "Ambas"; la dirección, sin espacios
     * sobrantes, solo si es Domicilio. Las de una sola modalidad no piden nada.
     */
    fun cuerpo(carrito: Carrito, elecciones: Map<Int, EleccionEntrega>): EnviarCarritoRequest =
        EnviarCarritoRequest(
            carrito.grupos.filter(::pideEleccion).mapNotNull { grupo ->
                val eleccion = elecciones[grupo.donacion]
                val modalidad = eleccion?.modalidad ?: return@mapNotNull null
                EleccionDonacion(
                    donacion = grupo.donacion,
                    modalidadEntrega = modalidad,
                    direccionEntrega = eleccion.direccion.trim()
                        .takeIf { modalidad == Donacion.MODALIDAD_DOMICILIO },
                )
            }
        )

    /**
     * Reparte las claves de extraerErroresCarrito: "items.<id>" al ítem, "donaciones.<id>" al grupo y el resto
     * ("carrito.N" y claves desconocidas) al aviso general. Si no hubo ninguna, el mensaje de la respuesta.
     */
    fun repartir(error: Resultado.Error): ErroresEnvio {
        val porItem = linkedMapOf<Int, String>()
        val porDonacion = linkedMapOf<Int, String>()
        val generales = mutableListOf<String>()
        error.erroresCampo.forEach { (clave, mensaje) ->
            val item = clave.removePrefix(PREFIJO_ERROR_ITEM).takeIf { clave.startsWith(PREFIJO_ERROR_ITEM) }?.toIntOrNull()
            val donacion = clave.removePrefix(PREFIJO_ERROR_DONACION)
                .takeIf { clave.startsWith(PREFIJO_ERROR_DONACION) }?.toIntOrNull()
            when {
                item != null -> porItem[item] = mensaje
                donacion != null -> porDonacion[donacion] = mensaje
                else -> generales += mensaje
            }
        }
        if (porItem.isEmpty() && porDonacion.isEmpty() && generales.isEmpty()) generales += error.mensaje
        return ErroresEnvio(porItem, porDonacion, generales.distinct())
    }

    /** Los errores de ítems o donaciones que ya no están en el carrito pasan al aviso general (no se pierden). */
    fun ajustar(errores: ErroresEnvio, carrito: Carrito): ErroresEnvio {
        val donaciones = carrito.grupos.mapTo(HashSet()) { it.donacion }
        val items = carrito.grupos.flatMap { it.items.orEmpty() }.mapTo(HashSet()) { it.id }
        val (itemsQueEstan, itemsQueNo) = errores.porItem.entries.partition { it.key in items }
        val (gruposQueEstan, gruposQueNo) = errores.porDonacion.entries.partition { it.key in donaciones }
        return ErroresEnvio(
            porItem = itemsQueEstan.associate { it.key to it.value },
            porDonacion = gruposQueEstan.associate { it.key to it.value },
            generales = (errores.generales + itemsQueNo.map { it.value } + gruposQueNo.map { it.value }).distinct(),
        )
    }
}
