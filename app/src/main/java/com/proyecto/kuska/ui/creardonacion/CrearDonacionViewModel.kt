package com.proyecto.kuska.ui.creardonacion

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.material.datepicker.MaterialDatePicker
import com.proyecto.kuska.KumoraApp
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.CategoriaPublica
import com.proyecto.kuska.data.repositorio.DonacionesRepositorio
import com.proyecto.kuska.ui.comun.TextoUi
import com.proyecto.kuska.util.PREFIJO_ERROR_ALIMENTO
import com.proyecto.kuska.util.Resultado
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** Alimento ya agregado a la donación, con el error que mandó el servidor para él (si hubo). */
data class AlimentoEnLista(
    val alimento: AlimentoBorrador,
    val errorServidor: String? = null,
)

data class CrearDonacionEstado(
    val titulo: String = "",
    val descripcion: String = "",
    val direccion: String = "",
    val modalidad: String? = null,
    val alimentos: List<AlimentoEnLista> = emptyList(),
    val errores: Map<CampoDonacion, TextoUi> = emptyMap(),
    /** Mensaje para el aviso rojo. */
    val errorApi: String? = null,
    val enviando: Boolean = false,
    val categorias: List<CategoriaPublica> = emptyList(),
    val cargandoCategorias: Boolean = false,
    val errorCategorias: String? = null,
    /** Dirección que se llenó desde el perfil (no cuenta como dato escrito por el usuario). */
    val direccionPerfil: String? = null,
) {
    val puedeEnviar get() = alimentos.isNotEmpty()

    val tieneDatosSinEnviar
        get() = titulo.isNotBlank() || descripcion.isNotBlank() || modalidad != null ||
            alimentos.isNotEmpty() || direccion.trim() != direccionPerfil.orEmpty().trim()
}

/** Alimento que se está agregando ([indice] = -1) o editando. */
data class EdicionAlimento(
    val indice: Int,
    val alimento: AlimentoBorrador,
    val errores: Map<CampoAlimento, TextoUi> = emptyMap(),
    val procesandoFoto: Boolean = false,
)

/** ViewModel del grafo nav_crear_donacion: lo comparten la donación y el formulario del alimento. */
class CrearDonacionViewModel(
    private val app: Application,
    private val repositorio: DonacionesRepositorio,
) : ViewModel() {

    private val _estado = MutableStateFlow(CrearDonacionEstado())
    val estado: StateFlow<CrearDonacionEstado> = _estado.asStateFlow()

    private val _edicion = MutableStateFlow<EdicionAlimento?>(null)
    val edicion: StateFlow<EdicionAlimento?> = _edicion.asStateFlow()

    private val _alimentoGuardado = Channel<Unit>(Channel.BUFFERED)
    val alimentoGuardado: Flow<Unit> = _alimentoGuardado.receiveAsFlow()

    private val _donacionEnviada = Channel<Unit>(Channel.BUFFERED)
    val donacionEnviada: Flow<Unit> = _donacionEnviada.receiveAsFlow()

    init {
        cargarCategorias()
        cargarDireccionPerfil()
    }

    // --- Donación ---

    /** Se ignora si el valor no cambió (p. ej. cuando la vista restaura su texto al rotar). */
    fun cambiarCampo(campo: CampoDonacion, valor: String) {
        val actual = _estado.value
        val anterior = when (campo) {
            CampoDonacion.TITULO -> actual.titulo
            CampoDonacion.DESCRIPCION -> actual.descripcion
            CampoDonacion.DIRECCION -> actual.direccion
            CampoDonacion.MODALIDAD -> return
        }
        if (anterior == valor) return
        _estado.update {
            val conValor = when (campo) {
                CampoDonacion.TITULO -> it.copy(titulo = valor)
                CampoDonacion.DESCRIPCION -> it.copy(descripcion = valor)
                else -> it.copy(direccion = valor)
            }
            conValor.copy(errores = it.errores - campo)
        }
    }

    fun elegirModalidad(modalidad: String?) {
        if (_estado.value.modalidad == modalidad) return
        _estado.update { it.copy(modalidad = modalidad, errores = it.errores - CampoDonacion.MODALIDAD) }
    }

    fun quitarAlimento(indice: Int) {
        if (_estado.value.enviando) return
        val quitado = _estado.value.alimentos.getOrNull(indice) ?: return
        _estado.update { it.copy(alimentos = it.alimentos.filterIndexed { i, _ -> i != indice }) }
        FotosAlimento.borrar(app, quitado.alimento.rutaFoto)
    }

    fun enviar() {
        val actual = _estado.value
        if (actual.enviando || !actual.puedeEnviar) return
        val errores = ValidacionDonacion.validar(actual.titulo, actual.direccion, actual.modalidad)
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(errores = errores, errorApi = null) }
            return
        }
        val modalidad = actual.modalidad ?: return
        val alimentos = actual.alimentos.map { it.alimento }
        val datos = ConstructorDatos.construir(actual.titulo, actual.descripcion, actual.direccion, modalidad, alimentos)
        val fotos = alimentos.map { File(requireNotNull(it.rutaFoto)) }

        _estado.update {
            it.copy(
                enviando = true,
                errores = emptyMap(),
                errorApi = null,
                alimentos = it.alimentos.map { a -> a.copy(errorServidor = null) },
            )
        }
        viewModelScope.launch {
            when (val resultado = repositorio.crearDonacion(datos, fotos)) {
                is Resultado.Exito -> {
                    FotosAlimento.borrarTodas(app)
                    // Se deja "enviando" mientras la pantalla se cierra.
                    _donacionEnviada.send(Unit)
                }
                is Resultado.Error -> mostrarErroresEnvio(resultado)
            }
        }
    }

    /** Errores de campo a su campo, los de cada alimento a su tarjeta y el resto al aviso rojo. */
    private fun mostrarErroresEnvio(error: Resultado.Error) {
        val erroresCampo = mutableMapOf<CampoDonacion, TextoUi>()
        val erroresAlimento = mutableMapOf<Int, String>()
        val generales = mutableListOf<String>()
        val cantidad = _estado.value.alimentos.size

        for ((clave, mensaje) in error.erroresCampo) {
            val campo = CAMPOS_API[clave]
            val indice = indiceAlimento(clave)
            when {
                campo != null -> erroresCampo[campo] = TextoUi.Texto(mensaje)
                indice != null && indice in 0 until cantidad -> erroresAlimento.putIfAbsent(indice, mensaje)
                else -> generales += mensaje
            }
        }
        val aviso = when {
            generales.isNotEmpty() -> generales.first()
            erroresCampo.isEmpty() && erroresAlimento.isEmpty() -> error.mensaje
            erroresAlimento.isNotEmpty() -> app.getString(R.string.crear_donacion_revisa_alimentos)
            else -> null
        }
        _estado.update {
            it.copy(
                enviando = false,
                errores = erroresCampo,
                errorApi = aviso,
                alimentos = it.alimentos.mapIndexed { i, a -> a.copy(errorServidor = erroresAlimento[i]) },
            )
        }
    }

    /** Sale del flujo sin enviar: borra las fotos tomadas. */
    fun descartar() {
        FotosAlimento.borrarTodas(app)
    }

    fun cargarCategorias() {
        if (_estado.value.cargandoCategorias) return
        _estado.update { it.copy(cargandoCategorias = true, errorCategorias = null) }
        viewModelScope.launch {
            when (val resultado = repositorio.obtenerCategorias()) {
                is Resultado.Exito -> _estado.update {
                    it.copy(cargandoCategorias = false, categorias = resultado.datos)
                }
                is Resultado.Error -> _estado.update {
                    it.copy(cargandoCategorias = false, errorCategorias = resultado.mensaje)
                }
            }
        }
    }

    /** Llena la dirección con la del perfil si el usuario aún no escribió una. Si falla, el campo queda como está. */
    private fun cargarDireccionPerfil() {
        viewModelScope.launch {
            val resultado = repositorio.obtenerDireccionPerfil()
            val direccion = (resultado as? Resultado.Exito)?.datos?.trim()
            if (direccion.isNullOrEmpty()) return@launch
            _estado.update {
                if (it.direccion.isBlank()) it.copy(direccion = direccion, direccionPerfil = direccion) else it
            }
        }
    }

    // --- Alimento ---

    /** Prepara el formulario. Si ya se está editando ese mismo alimento (p. ej. al rotar), lo conserva. */
    fun iniciarEdicion(indice: Int) {
        if (_edicion.value?.indice == indice) return
        val alimento = _estado.value.alimentos.getOrNull(indice)?.alimento
        _edicion.value = if (alimento != null) EdicionAlimento(indice, alimento) else EdicionAlimento(-1, AlimentoBorrador())
    }

    fun cambiarCampoAlimento(campo: CampoAlimento, valor: String) {
        val edicion = _edicion.value ?: return
        val anterior = when (campo) {
            CampoAlimento.NOMBRE -> edicion.alimento.nombre
            CampoAlimento.DESCRIPCION -> edicion.alimento.descripcion
            CampoAlimento.CANTIDAD -> edicion.alimento.cantidad
            else -> return
        }
        if (anterior == valor) return
        actualizarAlimento(campo) {
            when (campo) {
                CampoAlimento.NOMBRE -> it.copy(nombre = valor)
                CampoAlimento.DESCRIPCION -> it.copy(descripcion = valor)
                else -> it.copy(cantidad = valor)
            }
        }
    }

    fun elegirCategoria(categoria: CategoriaPublica) = actualizarAlimento(CampoAlimento.CATEGORIA) {
        it.copy(categoriaId = categoria.id, categoriaNombre = categoria.nombre)
    }

    /** Al cambiar la unidad se vuelve a validar la cantidad ya escrita (entera o con decimales, RN-10a). */
    fun elegirUnidad(unidad: String) {
        val edicion = _edicion.value ?: return
        val alimento = edicion.alimento.copy(unidadMedida = unidad)
        val errorCantidad = alimento.cantidad.takeIf { it.isNotBlank() }
            ?.let { ValidacionAlimento.validarCantidad(it, unidad) }
        val errores = (edicion.errores - CampoAlimento.UNIDAD - CampoAlimento.CANTIDAD).let {
            if (errorCantidad != null) it + (CampoAlimento.CANTIDAD to errorCantidad) else it
        }
        _edicion.value = edicion.copy(alimento = alimento, errores = errores)
    }

    fun elegirFecha(fechaUtc: Long) = actualizarAlimento(CampoAlimento.FECHA) { it.copy(fechaVencimiento = fechaUtc) }

    /** Reduce la foto elegida o tomada. [archivoCamara]: el archivo original de la cámara, que se borra después. */
    fun procesarFoto(origen: Uri, archivoCamara: File? = null) {
        if (_edicion.value == null) return
        _edicion.update { it?.copy(procesandoFoto = true) }
        viewModelScope.launch {
            val reducida = FotosAlimento.reducir(app, origen)
            archivoCamara?.delete()
            val edicion = _edicion.value
            if (edicion == null) {
                FotosAlimento.borrar(app, reducida?.path)
                return@launch
            }
            if (reducida == null) {
                _edicion.value = edicion.copy(
                    procesandoFoto = false,
                    errores = edicion.errores + (CampoAlimento.FOTO to TextoUi.Recurso(R.string.error_foto_no_leida)),
                )
                return@launch
            }
            borrarFotoSiNoSeUsa(edicion.alimento.rutaFoto)
            _edicion.value = edicion.copy(
                alimento = edicion.alimento.copy(rutaFoto = reducida.path),
                errores = edicion.errores - CampoAlimento.FOTO,
                procesandoFoto = false,
            )
        }
    }

    fun guardarAlimento() {
        val edicion = _edicion.value ?: return
        if (edicion.procesandoFoto) return
        val errores = ValidacionAlimento.validar(edicion.alimento, MaterialDatePicker.todayInUtcMilliseconds())
        if (errores.isNotEmpty()) {
            _edicion.value = edicion.copy(errores = errores)
            return
        }
        val anterior = _estado.value.alimentos.getOrNull(edicion.indice)?.alimento
        _estado.update {
            val nuevo = AlimentoEnLista(edicion.alimento)
            val lista = if (anterior != null) {
                it.alimentos.mapIndexed { i, a -> if (i == edicion.indice) nuevo else a }
            } else {
                it.alimentos + nuevo
            }
            it.copy(alimentos = lista)
        }
        if (anterior != null && anterior.rutaFoto != edicion.alimento.rutaFoto) {
            FotosAlimento.borrar(app, anterior.rutaFoto)
        }
        _edicion.value = null
        _alimentoGuardado.trySend(Unit)
    }

    /** Sale del formulario sin guardar: borra la foto nueva si no la usa ningún alimento de la lista. */
    fun cancelarEdicion() {
        val edicion = _edicion.value ?: return
        borrarFotoSiNoSeUsa(edicion.alimento.rutaFoto)
        _edicion.value = null
    }

    private fun actualizarAlimento(campo: CampoAlimento, cambio: (AlimentoBorrador) -> AlimentoBorrador) {
        val edicion = _edicion.value ?: return
        _edicion.value = edicion.copy(alimento = cambio(edicion.alimento), errores = edicion.errores - campo)
    }

    private fun borrarFotoSiNoSeUsa(ruta: String?) {
        if (ruta == null || _estado.value.alimentos.any { it.alimento.rutaFoto == ruta }) return
        FotosAlimento.borrar(app, ruta)
    }

    override fun onCleared() {
        // El grafo se cerró (enviada, descartada o cierre de sesión): ninguna foto temporal sirve ya.
        FotosAlimento.borrarTodas(app)
    }

    companion object {
        /** Campos de la donación en la respuesta de error de la API. */
        private val CAMPOS_API = mapOf(
            "titulo" to CampoDonacion.TITULO,
            "descripcion" to CampoDonacion.DESCRIPCION,
            "direccion_recogida" to CampoDonacion.DIRECCION,
            "modalidad_entrega" to CampoDonacion.MODALIDAD,
        )

        /** "alimentos.1" o "foto_1" → 1. */
        internal fun indiceAlimento(clave: String): Int? = when {
            clave.startsWith(PREFIJO_ERROR_ALIMENTO) -> clave.removePrefix(PREFIJO_ERROR_ALIMENTO).toIntOrNull()
            clave.startsWith("foto_") -> clave.removePrefix("foto_").toIntOrNull()
            else -> null
        }

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KumoraApp
                CrearDonacionViewModel(app, app.donacionesRepositorio)
            }
        }
    }
}
