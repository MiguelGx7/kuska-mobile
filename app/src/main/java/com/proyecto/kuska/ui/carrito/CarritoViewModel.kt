package com.proyecto.kuska.ui.carrito

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.proyecto.kuska.KumoraApp
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.AlimentoDisponible
import com.proyecto.kuska.data.api.modelos.Carrito
import com.proyecto.kuska.data.api.modelos.CarritoItem
import com.proyecto.kuska.data.repositorio.BeneficiarioRepositorio
import com.proyecto.kuska.data.repositorio.PerfilRepositorio
import com.proyecto.kuska.ui.comun.TextoUi
import com.proyecto.kuska.ui.detallealimento.CantidadPedido
import com.proyecto.kuska.ui.detallealimento.FalloCarrito
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
import java.math.BigDecimal

/** El diálogo "Cambiar cantidad" abierto (HU-045). */
data class CambioCantidad(
    val itemId: Int,
    val alimento: AlimentoDisponible,
    val maximo: BigDecimal,
    val cantidad: String,
    val error: TextoUi? = null,
    val aviso: String? = null,
    val guardando: Boolean = false,
)

data class CarritoEstado(
    val carrito: Carrito? = null,
    /** Primera carga (pantalla completa). */
    val cargando: Boolean = false,
    /** Pull-to-refresh. */
    val refrescando: Boolean = false,
    /** Error sin carrito que mostrar (pantalla completa con "Reintentar"). */
    val error: String? = null,
    /** Modalidad y dirección de cada donación "Ambas", por id de donación. */
    val elecciones: Map<Int, EleccionEntrega> = emptyMap(),
    /** Validación en el celular antes de enviar, por id de donación. */
    val erroresLocales: Map<Int, TextoUi> = emptyMap(),
    /** Errores del último 400 de enviar. */
    val errores: ErroresEnvio = ErroresEnvio(),
    val quitandoId: Int? = null,
    val cambio: CambioCantidad? = null,
    val enviando: Boolean = false,
    /** codigo_pedido del envío que salió bien: el diálogo de éxito está abierto. */
    val pedidoEnviado: String? = null,
) {
    val ocupado get() = enviando || quitandoId != null || cambio?.guardando == true
    val vacio get() = carrito != null && carrito.grupos.isEmpty() && error == null
    val puedeEnviar get() = EnvioCarrito.puedeEnviar(carrito, ocupado)
}

sealed interface EventoCarrito {
    data class Mensaje(val texto: TextoUi) : EventoCarrito

    /** Todo listo: se pide confirmación con cuántas solicitudes se crearán. */
    data class ConfirmarEnvio(val solicitudes: Int) : EventoCarrito
}

/**
 * Carrito del beneficiario (HU-045 a HU-050). El carrito no reserva (RN-20) y el servidor valida todo: después de
 * cada cambio se vuelve a pedir el carrito para tener los avisos y puede_enviar al día.
 */
class CarritoViewModel(
    private val repositorio: BeneficiarioRepositorio,
    private val perfil: PerfilRepositorio,
    private val estadoGuardado: SavedStateHandle,
) : ViewModel() {

    private val _estado = MutableStateFlow(CarritoEstado(elecciones = leerElecciones()))
    val estado: StateFlow<CarritoEstado> = _estado.asStateFlow()

    private val _eventos = Channel<EventoCarrito>(Channel.BUFFERED)
    val eventos: Flow<EventoCarrito> = _eventos.receiveAsFlow()

    private var carga: Job? = null

    /** La siguiente carga mueve al aviso general los errores del 400 de ítems o donaciones que ya no están. */
    private var ajustarErrores = false

    private var direccionPerfil: String? = null
    private var perfilPedido = false

    /**
     * La pantalla se mostró (al entrar a la pestaña o al girar): la primera vez carga con pantalla completa; después
     * recarga sin indicador, porque se pudieron agregar alimentos desde Inicio.
     */
    fun alMostrar() {
        val actual = _estado.value
        when {
            actual.carrito == null && !actual.cargando -> cargar(pantallaCompleta = true)
            actual.carrito != null && !actual.ocupado -> cargar(pantallaCompleta = false)
        }
    }

    fun refrescar() {
        _estado.update { it.copy(refrescando = true) }
        cargar(pantallaCompleta = _estado.value.carrito == null)
    }

    fun reintentar() = cargar(pantallaCompleta = true)

    private fun cargar(pantallaCompleta: Boolean) {
        carga?.cancel()
        _estado.update { it.copy(cargando = pantallaCompleta, error = null) }
        carga = viewModelScope.launch {
            when (val resultado = repositorio.obtenerCarrito()) {
                is Resultado.Exito -> aplicar(resultado.datos)
                is Resultado.Error -> {
                    val conCarrito = _estado.value.carrito != null
                    _estado.update {
                        it.copy(cargando = false, refrescando = false, error = if (conCarrito) null else resultado.mensaje)
                    }
                    if (conCarrito) _eventos.send(EventoCarrito.Mensaje(TextoUi.Texto(resultado.mensaje)))
                }
            }
        }
    }

    private suspend fun aplicar(carrito: Carrito) {
        val donaciones = carrito.grupos.mapTo(HashSet()) { it.donacion }
        val items = carrito.grupos.flatMap { it.items.orEmpty() }
        val idsItems = items.mapTo(HashSet()) { it.id }
        val antes = _estado.value
        val errores = if (ajustarErrores) {
            EnvioCarrito.ajustar(antes.errores, carrito)
        } else {
            antes.errores.copy(
                porItem = antes.errores.porItem.filterKeys { it in idsItems },
                porDonacion = antes.errores.porDonacion.filterKeys { it in donaciones },
                generales = if (carrito.grupos.isEmpty()) emptyList() else antes.errores.generales,
            )
        }
        ajustarErrores = false
        // El ítem del diálogo ya no está (lo quitó el servidor): se cierra el diálogo.
        val cambio = antes.cambio
        val cambioSeFue = cambio != null && cambio.itemId !in idsItems
        _estado.update {
            it.copy(
                carrito = carrito,
                cargando = false,
                refrescando = false,
                error = null,
                elecciones = EnvioCarrito.conservar(it.elecciones, carrito),
                erroresLocales = it.erroresLocales.filterKeys { id -> id in donaciones },
                errores = errores,
                cambio = if (cambioSeFue) null else it.cambio,
            )
        }
        guardarElecciones()
        if (cambioSeFue) cambio?.aviso?.let { _eventos.send(EventoCarrito.Mensaje(TextoUi.Texto(it))) }
        if (carrito.grupos.any(EnvioCarrito::pideEleccion)) cargarPerfil()
    }

    /** La dirección del perfil, para llenar Domicilio por defecto. Si falla, se reintenta en la siguiente carga. */
    private fun cargarPerfil() {
        if (perfilPedido) return
        perfilPedido = true
        viewModelScope.launch {
            when (val resultado = perfil.obtenerPerfil()) {
                is Resultado.Exito -> {
                    direccionPerfil = resultado.datos.direccion?.takeIf { it.isNotBlank() }
                    _estado.update { it.copy(elecciones = EnvioCarrito.conDireccionPerfil(it.elecciones, direccionPerfil)) }
                    guardarElecciones()
                }
                is Resultado.Error -> perfilPedido = false
            }
        }
    }

    // --- Modalidad (HU-049) ---

    fun elegirModalidad(donacion: Int, modalidad: String) {
        val actual = _estado.value
        if (actual.enviando) return
        val eleccion = actual.elecciones[donacion] ?: EleccionEntrega()
        if (eleccion.modalidad == modalidad) return
        _estado.update {
            it.copy(
                elecciones = it.elecciones + (donacion to EnvioCarrito.elegir(eleccion, modalidad, direccionPerfil)),
                erroresLocales = it.erroresLocales - donacion,
                errores = it.errores.copy(porDonacion = it.errores.porDonacion - donacion),
            )
        }
        guardarElecciones()
    }

    /** Se ignora si el texto no cambió (p. ej. cuando la vista se vuelve a dibujar). */
    fun cambiarDireccion(donacion: Int, texto: String) {
        val actual = _estado.value
        val eleccion = actual.elecciones[donacion] ?: return
        if (actual.enviando || eleccion.direccion == texto) return
        _estado.update {
            it.copy(
                elecciones = it.elecciones + (donacion to eleccion.copy(direccion = texto, direccionEditada = true)),
                erroresLocales = it.erroresLocales - donacion,
                errores = it.errores.copy(porDonacion = it.errores.porDonacion - donacion),
            )
        }
        guardarElecciones()
    }

    // --- Cambiar cantidad (HU-045) ---

    fun abrirCambio(item: CarritoItem) {
        val actual = _estado.value
        val alimento = item.alimento ?: return
        val maximo = CantidadPedido.maximo(alimento) ?: return
        if (actual.ocupado || actual.cambio != null) return
        val cantidad = CantidadPedido.leer(item.cantidad)?.let(CantidadPedido::texto) ?: CantidadPedido.inicial(maximo)
        _estado.update { it.copy(cambio = CambioCantidad(item.id, alimento, maximo, cantidad)) }
    }

    fun cambiarCantidad(texto: String) = actualizarCambio { if (it.cantidad == texto) it else it.copy(cantidad = texto, error = null) }

    fun sumar() = actualizarCambio { it.copy(cantidad = CantidadPedido.sumar(it.cantidad, it.maximo), error = null) }

    fun restar() = actualizarCambio { it.copy(cantidad = CantidadPedido.restar(it.cantidad, it.maximo), error = null) }

    private fun actualizarCambio(cambio: (CambioCantidad) -> CambioCantidad) {
        val actual = _estado.value.cambio ?: return
        if (actual.guardando) return
        _estado.update { it.copy(cambio = cambio(actual)) }
    }

    /** Mientras se guarda no se puede cerrar. */
    fun cerrarCambio() {
        if (_estado.value.cambio?.guardando == true) return
        _estado.update { it.copy(cambio = null) }
    }

    fun guardarCambio() {
        val cambio = _estado.value.cambio ?: return
        if (cambio.guardando) return
        CantidadPedido.validar(cambio.cantidad, cambio.alimento.unidadMedida, cambio.maximo)?.let { error ->
            _estado.update { it.copy(cambio = cambio.copy(error = error, aviso = null)) }
            return
        }
        _estado.update { it.copy(cambio = cambio.copy(guardando = true, error = null, aviso = null)) }
        viewModelScope.launch {
            when (val resultado = repositorio.cambiarCantidad(cambio.itemId, CantidadPedido.cuerpoCambio(cambio.cantidad))) {
                is Resultado.Exito -> {
                    _estado.update {
                        it.copy(
                            cambio = null,
                            errores = it.errores.copy(porItem = it.errores.porItem - cambio.itemId, generales = emptyList()),
                        )
                    }
                    _eventos.send(EventoCarrito.Mensaje(TextoUi.Recurso(R.string.carrito_cantidad_actualizada)))
                    cargar(pantallaCompleta = false)
                }
                is Resultado.Error -> when (val fallo = CantidadPedido.clasificar(resultado)) {
                    is FalloCarrito.EnCampo -> _estado.update {
                        it.copy(cambio = it.cambio?.copy(guardando = false, error = TextoUi.Texto(fallo.mensaje)))
                    }
                    is FalloCarrito.Aviso -> {
                        _estado.update { it.copy(cambio = it.cambio?.copy(guardando = false, aviso = fallo.mensaje)) }
                        if (fallo.recargarLista) cargar(pantallaCompleta = false)
                    }
                }
            }
        }
    }

    // --- Quitar (HU-046) ---

    fun quitar(itemId: Int) {
        if (_estado.value.ocupado) return
        _estado.update { it.copy(quitandoId = itemId) }
        viewModelScope.launch {
            val resultado = repositorio.quitarDelCarrito(itemId)
            // 404: ya no estaba en el carrito; queda igual que quitado.
            val quitado = resultado is Resultado.Exito || (resultado as? Resultado.Error)?.codigo == 404
            _estado.update {
                if (quitado) {
                    it.copy(
                        quitandoId = null,
                        errores = it.errores.copy(porItem = it.errores.porItem - itemId, generales = emptyList()),
                    )
                } else {
                    it.copy(quitandoId = null)
                }
            }
            if (quitado) {
                _eventos.send(EventoCarrito.Mensaje(TextoUi.Recurso(R.string.carrito_quitado)))
                cargar(pantallaCompleta = false)
            } else if (resultado is Resultado.Error) {
                _eventos.send(EventoCarrito.Mensaje(TextoUi.Texto(resultado.mensaje)))
            }
        }
    }

    // --- Enviar (HU-050) ---

    /** Revisa las elecciones en el celular; si están bien, pide la confirmación. */
    fun pedirEnvio() {
        val actual = _estado.value
        val carrito = actual.carrito ?: return
        if (!actual.puedeEnviar) return
        val errores = EnvioCarrito.validar(carrito, actual.elecciones)
        _estado.update { it.copy(erroresLocales = errores) }
        if (errores.isNotEmpty()) return
        _eventos.trySend(EventoCarrito.ConfirmarEnvio(EnvioCarrito.solicitudesACrear(carrito)))
    }

    fun enviar() {
        val actual = _estado.value
        val carrito = actual.carrito ?: return
        if (!actual.puedeEnviar) return
        // Por si algo cambió entre la confirmación y el envío.
        val errores = EnvioCarrito.validar(carrito, actual.elecciones)
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(erroresLocales = errores) }
            return
        }
        carga?.cancel()
        _estado.update { it.copy(enviando = true, erroresLocales = emptyMap(), errores = ErroresEnvio(), refrescando = false) }
        viewModelScope.launch {
            when (val resultado = repositorio.enviarCarrito(EnvioCarrito.cuerpo(carrito, actual.elecciones))) {
                is Resultado.Exito -> {
                    // El servidor vació el carrito (RN-22): se ve vacío aunque la recarga falle.
                    _estado.update {
                        it.copy(
                            carrito = Carrito(donaciones = emptyList(), avisos = emptyList(), puedeEnviar = false),
                            enviando = false,
                            pedidoEnviado = resultado.datos.codigoPedido,
                            elecciones = emptyMap(),
                        )
                    }
                    guardarElecciones()
                    cargar(pantallaCompleta = false)
                }
                is Resultado.Error -> {
                    if (resultado.codigo == 400) {
                        _estado.update { it.copy(enviando = false, errores = EnvioCarrito.repartir(resultado)) }
                        ajustarErrores = true
                        _eventos.send(EventoCarrito.Mensaje(TextoUi.Recurso(R.string.carrito_error_envio)))
                        cargar(pantallaCompleta = false)
                    } else {
                        // Sin conexión, 429, 5xx...: no se creó nada y el carrito sigue igual.
                        _estado.update { it.copy(enviando = false) }
                        _eventos.send(EventoCarrito.Mensaje(TextoUi.Texto(resultado.mensaje)))
                    }
                }
            }
        }
    }

    fun cerrarExito() = _estado.update { it.copy(pedidoEnviado = null) }

    // --- Estado guardado (las elecciones sobreviven también si Android cierra la app) ---

    private fun leerElecciones(): Map<Int, EleccionEntrega> {
        val json = estadoGuardado.get<String>(CLAVE_ELECCIONES) ?: return emptyMap()
        return try {
            Gson().fromJson<Map<Int, EleccionEntrega>>(json, TIPO_ELECCIONES) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun guardarElecciones() {
        estadoGuardado[CLAVE_ELECCIONES] = Gson().toJson(_estado.value.elecciones)
    }

    companion object {
        private const val CLAVE_ELECCIONES = "elecciones"
        private val TIPO_ELECCIONES = object : TypeToken<Map<Int, EleccionEntrega>>() {}.type

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KumoraApp
                CarritoViewModel(app.beneficiarioRepositorio, app.perfilRepositorio, createSavedStateHandle())
            }
        }
    }
}
