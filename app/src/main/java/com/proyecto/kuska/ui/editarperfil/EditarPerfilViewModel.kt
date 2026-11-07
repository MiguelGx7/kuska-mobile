package com.proyecto.kuska.ui.editarperfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.gson.JsonObject
import com.proyecto.kuska.KumoraApp
import com.proyecto.kuska.data.api.modelos.Perfil
import com.proyecto.kuska.data.repositorio.PerfilRepositorio
import com.proyecto.kuska.ui.comun.ErroresAccion
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

data class EditarPerfilEstado(
    /** El perfil tal como está en el servidor (null mientras carga). */
    val original: Perfil? = null,
    val cargando: Boolean = false,
    /** Error al cargarlo (pantalla completa con "Reintentar"). */
    val errorCarga: String? = null,
    /** Código del error de pantalla completa (null = sin conexión): elige la hormiga (IlustracionEstado). */
    val codigoError: Int? = null,
    val valores: ValoresPerfil = ValoresPerfil(),
    val errores: Map<CampoPerfil, TextoUi> = emptyMap(),
    /** Mensaje para el aviso rojo. */
    val errorApi: String? = null,
    val guardando: Boolean = false,
) {
    /** Solo lo que cambió respecto a [original]; vacío si no hay cambios o aún no cargó. */
    val cambios: JsonObject get() = original?.let { CambiosPerfil.construir(it, valores) } ?: JsonObject()

    val hayCambios get() = cambios.size() > 0
}

sealed interface EventoEditarPerfil {
    data object Guardado : EventoEditarPerfil
}

/** Editar nombres, apellidos, teléfono y dirección (HU-013, RN-04a). */
class EditarPerfilViewModel(private val repositorio: PerfilRepositorio) : ViewModel() {

    private val _estado = MutableStateFlow(EditarPerfilEstado())
    val estado: StateFlow<EditarPerfilEstado> = _estado.asStateFlow()

    private val _eventos = Channel<EventoEditarPerfil>(Channel.BUFFERED)
    val eventos: Flow<EventoEditarPerfil> = _eventos.receiveAsFlow()

    init {
        cargar()
    }

    /** Pide el perfil al abrir: los campos parten de lo que hay en el servidor. */
    fun cargar() {
        if (_estado.value.cargando) return
        _estado.update { it.copy(cargando = true, errorCarga = null, codigoError = null) }
        viewModelScope.launch {
            when (val resultado = repositorio.obtenerPerfil()) {
                is Resultado.Exito -> _estado.update {
                    it.copy(original = resultado.datos, cargando = false, valores = ValoresPerfil.de(resultado.datos))
                }
                is Resultado.Error -> _estado.update { it.copy(cargando = false, errorCarga = resultado.mensaje, codigoError = resultado.codigo) }
            }
        }
    }

    /** Se ignora si el valor no cambió (p. ej. cuando la vista restaura su texto al rotar). */
    fun cambiarCampo(campo: CampoPerfil, valor: String) {
        val actual = _estado.value
        if (actual.original == null || actual.guardando || actual.valores.valor(campo) == valor) return
        _estado.update { it.copy(valores = it.valores.con(campo, valor), errores = it.errores - campo) }
    }

    fun guardar() {
        val actual = _estado.value
        if (actual.guardando || !actual.hayCambios) return
        val errores = CambiosPerfil.validar(actual.valores)
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(errores = errores, errorApi = null) }
            return
        }
        val cambios = actual.cambios
        _estado.update { it.copy(guardando = true, errores = emptyMap(), errorApi = null) }
        viewModelScope.launch {
            when (val resultado = repositorio.editarPerfil(cambios)) {
                // Se deja "guardando" mientras la pantalla se cierra.
                is Resultado.Exito -> _eventos.send(EventoEditarPerfil.Guardado)
                is Resultado.Error -> {
                    val separados = ErroresAccion.separar(resultado.erroresCampo, CambiosPerfil.CAMPOS_API)
                    _estado.update {
                        it.copy(
                            guardando = false,
                            errores = separados.porCampo.mapValues { (_, mensaje) -> TextoUi.Texto(mensaje) },
                            errorApi = ErroresAccion.aviso(resultado, separados),
                        )
                    }
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                EditarPerfilViewModel((this[APPLICATION_KEY] as KumoraApp).perfilRepositorio)
            }
        }
    }
}
