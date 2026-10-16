package com.proyecto.kuska.ui.detalledonacion

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.proyecto.kuska.KumoraApp
import com.proyecto.kuska.data.api.modelos.AlimentoConSolicitudes
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.data.repositorio.DonacionesRepositorio
import com.proyecto.kuska.util.Resultado
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetalleDonacionEstado(
    val donacion: Donacion? = null,
    /** Carga sin datos todavía (pantalla completa). */
    val cargando: Boolean = false,
    /** Pull-to-refresh. */
    val refrescando: Boolean = false,
    /** Error sin datos (pantalla completa con "Reintentar"). */
    val error: String? = null,
    /** Solo si la donación no está Pendiente (RN-15). */
    val solicitudes: List<AlimentoConSolicitudes>? = null,
    val cargandoSolicitudes: Boolean = false,
    val errorSolicitudes: String? = null,
    val eliminando: Boolean = false,
    val errorEliminar: String? = null,
)

sealed interface EventoDetalle {
    data object Eliminada : EventoDetalle

    /** Error al refrescar un detalle que ya se ve (va en un Snackbar). */
    data class Mensaje(val texto: String) : EventoDetalle
}

class DetalleDonacionViewModel(
    private val repositorio: DonacionesRepositorio,
    estadoGuardado: SavedStateHandle,
) : ViewModel() {

    private val id: Int = requireNotNull(estadoGuardado.get<Int>(DetalleDonacionFragment.ARG_ID))

    private val _estado = MutableStateFlow(DetalleDonacionEstado())
    val estado: StateFlow<DetalleDonacionEstado> = _estado.asStateFlow()

    private val _eventos = Channel<EventoDetalle>(Channel.BUFFERED)
    val eventos: Flow<EventoDetalle> = _eventos.receiveAsFlow()

    private var trabajo: Job? = null

    init {
        cargar()
    }

    fun refrescar() {
        if (_estado.value.eliminando) {
            _estado.update { it.copy(refrescando = false) }
            return
        }
        _estado.update { it.copy(refrescando = true) }
        cargar()
    }

    fun reintentar() = cargar()

    fun reintentarSolicitudes() {
        val donacion = _estado.value.donacion ?: return
        trabajo?.cancel()
        trabajo = viewModelScope.launch { cargarSolicitudes(donacion) }
    }

    /** Primero la donación; luego, si no está Pendiente, sus solicitudes recibidas. */
    private fun cargar() {
        trabajo?.cancel()
        _estado.update { it.copy(cargando = it.donacion == null, error = null) }
        trabajo = viewModelScope.launch {
            when (val resultado = repositorio.obtenerDonacion(id)) {
                is Resultado.Exito -> {
                    val donacion = resultado.datos
                    _estado.update {
                        it.copy(
                            donacion = donacion,
                            cargando = false,
                            refrescando = false,
                            errorEliminar = null,
                            solicitudes = if (donacion.estado == Donacion.ESTADO_PENDIENTE) null else it.solicitudes,
                        )
                    }
                    if (donacion.estado != Donacion.ESTADO_PENDIENTE) cargarSolicitudes(donacion)
                }
                is Resultado.Error -> {
                    val conDatos = _estado.value.donacion != null
                    _estado.update {
                        it.copy(cargando = false, refrescando = false, error = if (conDatos) null else resultado.mensaje)
                    }
                    if (conDatos) _eventos.send(EventoDetalle.Mensaje(resultado.mensaje))
                }
            }
        }
    }

    private suspend fun cargarSolicitudes(donacion: Donacion) {
        _estado.update { it.copy(cargandoSolicitudes = true, errorSolicitudes = null) }
        when (val resultado = repositorio.solicitudesRecibidas(donacion.id)) {
            is Resultado.Exito -> _estado.update {
                it.copy(solicitudes = resultado.datos, cargandoSolicitudes = false)
            }
            is Resultado.Error -> _estado.update {
                it.copy(cargandoSolicitudes = false, errorSolicitudes = resultado.mensaje)
            }
        }
    }

    /** Solo Pendiente (RN-12). Si el servidor no lo permite, su mensaje va en el aviso rojo. */
    fun eliminar() {
        val actual = _estado.value
        if (actual.eliminando || actual.donacion?.estado != Donacion.ESTADO_PENDIENTE) return
        trabajo?.cancel()
        _estado.update { it.copy(eliminando = true, errorEliminar = null, refrescando = false) }
        viewModelScope.launch {
            when (val resultado = repositorio.eliminarDonacion(id)) {
                // Se deja "eliminando" mientras la pantalla se cierra.
                is Resultado.Exito -> _eventos.send(EventoDetalle.Eliminada)
                is Resultado.Error -> _estado.update {
                    it.copy(eliminando = false, errorEliminar = resultado.mensaje)
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                DetalleDonacionViewModel(
                    (this[APPLICATION_KEY] as KumoraApp).donacionesRepositorio,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
