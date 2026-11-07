package com.proyecto.kuska.ui.comun.formularioalimento

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Estado del botón "Guardar alimento" cuando guardar implica una petición. */
data class EstadoGuardadoAlimento(
    val guardando: Boolean = false,
    /** Mensaje para el aviso rojo del formulario. */
    val error: String? = null,
)

/**
 * Lo que necesita [FormularioAlimentoFragment] de su ViewModel. Cada ViewModel decide qué es "guardar":
 * agregarlo a la lista local (crear donación) o enviarlo a la API (agregar a una donación Pendiente).
 */
interface EditorAlimento {
    val formulario: FormularioAlimento
    val categorias: StateFlow<EstadoCategorias>
    val estadoGuardado: StateFlow<EstadoGuardadoAlimento>

    /** Emite cuando el alimento quedó guardado y el formulario debe cerrarse. */
    val alimentoGuardado: Flow<Unit>

    /** [indice]: posición del alimento a editar, o -1 para uno nuevo. */
    fun iniciarEdicion(indice: Int)

    fun cargarCategorias()

    /** [archivoCamara]: el original de la cámara, que se borra después de reducirlo. */
    fun procesarFoto(origen: Uri, archivoCamara: File? = null)

    fun guardarAlimento()

    fun cancelarEdicion()
}
