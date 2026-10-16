package com.proyecto.kuska.ui.registro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.proyecto.kuska.KumoraApp
import com.proyecto.kuska.data.api.modelos.RegistroRequest
import com.proyecto.kuska.data.repositorio.AuthRepositorio
import com.proyecto.kuska.ui.comun.ContadorReenvio
import com.proyecto.kuska.ui.comun.TextoUi
import com.proyecto.kuska.ui.comun.validarCodigo
import com.proyecto.kuska.util.Resultado
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegistroEstado(
    /** 1 = rol, 2 = datos, 3 = código. */
    val paso: Int = 1,
    val datos: DatosRegistro = DatosRegistro(),
    val errores: Map<CampoRegistro, TextoUi> = emptyMap(),
    val codigo: String = "",
    val errorCodigo: TextoUi? = null,
    /** Mensaje de la API para el aviso rojo del paso actual. */
    val errorApi: String? = null,
    val cargando: Boolean = false,
    /** Correo al que se envió el último código (null si aún no se ha enviado). */
    val correoConCodigo: String? = null,
    val segundosReenvio: Int = 0,
) {
    /** Si el correo no cambió desde el último envío, el paso 2 continúa sin pedir otro código. */
    val codigoYaEnviado get() = correoConCodigo != null && datos.correo.trim() == correoConCodigo
}

sealed interface EventoRegistro {
    data object CodigoReenviado : EventoRegistro
}

class RegistroViewModel(private val repositorio: AuthRepositorio) : ViewModel() {

    private val _estado = MutableStateFlow(RegistroEstado())
    val estado: StateFlow<RegistroEstado> = _estado.asStateFlow()

    private val _eventos = Channel<EventoRegistro>(Channel.BUFFERED)
    val eventos: Flow<EventoRegistro> = _eventos.receiveAsFlow()

    private val contador = ContadorReenvio(viewModelScope) { segundos ->
        _estado.update { it.copy(segundosReenvio = segundos) }
    }

    fun elegirRol(rol: String) = _estado.update {
        it.copy(datos = it.datos.copy(rol = rol), errorApi = null)
    }

    fun elegirTipoDonante(tipo: String) {
        if (_estado.value.datos.tipoDonante == tipo) return
        _estado.update {
            it.copy(
                datos = it.datos.copy(tipoDonante = tipo),
                errores = it.errores - CAMPOS_DONANTE,
            )
        }
    }

    fun elegirTipoDocumento(tipo: String) {
        if (_estado.value.datos.tipoDocumento == tipo) return
        _estado.update {
            it.copy(
                datos = it.datos.copy(tipoDocumento = tipo),
                errores = it.errores - CampoRegistro.TIPO_DOCUMENTO,
            )
        }
    }

    /** Se ignora si el valor no cambió (p. ej. cuando la vista restaura su texto al rotar). */
    fun cambiarCampo(campo: CampoRegistro, valor: String) {
        if (_estado.value.datos.valor(campo) == valor) return
        _estado.update { it.copy(datos = it.datos.con(campo, valor), errores = it.errores - campo) }
    }

    fun cambiarCodigo(valor: String) {
        if (_estado.value.codigo == valor) return
        _estado.update { it.copy(codigo = valor, errorCodigo = null) }
    }

    /** Botón principal: Continuar (paso 1), Enviar código / Continuar (paso 2), Crear cuenta (paso 3). */
    fun accionPrincipal() {
        val actual = _estado.value
        if (actual.cargando) return
        when (actual.paso) {
            1 -> if (actual.datos.rol != null) _estado.update { it.copy(paso = 2, errorApi = null) }
            2 -> enviarCodigo()
            3 -> crearCuenta()
        }
    }

    /** Vuelve al paso anterior. Devuelve false en el paso 1 (la pantalla debe cerrarse). */
    fun atras(): Boolean {
        val actual = _estado.value
        if (actual.paso == 1) return false
        if (!actual.cargando) _estado.update { it.copy(paso = it.paso - 1, errorApi = null) }
        return true
    }

    fun reenviarCodigo() {
        val actual = _estado.value
        val correo = actual.correoConCodigo ?: return
        if (actual.cargando || actual.segundosReenvio > 0) return

        _estado.update { it.copy(cargando = true, errorApi = null) }
        viewModelScope.launch {
            when (val resultado = repositorio.solicitarCodigoRegistro(correo)) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(cargando = false) }
                    contador.iniciar()
                    _eventos.send(EventoRegistro.CodigoReenviado)
                }
                is Resultado.Error -> _estado.update {
                    it.copy(cargando = false, errorApi = resultado.mensaje)
                }
            }
        }
    }

    private fun enviarCodigo() {
        val actual = _estado.value
        val errores = ValidacionRegistro.validar(actual.datos)
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(errores = errores, errorApi = null) }
            return
        }
        if (actual.codigoYaEnviado) {
            _estado.update { it.copy(paso = 3, errores = emptyMap(), errorApi = null) }
            return
        }

        val correo = actual.datos.correo.trim()
        _estado.update { it.copy(cargando = true, errores = emptyMap(), errorApi = null) }
        viewModelScope.launch {
            when (val resultado = repositorio.solicitarCodigoRegistro(correo)) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(cargando = false, paso = 3, correoConCodigo = correo) }
                    contador.iniciar()
                }
                // El correo ya registrado (RN-02) llega como error del campo "correo".
                is Resultado.Error -> {
                    val porCampo = erroresDeCampos(resultado.erroresCampo)
                    _estado.update {
                        it.copy(
                            cargando = false,
                            errores = porCampo,
                            errorApi = resultado.mensaje.takeIf { porCampo.isEmpty() },
                        )
                    }
                }
            }
        }
    }

    private fun crearCuenta() {
        val actual = _estado.value
        val codigo = actual.codigo.trim()
        val errorCodigo = validarCodigo(codigo)
        if (errorCodigo != null) {
            _estado.update { it.copy(errorCodigo = errorCodigo, errorApi = null) }
            return
        }

        _estado.update { it.copy(cargando = true, errorApi = null) }
        viewModelScope.launch {
            when (val resultado = repositorio.registrar(actual.datos.aSolicitud(codigo))) {
                // MainActivity reacciona a la sesión guardada; se deja "cargando" mientras cambia de pantalla.
                is Resultado.Exito -> Unit
                is Resultado.Error -> {
                    // Errores de los datos → al paso 2, cada uno en su campo. El del código
                    // (no es un campo del paso 2), 429, 503 o sin red → aviso en el paso 3.
                    val porCampo = erroresDeCampos(resultado.erroresCampo)
                    _estado.update {
                        if (porCampo.isNotEmpty()) {
                            it.copy(cargando = false, paso = 2, errores = porCampo)
                        } else {
                            it.copy(cargando = false, errorApi = resultado.mensaje)
                        }
                    }
                }
            }
        }
    }

    private fun erroresDeCampos(erroresApi: Map<String, String>): Map<CampoRegistro, TextoUi> =
        erroresApi.mapNotNull { (nombre, mensaje) ->
            CampoRegistro.desdeApi(nombre)?.let { it to TextoUi.Texto(mensaje) }
        }.toMap()

    companion object {
        private val CAMPOS_DONANTE = setOf(
            CampoRegistro.TIPO_DONANTE,
            CampoRegistro.NOMBRE_NEGOCIO,
            CampoRegistro.NIT,
        )

        val Factory = viewModelFactory {
            initializer {
                RegistroViewModel((this[APPLICATION_KEY] as KumoraApp).authRepositorio)
            }
        }
    }
}
