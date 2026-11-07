package com.proyecto.kuska.ui.comun.formularioalimento

import com.proyecto.kuska.data.api.modelos.CategoriaPublica
import com.proyecto.kuska.util.Resultado
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoCategorias(
    val lista: List<CategoriaPublica> = emptyList(),
    val cargando: Boolean = false,
    val error: String? = null,
)

/** Categorías activas para la lista desplegable del alimento (GET categorias/). */
class CategoriasAlimento(
    private val scope: CoroutineScope,
    private val obtener: suspend () -> Resultado<List<CategoriaPublica>>,
) {
    private val _estado = MutableStateFlow(EstadoCategorias())
    val estado: StateFlow<EstadoCategorias> = _estado.asStateFlow()

    fun cargar() {
        if (_estado.value.cargando) return
        _estado.update { it.copy(cargando = true, error = null) }
        scope.launch {
            when (val resultado = obtener()) {
                is Resultado.Exito -> _estado.update { it.copy(cargando = false, lista = resultado.datos) }
                is Resultado.Error -> _estado.update { it.copy(cargando = false, error = resultado.mensaje) }
            }
        }
    }
}
