package com.proyecto.kuska.ui.detallealimento

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.gson.Gson
import com.proyecto.kuska.KumoraApp
import com.proyecto.kuska.data.api.modelos.AlimentoDisponible
import com.proyecto.kuska.data.repositorio.BeneficiarioRepositorio
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
import java.math.BigDecimal

data class DetalleAlimentoEstado(
    val alimento: AlimentoDisponible,
    /** Null si no hay nada que pedir: el selector y el botón no se muestran. */
    val maximo: BigDecimal?,
    val cantidad: String,
    val errorCantidad: TextoUi? = null,
    /** Aviso rojo: reglas del servidor, sin conexión, 429... */
    val aviso: String? = null,
    val agregando: Boolean = false,
)

sealed interface EventoDetalleAlimento {
    /** Vuelve a la lista con "Agregado al carrito." y "Ver carrito". */
    data object Agregado : EventoDetalleAlimento

    /** Error de regla: la lista se recarga al volver. */
    data object RecargarListaAlVolver : EventoDetalleAlimento
}

/** Detalle de un alimento disponible (HU-042 a HU-044). Los datos vienen de la lista (no hay endpoint de detalle). */
class DetalleAlimentoViewModel(
    private val repositorio: BeneficiarioRepositorio,
    estadoGuardado: SavedStateHandle,
) : ViewModel() {

    private val _estado: MutableStateFlow<DetalleAlimentoEstado>
    val estado: StateFlow<DetalleAlimentoEstado>

    private val _eventos = Channel<EventoDetalleAlimento>(Channel.BUFFERED)
    val eventos: Flow<EventoDetalleAlimento> = _eventos.receiveAsFlow()

    init {
        val json = requireNotNull(estadoGuardado.get<String>(DetalleAlimentoFragment.ARG_ALIMENTO))
        val alimento = Gson().fromJson(json, AlimentoDisponible::class.java)
        val maximo = CantidadPedido.maximo(alimento)
        _estado = MutableStateFlow(
            DetalleAlimentoEstado(alimento, maximo, cantidad = maximo?.let(CantidadPedido::inicial).orEmpty())
        )
        estado = _estado.asStateFlow()
    }

    /** Se ignora si el valor no cambió (p. ej. cuando la vista restaura su texto al rotar). */
    fun cambiarCantidad(texto: String) {
        val actual = _estado.value
        if (actual.agregando || actual.cantidad == texto) return
        _estado.update { it.copy(cantidad = texto, errorCantidad = null) }
    }

    fun sumar() = ajustar(CantidadPedido::sumar)

    fun restar() = ajustar(CantidadPedido::restar)

    private fun ajustar(cambio: (String, BigDecimal) -> String) {
        val actual = _estado.value
        val maximo = actual.maximo ?: return
        if (actual.agregando) return
        _estado.update { it.copy(cantidad = cambio(it.cantidad, maximo), errorCantidad = null) }
    }

    fun agregar() {
        val actual = _estado.value
        val maximo = actual.maximo ?: return
        if (actual.agregando) return
        CantidadPedido.validar(actual.cantidad, actual.alimento.unidadMedida, maximo)?.let { error ->
            _estado.update { it.copy(errorCantidad = error, aviso = null) }
            return
        }
        _estado.update { it.copy(agregando = true, errorCantidad = null, aviso = null) }
        viewModelScope.launch {
            when (val resultado = repositorio.agregarAlCarrito(CantidadPedido.cuerpo(actual.alimento.id, actual.cantidad))) {
                // Se deja "agregando" mientras la pantalla se cierra.
                is Resultado.Exito -> _eventos.send(EventoDetalleAlimento.Agregado)
                is Resultado.Error -> when (val fallo = CantidadPedido.clasificar(resultado)) {
                    is FalloCarrito.EnCampo -> _estado.update {
                        it.copy(agregando = false, errorCantidad = TextoUi.Texto(fallo.mensaje))
                    }
                    is FalloCarrito.Aviso -> {
                        _estado.update { it.copy(agregando = false, aviso = fallo.mensaje) }
                        if (fallo.recargarLista) _eventos.send(EventoDetalleAlimento.RecargarListaAlVolver)
                    }
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                DetalleAlimentoViewModel(
                    (this[APPLICATION_KEY] as KumoraApp).beneficiarioRepositorio,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
