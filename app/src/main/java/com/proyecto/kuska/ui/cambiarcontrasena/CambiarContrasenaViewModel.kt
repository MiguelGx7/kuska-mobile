package com.proyecto.kuska.ui.cambiarcontrasena

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.proyecto.kuska.KumoraApp
import com.proyecto.kuska.data.repositorio.PerfilRepositorio
import com.proyecto.kuska.data.repositorio.SesionTrasCambio
import com.proyecto.kuska.ui.comun.TextoUi
import com.proyecto.kuska.util.Resultado
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Las contraseñas solo viven aquí (no en el SavedStateHandle): al cerrar la pantalla se pierden. */
data class CambiarContrasenaEstado(
    val actual: String = "",
    val nueva: String = "",
    val confirmar: String = "",
    val errores: Map<CampoContrasena, TextoUi> = emptyMap(),
    /** Mensaje para el aviso rojo. */
    val errorApi: String? = null,
    val guardando: Boolean = false,
)

sealed interface EventoCambiarContrasena {
    /** Contraseña cambiada y sesión renovada: vuelve a Perfil con "Contraseña actualizada.". */
    data object Cambiada : EventoCambiarContrasena
}

/** Cambiar contraseña (HU-014). */
class CambiarContrasenaViewModel(private val repositorio: PerfilRepositorio) : ViewModel() {

    private val _estado = MutableStateFlow(CambiarContrasenaEstado())
    val estado: StateFlow<CambiarContrasenaEstado> = _estado.asStateFlow()

    private val _eventos = Channel<EventoCambiarContrasena>(Channel.BUFFERED)
    val eventos: Flow<EventoCambiarContrasena> = _eventos.receiveAsFlow()

    fun cambiarCampo(campo: CampoContrasena, valor: String) {
        val actual = _estado.value
        if (actual.guardando) return
        val anterior = when (campo) {
            CampoContrasena.ACTUAL -> actual.actual
            CampoContrasena.NUEVA -> actual.nueva
            CampoContrasena.CONFIRMAR -> actual.confirmar
        }
        if (anterior == valor) return
        _estado.update {
            val conValor = when (campo) {
                CampoContrasena.ACTUAL -> it.copy(actual = valor)
                CampoContrasena.NUEVA -> it.copy(nueva = valor)
                CampoContrasena.CONFIRMAR -> it.copy(confirmar = valor)
            }
            conValor.copy(errores = it.errores - campo)
        }
    }

    fun guardar() {
        val actual = _estado.value
        if (actual.guardando) return
        val errores = ValidacionCambioContrasena.validar(actual.actual, actual.nueva, actual.confirmar)
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(errores = errores, errorApi = null) }
            return
        }
        _estado.update { it.copy(guardando = true, errores = emptyMap(), errorApi = null) }
        viewModelScope.launch {
            when (val resultado = repositorio.cambiarContrasena(actual.actual, actual.nueva)) {
                // Se deja "guardando" mientras la pantalla se cierra (o MainActivity vuelve al login).
                is Resultado.Exito -> when (resultado.datos) {
                    SesionTrasCambio.RENOVADA -> _eventos.send(EventoCambiarContrasena.Cambiada)
                    SesionTrasCambio.CERRADA -> _estado.update { CambiarContrasenaEstado(guardando = true) }
                }
                is Resultado.Error -> {
                    val errores = ValidacionCambioContrasena.repartir(resultado)
                    _estado.update { it.copy(guardando = false, errores = errores.porCampo, errorApi = errores.aviso) }
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                CambiarContrasenaViewModel((this[APPLICATION_KEY] as KumoraApp).perfilRepositorio)
            }
        }
    }
}
