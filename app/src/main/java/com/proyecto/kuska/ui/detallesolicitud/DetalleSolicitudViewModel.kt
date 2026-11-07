package com.proyecto.kuska.ui.detallesolicitud

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.proyecto.kuska.KumoraApp
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.ConfirmarEntregaRequest
import com.proyecto.kuska.data.api.modelos.MiSolicitud
import com.proyecto.kuska.data.repositorio.BeneficiarioRepositorio
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

data class DetalleSolicitudEstado(
    val solicitud: MiSolicitud? = null,
    /** Carga sin datos todavía (pantalla completa). */
    val cargando: Boolean = false,
    /** Pull-to-refresh. */
    val refrescando: Boolean = false,
    /** Error sin datos (pantalla completa con "Reintentar"). */
    val error: String? = null,
    val cancelando: Boolean = false,
    /** Respuesta de la entrega que se está enviando (RN-26b). */
    val confirmando: RespuestaEntrega? = null,
    /** Error del comentario, en su campo del diálogo "No me llegó". */
    val errorComentario: TextoUi? = null,
    /** Sin conexión o 429 al reclamar: dentro del diálogo. */
    val errorReclamo: String? = null,
    /** Aviso rojo del encabezado: error al cancelar o al confirmar, o el plazo para responder ya pasó. */
    val aviso: TextoUi? = null,
) {
    /** Hay una acción en curso: las demás quedan deshabilitadas. */
    val ocupado get() = cancelando || confirmando != null
}

sealed interface EventoSolicitud {
    /** Error al refrescar un detalle que ya se ve (va en un Snackbar). */
    data class Mensaje(val texto: String) : EventoSolicitud

    /** Cancelada (RN-23): Snackbar "Solicitud cancelada.". */
    data object Cancelada : EventoSolicitud

    /** "Sí, lo recibí" (HU-057): Snackbar "Gracias por confirmar.". */
    data object Confirmada : EventoSolicitud

    /** "No me llegó" (HU-058): se cierra el diálogo y Snackbar "Reclamo enviado…". */
    data object Reclamada : EventoSolicitud

    /** El error del reclamo va al aviso rojo: se cierra el diálogo. */
    data object CerrarDialogoReclamo : EventoSolicitud
}

class DetalleSolicitudViewModel(
    private val repositorio: BeneficiarioRepositorio,
    estadoGuardado: SavedStateHandle,
    /** Hora actual en milisegundos (para el plazo de confirmación). */
    private val reloj: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val id: Int = requireNotNull(estadoGuardado.get<Int>(DetalleSolicitudFragment.ARG_ID))

    private val _estado = MutableStateFlow(DetalleSolicitudEstado())
    val estado: StateFlow<DetalleSolicitudEstado> = _estado.asStateFlow()

    private val _eventos = Channel<EventoSolicitud>(Channel.BUFFERED)
    val eventos: Flow<EventoSolicitud> = _eventos.receiveAsFlow()

    private var trabajo: Job? = null

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

    fun reintentar() = cargar()

    private fun cargar() {
        trabajo?.cancel()
        _estado.update { it.copy(cargando = it.solicitud == null, error = null) }
        trabajo = viewModelScope.launch {
            when (val resultado = repositorio.obtenerMiSolicitud(id)) {
                is Resultado.Exito -> _estado.update {
                    it.copy(solicitud = resultado.datos, cargando = false, refrescando = false)
                }
                is Resultado.Error -> {
                    val conDatos = _estado.value.solicitud != null
                    _estado.update {
                        it.copy(cargando = false, refrescando = false, error = if (conDatos) null else resultado.mensaje)
                    }
                    if (conDatos) _eventos.send(EventoSolicitud.Mensaje(resultado.mensaje))
                }
            }
        }
    }

    /**
     * RN-23: solo Pendiente. La respuesta ya es la solicitud cancelada, así que se muestra sin volver a pedirla.
     * Si falla, el mensaje va al aviso rojo; si puede que ya no esté Pendiente (400 de regla o 404), se recarga.
     */
    fun cancelar() {
        val actual = _estado.value
        val solicitud = actual.solicitud ?: return
        if (actual.ocupado || !ReglasSolicitud.puedeCancelar(solicitud)) return
        // Una recarga en curso no debe pisar la respuesta de la cancelación.
        trabajo?.cancel()
        _estado.update { it.copy(cancelando = true, aviso = null, refrescando = false, cargando = false) }
        viewModelScope.launch {
            when (val resultado = repositorio.cancelarMiSolicitud(id)) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(solicitud = resultado.datos, cancelando = false) }
                    _eventos.send(EventoSolicitud.Cancelada)
                }
                is Resultado.Error -> {
                    _estado.update { it.copy(cancelando = false, aviso = TextoUi.Texto(resultado.mensaje)) }
                    if (ErroresAccion.debeRevisarEstado(resultado)) cargar()
                }
            }
        }
    }

    /**
     * Al pulsar "Sí, lo recibí" o "No me llegó", y otra vez al enviar (el diálogo pudo quedar abierto un rato):
     * true si todavía se puede responder. Si el plazo pasó con la pantalla abierta, lo avisa en rojo y recarga.
     */
    fun revisarPlazo(): Boolean {
        val actual = _estado.value
        val solicitud = actual.solicitud ?: return false
        if (actual.ocupado) return false
        if (ConfirmacionEntregaSolicitud.puedeResponder(solicitud, reloj())) return true
        avisarPlazoTerminado()
        return false
    }

    /** Al abrir el diálogo de reclamo o al escribir en él: quita los errores que quedaron dentro. */
    fun limpiarErroresReclamo() {
        _estado.update { it.copy(errorComentario = null, errorReclamo = null) }
    }

    /** HU-057: "Recibido" la deja Confirmada. */
    fun confirmarRecibido() {
        if (!revisarPlazo()) return
        responder(RespuestaEntrega.RECIBIDO, ConfirmacionEntregaSolicitud.cuerpoRecibido())
    }

    /** HU-058: "No recibido" la deja Reclamada; el comentario es obligatorio. */
    fun reclamar(comentario: String) {
        if (!revisarPlazo()) {
            _eventos.trySend(EventoSolicitud.CerrarDialogoReclamo)
            return
        }
        ConfirmacionEntregaSolicitud.validarComentario(comentario)?.let { error ->
            _estado.update { it.copy(errorComentario = error, errorReclamo = null) }
            return
        }
        responder(RespuestaEntrega.RECLAMO, ConfirmacionEntregaSolicitud.cuerpoReclamo(comentario))
    }

    private fun avisarPlazoTerminado() {
        _estado.update { it.copy(aviso = TextoUi.Recurso(R.string.solicitud_plazo_terminado_aviso), refrescando = false) }
        cargar()
    }

    /**
     * La respuesta ya es la solicitud con la confirmación, así que se muestra sin volver a pedirla. Los errores se
     * reparten según [ConfirmacionEntregaSolicitud.clasificar].
     */
    private fun responder(respuesta: RespuestaEntrega, cuerpo: ConfirmarEntregaRequest) {
        // Una recarga en curso no debe pisar la respuesta.
        trabajo?.cancel()
        _estado.update {
            it.copy(
                confirmando = respuesta, errorComentario = null, errorReclamo = null, aviso = null,
                refrescando = false, cargando = false,
            )
        }
        viewModelScope.launch {
            when (val resultado = repositorio.confirmarEntrega(id, cuerpo)) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(solicitud = resultado.datos, confirmando = null) }
                    _eventos.send(
                        if (respuesta == RespuestaEntrega.RECIBIDO) EventoSolicitud.Confirmada else EventoSolicitud.Reclamada
                    )
                }
                is Resultado.Error -> when (val fallo = ConfirmacionEntregaSolicitud.clasificar(resultado, respuesta)) {
                    is FalloConfirmacion.EnCampo ->
                        _estado.update { it.copy(confirmando = null, errorComentario = TextoUi.Texto(fallo.mensaje)) }
                    is FalloConfirmacion.EnDialogo ->
                        _estado.update { it.copy(confirmando = null, errorReclamo = fallo.mensaje) }
                    is FalloConfirmacion.Aviso -> {
                        _estado.update { it.copy(confirmando = null, aviso = TextoUi.Texto(fallo.mensaje)) }
                        if (respuesta == RespuestaEntrega.RECLAMO) _eventos.send(EventoSolicitud.CerrarDialogoReclamo)
                        if (fallo.recargar) cargar()
                    }
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                DetalleSolicitudViewModel(
                    (this[APPLICATION_KEY] as KumoraApp).beneficiarioRepositorio,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
