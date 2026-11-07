package com.proyecto.kuska.ui.iniciobeneficiario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.proyecto.kuska.KumoraApp
import com.proyecto.kuska.data.api.modelos.AlimentoDisponible
import com.proyecto.kuska.data.api.modelos.DashboardBeneficiario
import com.proyecto.kuska.data.repositorio.BeneficiarioRepositorio
import com.proyecto.kuska.data.repositorio.NotificacionesRepositorio
import com.proyecto.kuska.data.sesion.SesionManager
import com.proyecto.kuska.ui.comun.Saludo
import com.proyecto.kuska.ui.comun.formularioalimento.CategoriasAlimento
import com.proyecto.kuska.util.Resultado
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InicioBeneficiarioEstado(
    val filtros: FiltrosAlimentos = FiltrosAlimentos(),
    val alimentos: List<AlimentoDisponible> = emptyList(),
    /** Ya respondió al menos una vez la primera página de los filtros actuales. */
    val cargada: Boolean = false,
    /** Primera carga de los filtros (pantalla completa). */
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
    val vacia get() = cargada && alimentos.isEmpty() && error == null
}

/**
 * Resumen del inicio (HU-121), independiente de la lista: si falla, la lista sigue igual. [error] solo cuando no
 * hay datos (si un refresco falla, se queda el último resumen).
 */
data class ResumenEstado(
    val datos: DashboardBeneficiario? = null,
    val error: String? = null,
)

/**
 * Alimentos disponibles del beneficiario (HU-038 a HU-041): lista paginada con buscador, categoría y modalidad.
 * Arriba, el resumen (HU-121), que se recarga al volver a la pestaña (no al girar); la lista, no.
 */
class InicioBeneficiarioViewModel(
    private val repositorio: BeneficiarioRepositorio,
    private val notificaciones: NotificacionesRepositorio,
    sesionManager: SesionManager,
) : ViewModel() {

    private val _estado = MutableStateFlow(InicioBeneficiarioEstado())
    val estado: StateFlow<InicioBeneficiarioEstado> = _estado.asStateFlow()

    /** Errores al refrescar una lista que ya se ve (van en un Snackbar). */
    private val _mensajes = Channel<String>(Channel.BUFFERED)
    val mensajes: Flow<String> = _mensajes.receiveAsFlow()

    /** Chips de categoría: "Todas" + las activas. */
    val categorias = CategoriasAlimento(viewModelScope) { repositorio.obtenerCategorias() }

    private var trabajo: Job? = null
    private var espera: Job? = null
    private var paginaSiguiente = 1

    /** Filtros con los que se pidió la lista que se ve (o se está pidiendo). */
    private var consultados = FiltrosAlimentos()

    private val _resumen = MutableStateFlow(ResumenEstado())
    val resumen: StateFlow<ResumenEstado> = _resumen.asStateFlow()

    /** Para "Hola, …" del resumen. */
    val nombre: StateFlow<String?> = sesionManager.sesion
        .map { Saludo.nombre(it?.usuario) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var trabajoResumen: Job? = null
    private var recargarResumenAlVolver = false

    init {
        categorias.cargar()
        cargarPrimera(pantallaCompleta = true)
        cargarResumen()
    }

    fun reintentarResumen() = cargarResumen()

    /** Se fue a otra pestaña, a otra pantalla o a segundo plano (no al girar). */
    fun marcarSalida() {
        recargarResumenAlVolver = true
    }

    /** Al volver a la pestaña solo se recarga el resumen. */
    fun alVolver() {
        if (!recargarResumenAlVolver) return
        recargarResumenAlVolver = false
        cargarResumen()
    }

    /** Además del resumen, trae las no leídas: actualiza la campana. */
    private fun cargarResumen() {
        trabajoResumen?.cancel()
        _resumen.update { it.copy(error = null) }
        trabajoResumen = viewModelScope.launch {
            when (val resultado = repositorio.obtenerDashboard()) {
                is Resultado.Exito -> {
                    _resumen.value = ResumenEstado(datos = resultado.datos)
                    notificaciones.fijarContador(resultado.datos.notificacionesNoLeidas)
                }
                is Resultado.Error -> _resumen.update {
                    if (it.datos != null) it else it.copy(error = resultado.mensaje)
                }
            }
        }
    }

    /** Busca 400 ms después de la última tecla; mientras tanto la lista sigue como está. */
    fun cambiarBusqueda(texto: String) {
        if (_estado.value.filtros.busqueda == texto) return
        _estado.update { it.copy(filtros = it.filtros.copy(busqueda = texto)) }
        espera?.cancel()
        espera = viewModelScope.launch {
            delay(FiltrosAlimentos.ESPERA_BUSQUEDA_MS)
            aplicarFiltros()
        }
    }

    fun cambiarCategoria(categoria: Int?) {
        if (_estado.value.filtros.categoria == categoria) return
        _estado.update { it.copy(filtros = it.filtros.copy(categoria = categoria)) }
        aplicarFiltros()
    }

    fun cambiarModalidad(modalidad: String?) {
        if (_estado.value.filtros.modalidad == modalidad) return
        _estado.update { it.copy(filtros = it.filtros.copy(modalidad = modalidad)) }
        aplicarFiltros()
    }

    /** Pull-to-refresh. Si las categorías no cargaron, también las reintenta. */
    fun refrescar() {
        if (categorias.estado.value.lista.isEmpty()) categorias.cargar()
        _estado.update { it.copy(refrescando = true) }
        cargarPrimera(pantallaCompleta = _estado.value.alimentos.isEmpty())
        cargarResumen()
    }

    fun reintentar() = cargarPrimera(pantallaCompleta = true)

    /** Volvió del detalle después de un error de regla: la lista puede haber cambiado. */
    fun recargar() = cargarPrimera(pantallaCompleta = _estado.value.alimentos.isEmpty())

    /** Llegó al final de la lista. No reintenta solo si la página anterior falló. */
    fun cargarMas() {
        val actual = _estado.value
        if (!actual.hayMas || actual.cargando || actual.cargandoMas || actual.errorMas != null || trabajo?.isActive == true) {
            return
        }
        _estado.update { it.copy(cargandoMas = true) }
        val filtros = consultados
        val pagina = paginaSiguiente
        trabajo = viewModelScope.launch {
            when (val resultado = repositorio.listarAlimentos(filtros, pagina)) {
                is Resultado.Exito -> {
                    paginaSiguiente = pagina + 1
                    _estado.update {
                        // Un alimento puede repetirse si cambió el orden entre páginas.
                        val ids = it.alimentos.mapTo(HashSet()) { a -> a.id }
                        it.copy(
                            alimentos = it.alimentos + resultado.datos.resultados.filter { a -> a.id !in ids },
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

    /** Filtros distintos a los de la lista que se ve: empieza de nuevo desde la página 1. */
    private fun aplicarFiltros() {
        espera?.cancel()
        val filtros = _estado.value.filtros
        if (filtros.mismaConsulta(consultados)) return
        _estado.update { it.copy(alimentos = emptyList(), cargada = false, hayMas = false) }
        cargarPrimera(pantallaCompleta = true)
    }

    /** Pide la página 1 y reemplaza la lista. Cancela cualquier carga en curso. */
    private fun cargarPrimera(pantallaCompleta: Boolean) {
        trabajo?.cancel()
        val filtros = _estado.value.filtros
        consultados = filtros
        _estado.update {
            it.copy(cargando = pantallaCompleta, cargandoMas = false, error = null, codigoError = null, errorMas = null)
        }
        trabajo = viewModelScope.launch {
            when (val resultado = repositorio.listarAlimentos(filtros, 1)) {
                is Resultado.Exito -> {
                    paginaSiguiente = 2
                    _estado.update {
                        it.copy(
                            alimentos = resultado.datos.resultados,
                            hayMas = resultado.datos.siguiente != null,
                            cargada = true,
                            cargando = false,
                            refrescando = false,
                        )
                    }
                }
                is Resultado.Error -> {
                    val conLista = _estado.value.alimentos.isNotEmpty()
                    _estado.update {
                        it.copy(cargando = false, refrescando = false, error = if (conLista) null else resultado.mensaje, codigoError = if (conLista) null else resultado.codigo)
                    }
                    if (conLista) _mensajes.send(resultado.mensaje)
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KumoraApp
                InicioBeneficiarioViewModel(app.beneficiarioRepositorio, app.notificacionesRepositorio, app.sesionManager)
            }
        }
    }
}
