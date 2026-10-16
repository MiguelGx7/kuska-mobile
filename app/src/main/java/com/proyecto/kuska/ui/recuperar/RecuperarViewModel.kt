package com.proyecto.kuska.ui.recuperar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.proyecto.kuska.KumoraApp
import com.proyecto.kuska.data.repositorio.AuthRepositorio
import com.proyecto.kuska.ui.comun.ContadorReenvio
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

data class RecuperarEstado(
    /** 1 = correo, 2 = código y nueva contraseña. */
    val paso: Int = 1,
    val correo: String = "",
    val codigo: String = "",
    val contrasena: String = "",
    val confirmacion: String = "",
    val errores: Map<CampoRecuperar, TextoUi> = emptyMap(),
    /** Mensaje de la API para el aviso rojo del paso actual. */
    val errorApi: String? = null,
    val cargando: Boolean = false,
    /** Lo que respondió auth/recuperar/solicitar/ (null = usar el texto por defecto). */
    val mensajeEnvio: String? = null,
    /** Correo al que se pidió el último código (null si aún no se ha pedido). */
    val correoConCodigo: String? = null,
    val segundosReenvio: Int = 0,
) {
    /** Si el correo no cambió desde el último envío, el paso 1 continúa sin pedir otro código. */
    val codigoYaEnviado get() = correoConCodigo != null && correo.trim() == correoConCodigo
}

sealed interface EventoRecuperar {
    /** [mensaje]: lo que respondió la API (null = usar el texto por defecto). */
    data class CodigoReenviado(val mensaje: String?) : EventoRecuperar
    data class ContrasenaCambiada(val correo: String) : EventoRecuperar
}

class RecuperarViewModel(
    private val repositorio: AuthRepositorio,
    estadoGuardado: SavedStateHandle,
) : ViewModel() {

    private val _estado = MutableStateFlow(
        RecuperarEstado(correo = estadoGuardado.get<String>(ARG_CORREO).orEmpty())
    )
    val estado: StateFlow<RecuperarEstado> = _estado.asStateFlow()

    private val _eventos = Channel<EventoRecuperar>(Channel.BUFFERED)
    val eventos: Flow<EventoRecuperar> = _eventos.receiveAsFlow()

    private val contador = ContadorReenvio(viewModelScope) { segundos ->
        _estado.update { it.copy(segundosReenvio = segundos) }
    }

    /** Se ignora si el valor no cambió (p. ej. cuando la vista restaura su texto al rotar). */
    fun cambiarCampo(campo: CampoRecuperar, valor: String) {
        val actual = _estado.value
        val anterior = when (campo) {
            CampoRecuperar.CORREO -> actual.correo
            CampoRecuperar.CODIGO -> actual.codigo
            CampoRecuperar.CONTRASENA_NUEVA -> actual.contrasena
            CampoRecuperar.CONFIRMAR_CONTRASENA -> actual.confirmacion
        }
        if (anterior == valor) return
        _estado.update {
            val conValor = when (campo) {
                CampoRecuperar.CORREO -> it.copy(correo = valor)
                CampoRecuperar.CODIGO -> it.copy(codigo = valor)
                CampoRecuperar.CONTRASENA_NUEVA -> it.copy(contrasena = valor)
                CampoRecuperar.CONFIRMAR_CONTRASENA -> it.copy(confirmacion = valor)
            }
            conValor.copy(errores = it.errores - campo)
        }
    }

    /** Botón principal: Enviar código / Continuar (paso 1), Cambiar contraseña (paso 2). */
    fun accionPrincipal() {
        val actual = _estado.value
        if (actual.cargando) return
        if (actual.paso == 1) enviarCodigo() else cambiarContrasena()
    }

    /** Vuelve al paso anterior. Devuelve false en el paso 1 (la pantalla debe cerrarse). */
    fun atras(): Boolean {
        val actual = _estado.value
        if (actual.paso == 1) return false
        if (!actual.cargando) _estado.update { it.copy(paso = 1, errorApi = null) }
        return true
    }

    fun reenviarCodigo() {
        val actual = _estado.value
        val correo = actual.correoConCodigo ?: return
        if (actual.cargando || actual.segundosReenvio > 0) return

        _estado.update { it.copy(cargando = true, errorApi = null) }
        viewModelScope.launch {
            when (val resultado = repositorio.solicitarRecuperacion(correo)) {
                is Resultado.Exito -> {
                    val mensaje = resultado.datos?.takeIf { it.isNotBlank() }
                    _estado.update { it.copy(cargando = false, mensajeEnvio = mensaje) }
                    contador.iniciar()
                    _eventos.send(EventoRecuperar.CodigoReenviado(mensaje))
                }
                is Resultado.Error -> _estado.update {
                    it.copy(cargando = false, errorApi = resultado.mensaje)
                }
            }
        }
    }

    private fun enviarCodigo() {
        val actual = _estado.value
        val errores = ValidacionRecuperar.validarPasoCorreo(actual.correo)
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(errores = errores, errorApi = null) }
            return
        }
        if (actual.codigoYaEnviado) {
            _estado.update { it.copy(paso = 2, errores = emptyMap(), errorApi = null) }
            return
        }

        val correo = actual.correo.trim()
        _estado.update { it.copy(cargando = true, errores = emptyMap(), errorApi = null) }
        viewModelScope.launch {
            when (val resultado = repositorio.solicitarRecuperacion(correo)) {
                is Resultado.Exito -> {
                    _estado.update {
                        it.copy(
                            cargando = false,
                            paso = 2,
                            correoConCodigo = correo,
                            mensajeEnvio = resultado.datos?.takeIf { mensaje -> mensaje.isNotBlank() },
                        )
                    }
                    contador.iniciar()
                }
                is Resultado.Error -> {
                    val errorCorreo = resultado.erroresCampo[CAMPO_API_CORREO]
                    _estado.update {
                        if (errorCorreo != null) {
                            it.copy(cargando = false, errores = mapOf(CampoRecuperar.CORREO to TextoUi.Texto(errorCorreo)))
                        } else {
                            it.copy(cargando = false, errorApi = resultado.mensaje)
                        }
                    }
                }
            }
        }
    }

    private fun cambiarContrasena() {
        val actual = _estado.value
        val errores = ValidacionRecuperar.validarPasoCambio(actual.codigo, actual.contrasena, actual.confirmacion)
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(errores = errores, errorApi = null) }
            return
        }
        val correo = actual.correoConCodigo ?: return

        _estado.update { it.copy(cargando = true, errores = emptyMap(), errorApi = null) }
        viewModelScope.launch {
            val resultado = repositorio.confirmarRecuperacion(correo, actual.codigo.trim(), actual.contrasena)
            when (resultado) {
                is Resultado.Exito -> {
                    // Se deja "cargando" mientras la pantalla se cierra.
                    _eventos.send(EventoRecuperar.ContrasenaCambiada(correo))
                }
                is Resultado.Error -> {
                    // Las reglas de la contraseña van a su campo. El código inválido, vencido o sin
                    // intentos, y cualquier otro error (429, sin red...), al aviso del paso 2.
                    val errorContrasena = resultado.erroresCampo[CAMPO_API_CONTRASENA]
                    val otroError = if (errorContrasena == null) {
                        resultado.mensaje
                    } else {
                        (resultado.erroresCampo - CAMPO_API_CONTRASENA).values.firstOrNull()
                    }
                    _estado.update {
                        it.copy(
                            cargando = false,
                            errores = errorContrasena
                                ?.let { mensaje -> mapOf(CampoRecuperar.CONTRASENA_NUEVA to TextoUi.Texto(mensaje)) }
                                .orEmpty(),
                            errorApi = otroError,
                        )
                    }
                }
            }
        }
    }

    companion object {
        /** Argumento de navegación (nav_graph): correo escrito en el Login. */
        const val ARG_CORREO = "correo"

        /** Clave en el SavedStateHandle del Login para avisarle que la contraseña cambió. */
        const val RESULTADO_CORREO = "correo_contrasena_cambiada"

        private const val CAMPO_API_CORREO = "correo"
        private const val CAMPO_API_CONTRASENA = "contrasena_nueva"

        val Factory = viewModelFactory {
            initializer {
                RecuperarViewModel(
                    (this[APPLICATION_KEY] as KumoraApp).authRepositorio,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
