package com.proyecto.kuska.ui.detalledonacion

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.proyecto.kuska.KumoraApp
import com.proyecto.kuska.data.api.modelos.AlimentoConSolicitudes
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.data.api.modelos.Novedad
import com.proyecto.kuska.data.repositorio.DonacionesRepositorio
import com.proyecto.kuska.ui.comun.ErroresAccion
import com.proyecto.kuska.ui.comun.TextoUi
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

data class DetalleDonacionEstado(
    val donacion: Donacion? = null,
    /** Carga sin datos todavía (pantalla completa). */
    val cargando: Boolean = false,
    /** Pull-to-refresh. */
    val refrescando: Boolean = false,
    /** Error sin datos (pantalla completa con "Reintentar"). */
    val error: String? = null,
    /** Solo si la donación no está Pendiente (RN-15). */
    val solicitudes: List<AlimentoConSolicitudes>? = null,
    val cargandoSolicitudes: Boolean = false,
    val errorSolicitudes: String? = null,
    val eliminando: Boolean = false,
    /** Alimento que se está quitando (null = ninguno). */
    val quitandoId: Int? = null,
    /** Aviso rojo del encabezado: error de la última acción (quitar, eliminar, entregar) o de editar/agregar. */
    val aviso: String? = null,
    /** Solicitud cuya entrega se está registrando (null = ninguna). */
    val entregandoId: Int? = null,
    /** Diálogo de entrega: error de la observación (en su campo). */
    val errorObservacion: String? = null,
    /** Diálogo de entrega: sin conexión o 429 (dentro del diálogo, que sigue abierto). */
    val errorEntrega: String? = null,
    /** Novedades de la donación (no se piden si está Pendiente). Null = todavía no cargaron. */
    val novedades: List<Novedad>? = null,
    val cargandoNovedades: Boolean = false,
    /** Se mantiene mientras se reintenta, para que la sección no desaparezca. */
    val errorNovedades: String? = null,
    /** Novedad que se está enviando (null = ninguna). */
    val enviandoNovedad: ObjetivoNovedad? = null,
    /** Diálogo de novedad: error de la descripción (en su campo). */
    val errorDescripcion: TextoUi? = null,
    /** Diálogo de novedad: sin conexión o 429 (dentro del diálogo, que sigue abierto). */
    val errorNovedad: String? = null,
) {
    /** Hay una acción en curso: las demás quedan deshabilitadas. */
    val ocupado get() = eliminando || quitandoId != null || entregandoId != null || enviandoNovedad != null
}

sealed interface EventoDetalle {
    data object Eliminada : EventoDetalle

    /** Error al refrescar un detalle que ya se ve (va en un Snackbar). */
    data class Mensaje(val texto: String) : EventoDetalle

    /** Entrega registrada: se cierra el diálogo y sale "Entrega registrada.". */
    data object EntregaRegistrada : EventoDetalle

    /** Error que va al aviso rojo: se cierra el diálogo de entrega. */
    data object CerrarDialogoEntrega : EventoDetalle

    /** Novedad enviada: se cierra el diálogo y sale "Novedad enviada. El administrador la revisará.". */
    data object NovedadEnviada : EventoDetalle

    /** Error que va al aviso rojo: se cierra el diálogo de novedad. */
    data object CerrarDialogoNovedad : EventoDetalle
}

class DetalleDonacionViewModel(
    private val repositorio: DonacionesRepositorio,
    estadoGuardado: SavedStateHandle,
) : ViewModel() {

    private val id: Int = requireNotNull(estadoGuardado.get<Int>(DetalleDonacionFragment.ARG_ID))

    private val _estado = MutableStateFlow(DetalleDonacionEstado())
    val estado: StateFlow<DetalleDonacionEstado> = _estado.asStateFlow()

    private val _eventos = Channel<EventoDetalle>(Channel.BUFFERED)
    val eventos: Flow<EventoDetalle> = _eventos.receiveAsFlow()

    private var trabajo: Job? = null

    /** Aparte de [trabajo]: así reintentar las solicitudes no corta la carga de las novedades. */
    private var trabajoNovedades: Job? = null

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

    /**
     * Volvió de editar o agregar: recarga. [exito] va en un Snackbar ("Cambios guardados.");
     * [aviso] en el aviso rojo (la donación ya no se podía editar).
     */
    fun recibirResultado(exito: String?, aviso: String?) {
        _estado.update { it.copy(aviso = aviso) }
        exito?.let { _eventos.trySend(EventoDetalle.Mensaje(it)) }
        cargar()
    }

    /** Antes de abrir editar o agregar. */
    fun limpiarAviso() {
        _estado.update { it.copy(aviso = null) }
    }

    fun reintentar() = cargar()

    fun reintentarSolicitudes() {
        val donacion = _estado.value.donacion ?: return
        trabajo?.cancel()
        trabajo = viewModelScope.launch { cargarSolicitudes(donacion) }
    }

    fun reintentarNovedades() {
        val donacion = _estado.value.donacion ?: return
        if (NovedadDonacion.tieneNovedades(donacion)) iniciarCargaNovedades(donacion.id)
    }

    /** Primero la donación; luego, si no está Pendiente, sus novedades y sus solicitudes recibidas (en paralelo). */
    private fun cargar() {
        trabajo?.cancel()
        _estado.update { it.copy(cargando = it.donacion == null, error = null) }
        trabajo = viewModelScope.launch {
            when (val resultado = repositorio.obtenerDonacion(id)) {
                is Resultado.Exito -> {
                    val donacion = resultado.datos
                    _estado.update {
                        it.copy(
                            donacion = donacion,
                            cargando = false,
                            refrescando = false,
                            solicitudes = if (donacion.estado == Donacion.ESTADO_PENDIENTE) null else it.solicitudes,
                            novedades = if (NovedadDonacion.tieneNovedades(donacion)) it.novedades else null,
                            errorNovedades = if (NovedadDonacion.tieneNovedades(donacion)) it.errorNovedades else null,
                        )
                    }
                    if (NovedadDonacion.tieneNovedades(donacion)) iniciarCargaNovedades(donacion.id)
                    if (donacion.estado != Donacion.ESTADO_PENDIENTE) cargarSolicitudes(donacion)
                }
                is Resultado.Error -> {
                    val conDatos = _estado.value.donacion != null
                    _estado.update {
                        it.copy(cargando = false, refrescando = false, error = if (conDatos) null else resultado.mensaje)
                    }
                    if (conDatos) _eventos.send(EventoDetalle.Mensaje(resultado.mensaje))
                }
            }
        }
    }

    private fun iniciarCargaNovedades(donacionId: Int) {
        trabajoNovedades?.cancel()
        _estado.update { it.copy(cargandoNovedades = true) }
        trabajoNovedades = viewModelScope.launch {
            when (val resultado = repositorio.novedadesDeDonacion(donacionId)) {
                is Resultado.Exito -> _estado.update {
                    it.copy(novedades = resultado.datos, cargandoNovedades = false, errorNovedades = null)
                }
                is Resultado.Error -> _estado.update {
                    it.copy(cargandoNovedades = false, errorNovedades = resultado.mensaje)
                }
            }
        }
    }

    private suspend fun cargarSolicitudes(donacion: Donacion) {
        _estado.update { it.copy(cargandoSolicitudes = true, errorSolicitudes = null) }
        when (val resultado = repositorio.solicitudesRecibidas(donacion.id)) {
            is Resultado.Exito -> _estado.update {
                it.copy(solicitudes = resultado.datos, cargandoSolicitudes = false)
            }
            is Resultado.Error -> _estado.update {
                it.copy(cargandoSolicitudes = false, errorSolicitudes = resultado.mensaje)
            }
        }
    }

    /** Solo Pendiente (RN-12). Si el servidor no lo permite, su mensaje va en el aviso rojo. */
    fun eliminar() {
        val actual = _estado.value
        if (actual.ocupado || actual.donacion?.estado != Donacion.ESTADO_PENDIENTE) return
        trabajo?.cancel()
        _estado.update { it.copy(eliminando = true, aviso = null, refrescando = false) }
        viewModelScope.launch {
            when (val resultado = repositorio.eliminarDonacion(id)) {
                // Se deja "eliminando" mientras la pantalla se cierra.
                is Resultado.Exito -> _eventos.send(EventoDetalle.Eliminada)
                is Resultado.Error -> mostrarErrorAccion(resultado) { it.copy(eliminando = false) }
            }
        }
    }

    /** Solo Pendiente y con más de un alimento: el último no se quita, se elimina la donación. */
    fun quitarAlimento(alimentoId: Int) {
        val actual = _estado.value
        val donacion = actual.donacion ?: return
        if (actual.ocupado || donacion.estado != Donacion.ESTADO_PENDIENTE || donacion.alimentos.orEmpty().size < 2) return
        trabajo?.cancel()
        _estado.update { it.copy(quitandoId = alimentoId, aviso = null, refrescando = false) }
        viewModelScope.launch {
            when (val resultado = repositorio.quitarAlimento(id, alimentoId)) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(quitandoId = null) }
                    cargar()
                }
                is Resultado.Error -> mostrarErrorAccion(resultado) { it.copy(quitandoId = null) }
            }
        }
    }

    /** Al abrir el diálogo de entrega o al escribir en él: quita los errores que quedaron dentro. */
    fun limpiarErroresEntrega() {
        _estado.update { it.copy(errorObservacion = null, errorEntrega = null) }
    }

    /**
     * Entrega total de una solicitud Aprobada y no cancelada (RN-26). Al terminar se recarga para ver la solicitud
     * Entregada. Los errores se reparten según [EntregaSolicitud.clasificar].
     */
    fun registrarEntrega(solicitudId: Int, observacion: String) {
        val actual = _estado.value
        if (actual.ocupado) return
        val solicitud = actual.solicitudes.orEmpty().flatMap { it.solicitudes }.firstOrNull { it.solicitud == solicitudId }
        if (solicitud == null || !EntregaSolicitud.puedeRegistrar(solicitud)) return
        _estado.update {
            it.copy(entregandoId = solicitudId, errorObservacion = null, errorEntrega = null, aviso = null, refrescando = false)
        }
        viewModelScope.launch {
            when (val resultado = repositorio.registrarEntrega(solicitudId, EntregaSolicitud.cuerpo(observacion))) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(entregandoId = null) }
                    _eventos.send(EventoDetalle.EntregaRegistrada)
                    cargar()
                }
                is Resultado.Error -> when (val fallo = EntregaSolicitud.clasificar(resultado)) {
                    is FalloEntrega.EnCampo -> _estado.update { it.copy(entregandoId = null, errorObservacion = fallo.mensaje) }
                    is FalloEntrega.EnDialogo -> _estado.update { it.copy(entregandoId = null, errorEntrega = fallo.mensaje) }
                    is FalloEntrega.Aviso -> {
                        _estado.update { it.copy(entregandoId = null, aviso = fallo.mensaje) }
                        _eventos.send(EventoDetalle.CerrarDialogoEntrega)
                        if (fallo.recargar) cargar()
                    }
                }
            }
        }
    }

    /** Al abrir el diálogo de novedad o al escribir en él: quita los errores que quedaron dentro. */
    fun limpiarErroresNovedad() {
        _estado.update { it.copy(errorDescripcion = null, errorNovedad = null) }
    }

    /**
     * Novedad sobre un alimento o, con [alimentoId] null, sobre toda la donación (RN-30). Solo Validada o Agotada.
     * Al terminar se recarga el detalle. Los errores se reparten según [NovedadDonacion.clasificar].
     */
    fun reportarNovedad(alimentoId: Int?, descripcion: String) {
        val actual = _estado.value
        val donacion = actual.donacion ?: return
        if (actual.ocupado || !NovedadDonacion.permiteReportar(donacion)) return
        NovedadDonacion.validarDescripcion(descripcion)?.let { error ->
            _estado.update { it.copy(errorDescripcion = error, errorNovedad = null) }
            return
        }
        val objetivo = ObjetivoNovedad(alimentoId)
        _estado.update {
            it.copy(enviandoNovedad = objetivo, errorDescripcion = null, errorNovedad = null, aviso = null, refrescando = false)
        }
        viewModelScope.launch {
            when (val resultado = repositorio.reportarNovedad(NovedadDonacion.cuerpo(donacion.id, objetivo, descripcion))) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(enviandoNovedad = null) }
                    _eventos.send(EventoDetalle.NovedadEnviada)
                    cargar()
                }
                is Resultado.Error -> when (val fallo = NovedadDonacion.clasificar(resultado)) {
                    is FalloNovedad.EnCampo -> _estado.update {
                        it.copy(enviandoNovedad = null, errorDescripcion = TextoUi.Texto(fallo.mensaje))
                    }
                    is FalloNovedad.EnDialogo -> _estado.update { it.copy(enviandoNovedad = null, errorNovedad = fallo.mensaje) }
                    is FalloNovedad.Aviso -> {
                        _estado.update { it.copy(enviandoNovedad = null, aviso = fallo.mensaje) }
                        _eventos.send(EventoDetalle.CerrarDialogoNovedad)
                        if (fallo.recargar) cargar()
                    }
                }
            }
        }
    }

    /**
     * El mensaje va al aviso rojo del encabezado. Si puede ser que la donación ya no esté Pendiente (o ya no
     * exista), se recarga: así el detalle muestra su estado real y desaparecen las acciones que ya no aplican.
     */
    private fun mostrarErrorAccion(error: Resultado.Error, terminar: (DetalleDonacionEstado) -> DetalleDonacionEstado) {
        _estado.update { terminar(it).copy(aviso = error.mensaje) }
        if (ErroresAccion.debeRevisarEstado(error)) cargar()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                DetalleDonacionViewModel(
                    (this[APPLICATION_KEY] as KumoraApp).donacionesRepositorio,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
