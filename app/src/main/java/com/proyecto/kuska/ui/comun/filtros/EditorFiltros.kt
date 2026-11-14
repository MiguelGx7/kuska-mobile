package com.proyecto.kuska.ui.comun.filtros

/** Contrato del ViewModel: la hoja no conoce categorías, estados, repositorios ni consultas. */
interface EditorFiltros {
    val filtrosHoja: FiltrosHoja

    /** Confirma el borrador y lo traduce a los filtros propios de la pantalla. */
    fun aplicarFiltrosHoja()

    /** Reintenta las opciones de un grupo sin consultar el listado de la pantalla. */
    fun reintentarGrupoFiltro(clave: String)
}
