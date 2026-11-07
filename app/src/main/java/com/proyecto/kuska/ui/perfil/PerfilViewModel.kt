package com.proyecto.kuska.ui.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.proyecto.kuska.KumoraApp
import com.proyecto.kuska.data.api.modelos.Perfil
import com.proyecto.kuska.data.repositorio.AuthRepositorio
import com.proyecto.kuska.data.repositorio.PerfilRepositorio
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

data class PerfilEstado(
    val perfil: Perfil? = null,
    /** Carga sin datos todavía (pantalla completa). */
    val cargando: Boolean = false,
    /** Pull-to-refresh. */
    val refrescando: Boolean = false,
    /** Error sin datos (pantalla completa con "Reintentar"). */
    val error: String? = null,
    val cerrandoSesion: Boolean = false,
)

sealed interface EventoPerfil {
    /** Snackbar: éxito al volver de editar o cambiar la contraseña, o error al refrescar un perfil que ya se ve. */
    data class Mensaje(val texto: String) : EventoPerfil
}

/** Ver perfil (HU-012) y cerrar sesión (HU-015). */
class PerfilViewModel(
    private val repositorio: PerfilRepositorio,
    private val auth: AuthRepositorio,
) : ViewModel() {

    private val _estado = MutableStateFlow(PerfilEstado())
    val estado: StateFlow<PerfilEstado> = _estado.asStateFlow()

    private val _eventos = Channel<EventoPerfil>(Channel.BUFFERED)
    val eventos: Flow<EventoPerfil> = _eventos.receiveAsFlow()

    private var trabajo: Job? = null

    init {
        cargar()
    }

    fun reintentar() = cargar()

    fun refrescar() {
        if (_estado.value.cerrandoSesion) {
            _estado.update { it.copy(refrescando = false) }
            return
        }
        _estado.update { it.copy(refrescando = true) }
        cargar()
    }

    /** Volvió de editar o de cambiar la contraseña con [mensaje] de éxito: se muestra y se recarga. */
    fun recibirResultado(mensaje: String) {
        _eventos.trySend(EventoPerfil.Mensaje(mensaje))
        cargar()
    }

    private fun cargar() {
        trabajo?.cancel()
        _estado.update { it.copy(cargando = it.perfil == null, error = null) }
        trabajo = viewModelScope.launch {
            when (val resultado = repositorio.obtenerPerfil()) {
                is Resultado.Exito -> _estado.update {
                    it.copy(perfil = resultado.datos, cargando = false, refrescando = false)
                }
                is Resultado.Error -> {
                    val conDatos = _estado.value.perfil != null
                    _estado.update {
                        it.copy(cargando = false, refrescando = false, error = if (conDatos) null else resultado.mensaje)
                    }
                    if (conDatos) _eventos.send(EventoPerfil.Mensaje(resultado.mensaje))
                }
            }
        }
    }

    /** La API no tiene logout: se borra la sesión local y MainActivity vuelve al login. */
    fun cerrarSesion() {
        if (_estado.value.cerrandoSesion) return
        trabajo?.cancel()
        _estado.update { it.copy(cerrandoSesion = true, refrescando = false) }
        viewModelScope.launch { auth.cerrarSesion() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KumoraApp
                PerfilViewModel(app.perfilRepositorio, app.authRepositorio)
            }
        }
    }
}
