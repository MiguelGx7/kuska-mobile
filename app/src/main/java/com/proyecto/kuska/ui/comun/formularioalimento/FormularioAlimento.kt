package com.proyecto.kuska.ui.comun.formularioalimento

import com.google.android.material.datepicker.MaterialDatePicker
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.CategoriaPublica
import com.proyecto.kuska.ui.comun.TextoUi
import com.proyecto.kuska.ui.creardonacion.AlimentoBorrador
import com.proyecto.kuska.ui.creardonacion.CampoAlimento
import com.proyecto.kuska.ui.creardonacion.ValidacionAlimento
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** Alimento que se está agregando ([indice] = -1) o editando. */
data class EdicionAlimento(
    val indice: Int,
    val alimento: AlimentoBorrador,
    val errores: Map<CampoAlimento, TextoUi> = emptyMap(),
    val procesandoFoto: Boolean = false,
)

/**
 * Estado y reglas del formulario de un alimento, sin saber a dónde se guarda. Lo usa cada ViewModel que
 * muestra el formulario (crear donación, agregar alimento a una donación Pendiente).
 *
 * [borrarFoto] borra una foto temporal; [fotoEnUso] dice si una foto la usa un alimento ya guardado
 * (esa no se borra al reemplazarla ni al cancelar). [hoyUtc] es la medianoche UTC de hoy.
 */
class FormularioAlimento(
    private val scope: CoroutineScope,
    private val borrarFoto: (String?) -> Unit,
    private val fotoEnUso: (String) -> Boolean = { false },
    private val hoyUtc: () -> Long = { MaterialDatePicker.todayInUtcMilliseconds() },
) {
    private val _edicion = MutableStateFlow<EdicionAlimento?>(null)
    val edicion: StateFlow<EdicionAlimento?> = _edicion.asStateFlow()

    /** Abre el formulario con [alimento] (null = vacío). Si ya se está editando ese [indice] (p. ej. al rotar), lo conserva. */
    fun iniciar(indice: Int, alimento: AlimentoBorrador?) {
        if (_edicion.value?.indice == indice) return
        _edicion.value = if (alimento != null) EdicionAlimento(indice, alimento) else EdicionAlimento(-1, AlimentoBorrador())
    }

    /** Nombre, descripción o cantidad. Se ignora si el valor no cambió (la vista restaura su texto al rotar). */
    fun cambiarCampo(campo: CampoAlimento, valor: String) {
        val edicion = _edicion.value ?: return
        val anterior = when (campo) {
            CampoAlimento.NOMBRE -> edicion.alimento.nombre
            CampoAlimento.DESCRIPCION -> edicion.alimento.descripcion
            CampoAlimento.CANTIDAD -> edicion.alimento.cantidad
            else -> return
        }
        if (anterior == valor) return
        actualizar(campo) {
            when (campo) {
                CampoAlimento.NOMBRE -> it.copy(nombre = valor)
                CampoAlimento.DESCRIPCION -> it.copy(descripcion = valor)
                else -> it.copy(cantidad = valor)
            }
        }
    }

    fun elegirCategoria(categoria: CategoriaPublica) = actualizar(CampoAlimento.CATEGORIA) {
        it.copy(categoriaId = categoria.id, categoriaNombre = categoria.nombre)
    }

    /** Al cambiar la unidad se vuelve a validar la cantidad ya escrita (entera o con decimales, RN-10a). */
    fun elegirUnidad(unidad: String) {
        val edicion = _edicion.value ?: return
        val alimento = edicion.alimento.copy(unidadMedida = unidad)
        val errorCantidad = alimento.cantidad.takeIf { it.isNotBlank() }
            ?.let { ValidacionAlimento.validarCantidad(it, unidad) }
        val errores = (edicion.errores - CampoAlimento.UNIDAD - CampoAlimento.CANTIDAD).let {
            if (errorCantidad != null) it + (CampoAlimento.CANTIDAD to errorCantidad) else it
        }
        _edicion.value = edicion.copy(alimento = alimento, errores = errores)
    }

    fun elegirFecha(fechaUtc: Long) = actualizar(CampoAlimento.FECHA) { it.copy(fechaVencimiento = fechaUtc) }

    /**
     * Reduce la foto elegida o tomada con [leer] (devuelve el archivo reducido o null si no se pudo leer).
     * [archivoCamara]: el original de la cámara, que se borra después de reducirlo.
     */
    fun procesarFoto(leer: suspend () -> File?, archivoCamara: File? = null) {
        if (_edicion.value == null) return
        _edicion.update { it?.copy(procesandoFoto = true) }
        scope.launch {
            val reducida = leer()
            archivoCamara?.delete()
            val edicion = _edicion.value
            if (edicion == null) {
                // El formulario se cerró mientras se reducía.
                borrarFoto(reducida?.path)
                return@launch
            }
            if (reducida == null) {
                _edicion.value = edicion.copy(
                    procesandoFoto = false,
                    errores = edicion.errores + (CampoAlimento.FOTO to TextoUi.Recurso(R.string.error_foto_no_leida)),
                )
                return@launch
            }
            borrarSiNoSeUsa(edicion.alimento.rutaFoto)
            _edicion.value = edicion.copy(
                alimento = edicion.alimento.copy(rutaFoto = reducida.path),
                errores = edicion.errores - CampoAlimento.FOTO,
                procesandoFoto = false,
            )
        }
    }

    /**
     * Valida el alimento. Si tiene errores los muestra y devuelve null; si no, devuelve la edición tal cual
     * (el formulario sigue abierto hasta [terminar]). Mientras se reduce una foto no valida.
     */
    fun validar(): EdicionAlimento? {
        val edicion = _edicion.value ?: return null
        if (edicion.procesandoFoto) return null
        val errores = ValidacionAlimento.validar(edicion.alimento, hoyUtc())
        if (errores.isNotEmpty()) {
            _edicion.value = edicion.copy(errores = errores)
            return null
        }
        return edicion
    }

    /** Errores que mandó el servidor para campos del formulario (se suman a los que ya hay). */
    fun mostrarErrores(errores: Map<CampoAlimento, TextoUi>) {
        if (errores.isEmpty()) return
        _edicion.update { it?.copy(errores = it.errores + errores) }
    }

    /** El alimento ya se guardó: cierra el formulario sin borrar su foto. */
    fun terminar() {
        _edicion.value = null
    }

    /** Sale sin guardar: borra la foto nueva si no la usa un alimento ya guardado. */
    fun cancelar() {
        val edicion = _edicion.value ?: return
        borrarSiNoSeUsa(edicion.alimento.rutaFoto)
        _edicion.value = null
    }

    private fun actualizar(campo: CampoAlimento, cambio: (AlimentoBorrador) -> AlimentoBorrador) {
        val edicion = _edicion.value ?: return
        _edicion.value = edicion.copy(alimento = cambio(edicion.alimento), errores = edicion.errores - campo)
    }

    private fun borrarSiNoSeUsa(ruta: String?) {
        if (ruta == null || fotoEnUso(ruta)) return
        borrarFoto(ruta)
    }
}
