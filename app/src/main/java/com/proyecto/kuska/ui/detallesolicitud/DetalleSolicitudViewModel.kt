package com.proyecto.kuska.ui.detallesolicitud

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.proyecto.kuska.KumoraApp
import com.proyecto.kuska.data.api.modelos.MiSolicitud
import com.proyecto.kuska.data.repositorio.BeneficiarioRepositorio
import com.proyecto.kuska.ui.comun.ErroresAccion
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

data class DetalleSolicitudEstado(
    val solicitud: MiSolicitud? = null,
    /** Carga sin datos todavía (pantalla completa). */
    val cargando: Boolean = false,
    /** Pull-to-refresh. */
    val refrescando: Boolean = false,
    /** Error sin datos (pantalla completa con "Reintentar"). */
    val error: String? = null,
    val cancelando: Boolean = false,
    /** Aviso rojo del encabezado: error al cancelar. */
    val aviso: String? = null,
) {
    /** Hay una acción en curso: las demás quedan deshabilitadas. */
    val ocupado get() = cancelando
}

sealed interface EventoSolicitud {
    /** Error al refrescar un detalle que ya se ve (va en un Snackbar). */
    data class Mensaje(val texto: String) : EventoSolicitud

    /** Cancelada (RN-23): Snackbar "Solicitud cancelada.". */
    data object Cancelada : EventoSolicitud
}

class DetalleSolicitudViewModel(
    private val repositorio: BeneficiarioRepositorio,
    estadoGuardado: SavedStateHandle,
) : ViewModel() {

    private val id: Int = requireNotNull(estadoGuardado.get<Int>(DetalleSolicitudFragment.ARG_ID))

    private val _estado = MutableStateFlow(DetalleSolicitudEstado())
    val estado: StateFlow<DetalleSolicitudEstado> = _estado.asStateFlow()

    private val _eventos = Channel<EventoSolicitud>(Channel.BUFFERED)
    val eventos: Flow<EventoSolicitud> = _eventos.receiveAsFlow()

    private var trabajo: Job? = null

    init {
        cargar()
    }

    /** Pull-to-refresh. También quita el aviso rojo de la última acción. */
    fun refrescar() {
        if (_estado.value.ocupado) {
            _estado.update { it.copy(refrescando = false) }
            return
        }
        _estado.update { it.copy(refrescando = true, aviso = null) }
        cargar()
    }

    fun reintentar() = cargar()

    private fun cargar() {
        trabajo?.cancel()
        _estado.update { it.copy(cargando = it.solicitud == null, error = null) }
        trabajo = viewModelScope.launch {
            when (val resultado = repositorio.obtenerMiSolicitud(id)) {
                is Resultado.Exito -> _estado.update {
                    it.copy(solicitud = resultado.datos, cargando = false, refrescando = false)
                }
                is Resultado.Error -> {
                    val conDatos = _estado.value.solicitud != null
                    _estado.update {
                        it.copy(cargando = false, refrescando = false, error = if (conDatos) null else resultado.mensaje)
                    }
                    if (conDatos) _eventos.send(EventoSolicitud.Mensaje(resultado.mensaje))
                }
            }
        }
    }

    /**
     * RN-23: solo Pendiente. La respuesta ya es la solicitud cancelada, así que se muestra sin volver a pedirla.
     * Si falla, el mensaje va al aviso rojo; si puede que ya no esté Pendiente (400 de regla o 404), se recarga.
     */
    fun cancelar() {
        val actual = _estado.value
        val solicitud = actual.solicitud ?: return
        if (actual.ocupado || !ReglasSolicitud.puedeCancelar(solicitud)) return
        // Una recarga en curso no debe pisar la respuesta de la cancelación.
        trabajo?.cancel()
        _estado.update { it.copy(cancelando = true, aviso = null, refrescando = false, cargando = false) }
        viewModelScope.launch {
            when (val resultado = repositorio.cancelarMiSolicitud(id)) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(solicitud = resultado.datos, cancelando = false) }
                    _eventos.send(EventoSolicitud.Cancelada)
                }
                is Resultado.Error -> {
                    _estado.update { it.copy(cancelando = false, aviso = resultado.mensaje) }
                    if (ErroresAccion.debeRevisarEstado(resultado)) cargar()
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                DetalleSolicitudViewModel(
                    (this[APPLICATION_KEY] as KumoraApp).beneficiarioRepositorio,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
