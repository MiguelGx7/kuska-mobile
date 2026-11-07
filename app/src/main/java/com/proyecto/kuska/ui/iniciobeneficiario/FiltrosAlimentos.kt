package com.proyecto.kuska.ui.iniciobeneficiario

import com.proyecto.kuska.data.api.modelos.Donacion

/**
 * Búsqueda y filtros de alimentos disponibles (HU-039 a HU-041). [categoria] y [modalidad] null = Todas.
 * Recoger y Domicilio ya incluyen las donaciones "Ambas": lo hace el servidor.
 */
data class FiltrosAlimentos(
    /** Lo escrito en el buscador, tal cual. */
    val busqueda: String = "",
    val categoria: Int? = null,
    val modalidad: String? = null,
) {
    /** ?search=: sin espacios en los extremos; vacía no se envía. */
    val parametroBusqueda: String? get() = busqueda.trim().takeIf { it.isNotEmpty() }

    /** Hay algo que reduce la lista: el vacío dice "No encontramos alimentos con ese filtro.". */
    val activos: Boolean get() = parametroBusqueda != null || categoria != null || modalidad != null

    /** Dos filtros piden lo mismo al servidor (p. ej. "arroz" y "arroz "): no hace falta recargar. */
    fun mismaConsulta(otros: FiltrosAlimentos) =
        parametroBusqueda == otros.parametroBusqueda && categoria == otros.categoria && modalidad == otros.modalidad

    companion object {
        /** Opciones del filtro por modalidad: Todas, Recoger y Domicilio. */
        val MODALIDADES: List<String?> = listOf(null, Donacion.MODALIDAD_RECOGER, Donacion.MODALIDAD_DOMICILIO)

        /** Espera tras la última tecla antes de buscar. */
        const val ESPERA_BUSQUEDA_MS = 400L
    }
}
