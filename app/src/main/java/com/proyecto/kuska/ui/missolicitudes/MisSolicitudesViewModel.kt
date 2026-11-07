package com.proyecto.kuska.ui.missolicitudes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.proyecto.kuska.KumoraApp
import com.proyecto.kuska.data.api.modelos.MiSolicitud
import com.proyecto.kuska.data.repositorio.BeneficiarioRepositorio
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

data class MisSolicitudesEstado(
    /** Estado elegido en los chips (null = Todas). */
    val filtro: String? = null,
    val solicitudes: List<MiSolicitud> = emptyList(),
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
    /** Código del error de pantalla completa (null = sin conexión): elige la hormiga (IlustracionEstado). */
    val codigoError: Int? = null,
    /** Error al pedir otra página (al final de la lista con "Reintentar"). */
    val errorMas: String? = null,
) {
    val vacia get() = cargada && solicitudes.isEmpty() && error == null
}

class MisSolicitudesViewModel(private val repositorio: BeneficiarioRepositorio) : ViewModel() {

    private val _estado = MutableStateFlow(MisSolicitudesEstado())
    val estado: StateFlow<MisSolicitudesEstado> = _estado.asStateFlow()

    /** Errores al refrescar una lista que ya se ve (van en un Snackbar). */
    private val _mensajes = Channel<String>(Channel.BUFFERED)
    val mensajes: Flow<String> = _mensajes.receiveAsFlow()

    private var trabajo: Job? = null
    private var paginaSiguiente = 1

    /** Salió de la pantalla (detalle, otra pestaña o segundo plano): al volver se recarga. Al rotar no. */
    private var recargarAlVolver = false

    init {
        cargarPrimera(pantallaCompleta = true)
    }

    fun cambiarFiltro(estado: String?) {
        if (_estado.value.filtro == estado) return
        recargarAlVolver = false
        _estado.value = MisSolicitudesEstado(filtro = estado)
        cargarPrimera(pantallaCompleta = true)
    }

    /** Llegó desde "Ver mis solicitudes": filtro en Todas, para que se vea el pedido nuevo, y lista recargada. */
    fun mostrarTodas() {
        if (_estado.value.filtro != null) cambiarFiltro(null) else alVolver()
    }

    /** Pull-to-refresh. */
    fun refrescar() {
        _estado.update { it.copy(refrescando = true) }
        cargarPrimera(pantallaCompleta = _estado.value.solicitudes.isEmpty())
    }

    fun reintentar() = cargarPrimera(pantallaCompleta = true)

    fun marcarSalida() {
        recargarAlVolver = true
    }

    /** Al volver a la pantalla: si se había salido, recarga sin tapar la lista. */
    fun alVolver() {
        if (!recargarAlVolver) return
        recargarAlVolver = false
        cargarPrimera(pantallaCompleta = _estado.value.solicitudes.isEmpty())
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
            when (val resultado = repositorio.listarMisSolicitudes(filtro, pagina)) {
                is Resultado.Exito -> {
                    paginaSiguiente = pagina + 1
                    _estado.update {
                        // Una solicitud puede repetirse si cambió el orden entre páginas.
                        val ids = it.solicitudes.mapTo(HashSet()) { d -> d.id }
                        it.copy(
                            solicitudes = it.solicitudes + resultado.datos.resultados.filter { d -> d.id !in ids },
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
            it.copy(cargando = pantallaCompleta, cargandoMas = false, error = null, codigoError = null, errorMas = null)
        }
        val filtro = _estado.value.filtro
        trabajo = viewModelScope.launch {
            val resultado = repositorio.listarMisSolicitudes(filtro, 1)
            when (resultado) {
                is Resultado.Exito -> {
                    paginaSiguiente = 2
                    _estado.update {
                        it.copy(
                            solicitudes = resultado.datos.resultados,
                            hayMas = resultado.datos.siguiente != null,
                            cargada = true,
                            cargando = false,
                            refrescando = false,
                        )
                    }
                }
                is Resultado.Error -> {
                    val conLista = _estado.value.solicitudes.isNotEmpty()
                    _estado.update {
                        it.copy(
                            cargando = false,
                            refrescando = false,
                            error = if (conLista) null else resultado.mensaje, codigoError = if (conLista) null else resultado.codigo,
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
                MisSolicitudesViewModel((this[APPLICATION_KEY] as KumoraApp).beneficiarioRepositorio)
            }
        }
    }
}
