package com.proyecto.kuska.ui.iniciodonante

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.proyecto.kuska.KumoraApp
import com.proyecto.kuska.data.api.modelos.DashboardDonante
import com.proyecto.kuska.data.repositorio.DonacionesRepositorio
import com.proyecto.kuska.data.sesion.SesionManager
import com.proyecto.kuska.ui.comun.Saludo
import com.proyecto.kuska.util.Resultado
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InicioDonanteEstado(
    val dashboard: DashboardDonante? = null,
    /** Carga sin datos todavía (pantalla completa). */
    val cargando: Boolean = false,
    /** Pull-to-refresh. */
    val refrescando: Boolean = false,
    /** Error sin datos (pantalla completa con "Reintentar"). */
    val error: String? = null,
    /** Código del error de pantalla completa (null = sin conexión): elige la hormiga (IlustracionEstado). */
    val codigoError: Int? = null,
)

/** Inicio del donante (HU-035 a HU-037). Se recarga al volver a la pestaña, no al girar. */
class InicioDonanteViewModel(
    private val repositorio: DonacionesRepositorio,
    sesionManager: SesionManager,
) : ViewModel() {

    private val _estado = MutableStateFlow(InicioDonanteEstado())
    val estado: StateFlow<InicioDonanteEstado> = _estado.asStateFlow()

    /** Para "Hola, …". */
    val nombre: StateFlow<String?> = sesionManager.sesion
        .map { Saludo.nombre(it?.usuario) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Error al refrescar un inicio que ya se ve (va en un Snackbar). */
    private val _mensajes = Channel<String>(Channel.BUFFERED)
    val mensajes: Flow<String> = _mensajes.receiveAsFlow()

    private var trabajo: Job? = null
    private var recargarAlVolver = false

    init {
        cargar()
    }

    fun refrescar() {
        _estado.update { it.copy(refrescando = true) }
        cargar()
    }

    fun reintentar() = cargar()

    /** Se fue a otra pestaña, a otra pantalla o a segundo plano (no al girar). */
    fun marcarSalida() {
        recargarAlVolver = true
    }

    fun alVolver() {
        if (!recargarAlVolver) return
        recargarAlVolver = false
        cargar()
    }

    private fun cargar() {
        trabajo?.cancel()
        _estado.update { it.copy(cargando = it.dashboard == null, error = null, codigoError = null) }
        trabajo = viewModelScope.launch {
            when (val resultado = repositorio.obtenerDashboard()) {
                is Resultado.Exito -> _estado.update {
                    it.copy(dashboard = resultado.datos, cargando = false, refrescando = false)
                }
                is Resultado.Error -> {
                    val conDatos = _estado.value.dashboard != null
                    _estado.update {
                        it.copy(cargando = false, refrescando = false, error = if (conDatos) null else resultado.mensaje, codigoError = if (conDatos) null else resultado.codigo)
                    }
                    if (conDatos) _mensajes.send(resultado.mensaje)
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KumoraApp
                InicioDonanteViewModel(app.donacionesRepositorio, app.sesionManager)
            }
        }
    }
}
