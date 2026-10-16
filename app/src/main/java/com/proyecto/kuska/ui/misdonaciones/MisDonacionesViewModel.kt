package com.proyecto.kuska.ui.misdonaciones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.proyecto.kuska.KumoraApp
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

data class MisDonacionesEstado(
    /** Estado elegido en los chips (null = Todas). */
    val filtro: String? = null,
    val donaciones: List<Donacion> = emptyList(),
    /** Ya respondió al menos una vez la primera página del filtro actual. */
    val cargada: Boolean = false,
    /** Primera carga del filtro (pantalla completa). */
    val cargando: Boolean = false,
    /** Pull-to-refresh. */
    val refrescando: Boolean = false,
    val cargandoMas: Boolean = false,
    val hayMas: Boolean = false,
    /** Error de la primera página (pantalla completa con "Reintentar"). */
    val error: String? = null,
    /** Error al pedir otra página (al final de la lista con "Reintentar"). */
    val errorMas: String? = null,
) {
    val vacia get() = cargada && donaciones.isEmpty() && error == null
}

class MisDonacionesViewModel(private val repositorio: DonacionesRepositorio) : ViewModel() {

    private val _estado = MutableStateFlow(MisDonacionesEstado())
    val estado: StateFlow<MisDonacionesEstado> = _estado.asStateFlow()

    /** Errores al refrescar una lista que ya se ve (van en un Snackbar). */
    private val _mensajes = Channel<String>(Channel.BUFFERED)
    val mensajes: Flow<String> = _mensajes.receiveAsFlow()

    private var trabajo: Job? = null
    private var paginaSiguiente = 1

    /** Se fue a otra pantalla (detalle o crear): al volver se recarga. Al rotar no. */
    private var recargarAlVolver = false

    init {
        cargarPrimera(pantallaCompleta = true)
    }

    fun cambiarFiltro(estado: String?) {
        if (_estado.value.filtro == estado) return
        _estado.value = MisDonacionesEstado(filtro = estado)
        cargarPrimera(pantallaCompleta = true)
    }

    /** Pull-to-refresh. */
    fun refrescar() {
        _estado.update { it.copy(refrescando = true) }
        cargarPrimera(pantallaCompleta = _estado.value.donaciones.isEmpty())
    }

    fun reintentar() = cargarPrimera(pantallaCompleta = true)

    fun marcarSalida() {
        recargarAlVolver = true
    }

    /** Al recrear la vista: si se volvió de otra pantalla, recarga sin tapar la lista. */
    fun alVolver() {
        if (!recargarAlVolver) return
        recargarAlVolver = false
        cargarPrimera(pantallaCompleta = _estado.value.donaciones.isEmpty())
    }

    /** Llegó al final de la lista. No reintenta solo si la página anterior falló. */
    fun cargarMas() {
        val actual = _estado.value
        if (!actual.hayMas || actual.cargando || actual.cargandoMas || actual.errorMas != null || trabajo?.isActive == true) {
            return
        }
        _estado.update { it.copy(cargandoMas = true) }
        val filtro = actual.filtro
        val pagina = paginaSiguiente
        trabajo = viewModelScope.launch {
            when (val resultado = repositorio.listarDonaciones(filtro, pagina)) {
                is Resultado.Exito -> {
                    paginaSiguiente = pagina + 1
                    _estado.update {
                        // Una donación puede repetirse si cambió el orden entre páginas.
                        val ids = it.donaciones.mapTo(HashSet()) { d -> d.id }
                        it.copy(
                            donaciones = it.donaciones + resultado.datos.resultados.filter { d -> d.id !in ids },
                            hayMas = resultado.datos.siguiente != null,
                            cargandoMas = false,
                        )
                    }
                }
                is Resultado.Error -> _estado.update { it.copy(cargandoMas = false, errorMas = resultado.mensaje) }
            }
        }
    }

    fun reintentarMas() {
        _estado.update { it.copy(errorMas = null) }
        cargarMas()
    }

    /** Pide la página 1 y reemplaza la lista. Cancela cualquier carga en curso. */
    private fun cargarPrimera(pantallaCompleta: Boolean) {
        trabajo?.cancel()
        _estado.update {
            it.copy(cargando = pantallaCompleta, cargandoMas = false, error = null, errorMas = null)
        }
        val filtro = _estado.value.filtro
        trabajo = viewModelScope.launch {
            val resultado = repositorio.listarDonaciones(filtro, 1)
            when (resultado) {
                is Resultado.Exito -> {
                    paginaSiguiente = 2
                    _estado.update {
                        it.copy(
                            donaciones = resultado.datos.resultados,
                            hayMas = resultado.datos.siguiente != null,
                            cargada = true,
                            cargando = false,
                            refrescando = false,
                        )
                    }
                }
                is Resultado.Error -> {
                    val conLista = _estado.value.donaciones.isNotEmpty()
                    _estado.update {
                        it.copy(
                            cargando = false,
                            refrescando = false,
                            error = if (conLista) null else resultado.mensaje,
                        )
                    }
                    if (conLista) _mensajes.send(resultado.mensaje)
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                MisDonacionesViewModel((this[APPLICATION_KEY] as KumoraApp).donacionesRepositorio)
            }
        }
    }
}
