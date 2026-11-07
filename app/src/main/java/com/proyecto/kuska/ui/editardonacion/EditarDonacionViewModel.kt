package com.proyecto.kuska.ui.editardonacion

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.gson.JsonObject
import com.proyecto.kuska.KumoraApp
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.data.repositorio.DonacionesRepositorio
import com.proyecto.kuska.ui.comun.ErroresAccion
import com.proyecto.kuska.ui.comun.TextoUi
import com.proyecto.kuska.ui.creardonacion.CampoDonacion
import com.proyecto.kuska.ui.creardonacion.ValidacionDonacion
import com.proyecto.kuska.ui.creardonacion.ValoresDatosDonacion
import com.proyecto.kuska.ui.detalledonacion.DetalleDonacionFragment
import com.proyecto.kuska.util.Resultado
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditarDonacionEstado(
    /** La donación tal como está en el servidor (null mientras carga). */
    val original: Donacion? = null,
    val cargando: Boolean = false,
    /** Error al cargarla (pantalla completa con "Reintentar"). */
    val errorCarga: String? = null,
    val titulo: String = "",
    val descripcion: String = "",
    val direccion: String = "",
    val modalidad: String? = null,
    val errores: Map<CampoDonacion, TextoUi> = emptyMap(),
    /** Mensaje para el aviso rojo. */
    val errorApi: String? = null,
    val guardando: Boolean = false,
) {
    val valores get() = ValoresDatosDonacion(titulo, descripcion, direccion, modalidad)

    /** Solo lo que cambió respecto a [original]; vacío si no hay cambios o aún no cargó. */
    val cambios: JsonObject
        get() = original?.let { CambiosDonacion.construir(it, titulo, descripcion, direccion, modalidad) } ?: JsonObject()

    val hayCambios get() = cambios.size() > 0
}

sealed interface EventoEditar {
    data object Guardada : EventoEditar

    /** La donación ya no se puede editar: se cierra la pantalla y el detalle muestra [mensaje] en su aviso rojo. */
    data class YaNoEditable(val mensaje: String) : EventoEditar
}

/** Editar título, descripción, dirección y modalidad de una donación Pendiente (RN-12). */
class EditarDonacionViewModel(
    private val app: Application,
    private val repositorio: DonacionesRepositorio,
    estadoGuardado: SavedStateHandle,
) : ViewModel() {

    private val id: Int = requireNotNull(estadoGuardado.get<Int>(DetalleDonacionFragment.ARG_ID))

    private val _estado = MutableStateFlow(EditarDonacionEstado())
    val estado: StateFlow<EditarDonacionEstado> = _estado.asStateFlow()

    private val _eventos = Channel<EventoEditar>(Channel.BUFFERED)
    val eventos: Flow<EventoEditar> = _eventos.receiveAsFlow()

    init {
        cargar()
    }

    /** Pide la donación al abrir: los campos parten de lo que hay en el servidor, no de lo que mostraba el detalle. */
    fun cargar() {
        if (_estado.value.cargando) return
        _estado.update { it.copy(cargando = true, errorCarga = null) }
        viewModelScope.launch {
            when (val resultado = repositorio.obtenerDonacion(id)) {
                is Resultado.Exito -> {
                    val donacion = resultado.datos
                    if (donacion.estado != Donacion.ESTADO_PENDIENTE) {
                        _eventos.send(EventoEditar.YaNoEditable(app.getString(R.string.editar_ya_no_pendiente)))
                        return@launch
                    }
                    _estado.update {
                        it.copy(
                            original = donacion,
                            cargando = false,
                            titulo = donacion.titulo,
                            descripcion = donacion.descripcion.orEmpty(),
                            direccion = donacion.direccionRecogida.orEmpty(),
                            modalidad = donacion.modalidadEntrega,
                        )
                    }
                }
                is Resultado.Error -> {
                    if (resultado.codigo == 404) {
                        _eventos.send(EventoEditar.YaNoEditable(resultado.mensaje))
                        return@launch
                    }
                    _estado.update { it.copy(cargando = false, errorCarga = resultado.mensaje) }
                }
            }
        }
    }

    /** Se ignora si el valor no cambió (p. ej. cuando la vista restaura su texto al rotar). */
    fun cambiarCampo(campo: CampoDonacion, valor: String) {
        val actual = _estado.value
        if (actual.original == null) return
        val anterior = when (campo) {
            CampoDonacion.TITULO -> actual.titulo
            CampoDonacion.DESCRIPCION -> actual.descripcion
            CampoDonacion.DIRECCION -> actual.direccion
            CampoDonacion.MODALIDAD -> return
        }
        if (anterior == valor) return
        _estado.update {
            val conValor = when (campo) {
                CampoDonacion.TITULO -> it.copy(titulo = valor)
                CampoDonacion.DESCRIPCION -> it.copy(descripcion = valor)
                else -> it.copy(direccion = valor)
            }
            conValor.copy(errores = it.errores - campo)
        }
    }

    fun elegirModalidad(modalidad: String?) {
        val actual = _estado.value
        if (actual.original == null || actual.modalidad == modalidad) return
        _estado.update { it.copy(modalidad = modalidad, errores = it.errores - CampoDonacion.MODALIDAD) }
    }

    fun guardar() {
        val actual = _estado.value
        if (actual.guardando || !actual.hayCambios) return
        val errores = ValidacionDonacion.validar(actual.titulo, actual.direccion, actual.modalidad)
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(errores = errores, errorApi = null) }
            return
        }
        val cambios = actual.cambios
        _estado.update { it.copy(guardando = true, errores = emptyMap(), errorApi = null) }
        viewModelScope.launch {
            when (val resultado = repositorio.editarDonacion(id, cambios)) {
                // Se deja "guardando" mientras la pantalla se cierra.
                is Resultado.Exito -> _eventos.send(EventoEditar.Guardada)
                is Resultado.Error -> mostrarError(resultado)
            }
        }
    }

    /** Errores de campo a su campo; el resto al aviso rojo, salvo que la donación ya no se pueda editar. */
    private suspend fun mostrarError(error: Resultado.Error) {
        val separados = ErroresAccion.separar(error.erroresCampo, CAMPOS_API)
        val aviso = ErroresAccion.aviso(error, separados)
        if (ErroresAccion.debeRevisarEstado(error) &&
            !ErroresAccion.sigueEditable(repositorio.obtenerDonacion(id))
        ) {
            _eventos.send(EventoEditar.YaNoEditable(aviso ?: error.mensaje))
            return
        }
        _estado.update {
            it.copy(
                guardando = false,
                errores = separados.porCampo.mapValues { (_, mensaje) -> TextoUi.Texto(mensaje) },
                errorApi = aviso,
            )
        }
    }

    companion object {
        /** Campos de PatchedDonacionEditarRequest en la respuesta de error. */
        private val CAMPOS_API = mapOf(
            "titulo" to CampoDonacion.TITULO,
            "descripcion" to CampoDonacion.DESCRIPCION,
            "direccion_recogida" to CampoDonacion.DIRECCION,
            "modalidad_entrega" to CampoDonacion.MODALIDAD,
        )

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KumoraApp
                EditarDonacionViewModel(app, app.donacionesRepositorio, createSavedStateHandle())
            }
        }
    }
}
