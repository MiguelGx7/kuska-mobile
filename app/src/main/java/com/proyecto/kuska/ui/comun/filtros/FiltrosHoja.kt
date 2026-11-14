package com.proyecto.kuska.ui.comun.filtros

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** El valor es una clave de la pantalla, independiente de la etiqueta que se muestra. */
data class OpcionFiltro(val valor: String, val etiqueta: String)

/** "Todas" (null) la agrega la hoja antes de estas opciones. */
data class GrupoFiltro(
    val clave: String,
    val titulo: String,
    val opciones: List<OpcionFiltro>,
    val cargando: Boolean = false,
    val error: String? = null,
)

data class EstadoFiltrosHoja(
    val grupos: List<GrupoFiltro> = emptyList(),
    /** Solo contiene dimensiones activas: la ausencia de una clave significa "Todas". */
    val aplicados: Map<String, String> = emptyMap(),
    /** null = hoja cerrada; un mapa vacío es una edición abierta sin filtros. */
    val borrador: Map<String, String>? = null,
) {
    val contador: Int get() = aplicados.size
}

/**
 * Selección transaccional sin dependencias de vistas ni de red. Vive en el ViewModel de la pantalla.
 * Abrir otra vez durante una recreación conserva el borrador; cerrar realmente lo descarta.
 */
class FiltrosHoja {
    private val _estado = MutableStateFlow(EstadoFiltrosHoja())
    val estado: StateFlow<EstadoFiltrosHoja> = _estado.asStateFlow()

    /** Actualiza carga/opciones sin borrar selecciones, incluso si una categoría ya no está disponible. */
    fun actualizarGrupos(grupos: List<GrupoFiltro>) {
        require(grupos.map { it.clave }.distinct().size == grupos.size)
        require(grupos.all { grupo -> grupo.opciones.map { it.valor }.distinct().size == grupo.opciones.size })
        _estado.update { it.copy(grupos = grupos.toList()) }
    }

    /** Para filtros recibidos desde fuera de la hoja. No modifica una edición en curso. */
    fun fijarAplicados(seleccion: Map<String, String>) {
        _estado.update { it.copy(aplicados = seleccion.toMap()) }
    }

    fun abrir() {
        _estado.update { if (it.borrador != null) it else it.copy(borrador = it.aplicados.toMap()) }
    }

    fun seleccionar(clave: String, valor: String?) {
        val grupo = _estado.value.grupos.firstOrNull { it.clave == clave } ?: return
        if (valor != null && (grupo.cargando || grupo.error != null || grupo.opciones.none { it.valor == valor })) return
        _estado.update { actual ->
            val borrador = actual.borrador ?: return@update actual
            actual.copy(borrador = if (valor == null) borrador - clave else borrador + (clave to valor))
        }
    }

    /** Limpia únicamente el borrador. La pantalla no consulta hasta aplicar. */
    fun limpiar() {
        _estado.update { if (it.borrador == null) it else it.copy(borrador = emptyMap()) }
    }

    /** Devuelve la selección completa para que la pantalla decida si necesita consultar. */
    fun aplicar(): Map<String, String>? {
        val seleccion = _estado.value.borrador ?: return null
        _estado.update { it.copy(aplicados = seleccion.toMap(), borrador = null) }
        return seleccion.toMap()
    }

    fun descartar() {
        _estado.update { it.copy(borrador = null) }
    }
}
