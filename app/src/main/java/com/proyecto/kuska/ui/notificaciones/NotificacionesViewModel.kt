package com.proyecto.kuska.ui.notificaciones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.proyecto.kuska.KumoraApp
import com.proyecto.kuska.data.api.modelos.Notificacion
import com.proyecto.kuska.data.repositorio.NotificacionesRepositorio
import com.proyecto.kuska.data.sesion.SesionManager
import com.proyecto.kuska.util.Resultado
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificacionesEstado(
    /** Filtro "No leídas" (false = Todas). */
    val soloNoLeidas: Boolean = false,
    val notificaciones: List<Notificacion> = emptyList(),
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
    val marcandoTodas: Boolean = false,
) {
    val vacia get() = cargada && notificaciones.isEmpty() && error == null
}

sealed interface EventoNotificaciones {
    /** Error al refrescar una lista que ya se ve o al marcar todas (va en un Snackbar). */
    data class Mensaje(val texto: String) : EventoNotificaciones

    data class Abrir(val destino: DestinoNotificacion) : EventoNotificaciones
}

/** Notificaciones (HU-119): lista paginada, filtro, marcar una o todas como leídas y abrir lo relacionado. */
class NotificacionesViewModel(
    private val repositorio: NotificacionesRepositorio,
    private val sesionManager: SesionManager,
) : ViewModel() {

    private val _estado = MutableStateFlow(NotificacionesEstado())
    val estado: StateFlow<NotificacionesEstado> = _estado.asStateFlow()

    /** Contador de la campana: decide si "Marcar todas como leídas" tiene sentido. */
    val noLeidas: StateFlow<Int?> = repositorio.noLeidas

    private val _eventos = Channel<EventoNotificaciones>(Channel.BUFFERED)
    val eventos: Flow<EventoNotificaciones> = _eventos.receiveAsFlow()

    private var trabajo: Job? = null
    private var paginaSiguiente = 1

    /** Se fue a un detalle: al volver se recarga. Al rotar no. */
    private var recargarAlVolver = false

    init {
        cargarPrimera(pantallaCompleta = true)
    }

    fun cambiarFiltro(soloNoLeidas: Boolean) {
        if (_estado.value.soloNoLeidas == soloNoLeidas) return
        _estado.value = NotificacionesEstado(soloNoLeidas = soloNoLeidas)
        cargarPrimera(pantallaCompleta = true)
    }

    /** Pull-to-refresh. */
    fun refrescar() {
        _estado.update { it.copy(refrescando = true) }
        cargarPrimera(pantallaCompleta = _estado.value.notificaciones.isEmpty())
    }

    fun reintentar() = cargarPrimera(pantallaCompleta = true)

    /** Al recrear la vista: si se volvió de un detalle, recarga sin tapar la lista. */
    fun alVolver() {
        if (!recargarAlVolver) return
        recargarAlVolver = false
        cargarPrimera(pantallaCompleta = _estado.value.notificaciones.isEmpty())
    }

    /**
     * Marca como leída de forma optimista (la lista y el contador cambian enseguida) y lleva a lo relacionado sin
     * esperar la respuesta. Si marcar falla, vuelve a "no leída" (se reintenta la próxima vez) y el contador se
     * corrige con el servidor. Con el filtro "No leídas" se queda en la lista hasta el próximo refresco.
     */
    fun tocar(notificacion: Notificacion) {
        if (!notificacion.leida) {
            _estado.update { it.copy(notificaciones = ReglasNotificacion.conLeida(it.notificaciones, notificacion.id, true)) }
            repositorio.ajustarContador(-1)
            viewModelScope.launch {
                if (repositorio.marcarLeida(notificacion.id) is Resultado.Error) {
                    _estado.update {
                        it.copy(notificaciones = ReglasNotificacion.conLeida(it.notificaciones, notificacion.id, false))
                    }
                    repositorio.refrescarContador()
                }
            }
        }
        viewModelScope.launch {
            val rol = sesionManager.sesion.first()?.usuario?.rol
            ReglasNotificacion.destino(rol, notificacion)?.let { destino ->
                recargarAlVolver = true
                _eventos.send(EventoNotificaciones.Abrir(destino))
            }
        }
    }

    /** "Marcar todas como leídas": el contador queda en lo que diga la API (0). */
    fun marcarTodas() {
        if (_estado.value.marcandoTodas || (noLeidas.value ?: 0) <= 0) return
        _estado.update { it.copy(marcandoTodas = true) }
        viewModelScope.launch {
            when (val resultado = repositorio.marcarTodas()) {
                is Resultado.Exito -> {
                    _estado.update {
                        it.copy(notificaciones = ReglasNotificacion.todasLeidas(it.notificaciones), marcandoTodas = false)
                    }
                    // Con "No leídas" la lista queda vacía: se vuelve a pedir.
                    if (_estado.value.soloNoLeidas) cargarPrimera(pantallaCompleta = false)
                }
                is Resultado.Error -> {
                    _estado.update { it.copy(marcandoTodas = false) }
                    _eventos.send(EventoNotificaciones.Mensaje(resultado.mensaje))
                }
            }
        }
    }

    /** Llegó al final de la lista. No reintenta solo si la página anterior falló. */
    fun cargarMas() {
        val actual = _estado.value
        if (!actual.hayMas || actual.cargando || actual.cargandoMas || actual.errorMas != null || trabajo?.isActive == true) {
            return
        }
        _estado.update { it.copy(cargandoMas = true) }
        val soloNoLeidas = actual.soloNoLeidas
        val pagina = paginaSiguiente
        trabajo = viewModelScope.launch {
            when (val resultado = repositorio.listar(soloNoLeidas, pagina)) {
                is Resultado.Exito -> {
                    paginaSiguiente = pagina + 1
                    _estado.update {
                        // Una notificación puede repetirse si llegó una nueva entre páginas.
                        val ids = it.notificaciones.mapTo(HashSet()) { n -> n.id }
                        it.copy(
                            notificaciones = it.notificaciones + resultado.datos.resultados.filter { n -> n.id !in ids },
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
        _estado.update { it.copy(cargando = pantallaCompleta, cargandoMas = false, error = null, errorMas = null) }
        val soloNoLeidas = _estado.value.soloNoLeidas
        trabajo = viewModelScope.launch {
            when (val resultado = repositorio.listar(soloNoLeidas, 1)) {
                is Resultado.Exito -> {
                    paginaSiguiente = 2
                    _estado.update {
                        it.copy(
                            notificaciones = resultado.datos.resultados,
                            hayMas = resultado.datos.siguiente != null,
                            cargada = true,
                            cargando = false,
                            refrescando = false,
                        )
                    }
                }
                is Resultado.Error -> {
                    val conLista = _estado.value.notificaciones.isNotEmpty()
                    _estado.update {
                        it.copy(cargando = false, refrescando = false, error = if (conLista) null else resultado.mensaje)
                    }
                    if (conLista) _eventos.send(EventoNotificaciones.Mensaje(resultado.mensaje))
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KumoraApp
                NotificacionesViewModel(app.notificacionesRepositorio, app.sesionManager)
            }
        }
    }
}
