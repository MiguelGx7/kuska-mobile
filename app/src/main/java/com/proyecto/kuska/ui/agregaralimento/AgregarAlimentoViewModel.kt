package com.proyecto.kuska.ui.agregaralimento

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.proyecto.kuska.KumoraApp
import com.proyecto.kuska.data.repositorio.DonacionesRepositorio
import com.proyecto.kuska.ui.comun.ErroresAccion
import com.proyecto.kuska.ui.comun.TextoUi
import com.proyecto.kuska.ui.comun.formularioalimento.CategoriasAlimento
import com.proyecto.kuska.ui.comun.formularioalimento.EditorAlimento
import com.proyecto.kuska.ui.comun.formularioalimento.EstadoCategorias
import com.proyecto.kuska.ui.comun.formularioalimento.EstadoGuardadoAlimento
import com.proyecto.kuska.ui.comun.formularioalimento.FormularioAlimento
import com.proyecto.kuska.ui.creardonacion.AlimentoBorrador
import com.proyecto.kuska.ui.creardonacion.CampoAlimento
import com.proyecto.kuska.ui.creardonacion.ConstructorDatos
import com.proyecto.kuska.ui.creardonacion.FotosAlimento
import com.proyecto.kuska.ui.detalledonacion.DetalleDonacionFragment
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

/**
 * Agregar un alimento a una donación Pendiente: "Guardar alimento" lo envía a POST donaciones/{id}/alimentos/.
 * Solo borra sus propias fotos (nunca toda la carpeta, que también usa crear donación).
 */
class AgregarAlimentoViewModel(
    private val app: Application,
    private val repositorio: DonacionesRepositorio,
    estadoGuardado: SavedStateHandle,
) : ViewModel(), EditorAlimento {

    private val id: Int = requireNotNull(estadoGuardado.get<Int>(DetalleDonacionFragment.ARG_ID))

    override val formulario = FormularioAlimento(
        scope = viewModelScope,
        borrarFoto = { FotosAlimento.borrar(app, it) },
    )

    private val categoriasAlimento = CategoriasAlimento(viewModelScope, repositorio::obtenerCategorias)
    override val categorias: StateFlow<EstadoCategorias> = categoriasAlimento.estado

    private val _estadoGuardado = MutableStateFlow(EstadoGuardadoAlimento())
    override val estadoGuardado: StateFlow<EstadoGuardadoAlimento> = _estadoGuardado.asStateFlow()

    private val _alimentoGuardado = Channel<Unit>(Channel.BUFFERED)
    override val alimentoGuardado: Flow<Unit> = _alimentoGuardado.receiveAsFlow()

    /** La donación ya no admite alimentos: se cierra y el detalle muestra el mensaje en su aviso rojo. */
    private val _yaNoEditable = Channel<String>(Channel.BUFFERED)
    val yaNoEditable: Flow<String> = _yaNoEditable.receiveAsFlow()

    init {
        cargarCategorias()
    }

    /** Siempre un alimento nuevo; si ya hay uno a medias (p. ej. al rotar), se conserva. */
    override fun iniciarEdicion(indice: Int) = formulario.iniciar(-1, null)

    override fun cargarCategorias() = categoriasAlimento.cargar()

    override fun procesarFoto(origen: Uri, archivoCamara: File?) =
        formulario.procesarFoto({ FotosAlimento.reducir(app, origen) }, archivoCamara)

    /** Hay algo escrito o elegido (para preguntar antes de salir). */
    val tieneDatos get() = formulario.edicion.value?.alimento?.let { it != AlimentoBorrador() } == true

    override fun guardarAlimento() {
        if (_estadoGuardado.value.guardando) return
        val edicion = formulario.validar() ?: return
        val ruta = requireNotNull(edicion.alimento.rutaFoto)
        val datos = ConstructorDatos.construirAlimento(edicion.alimento)

        _estadoGuardado.value = EstadoGuardadoAlimento(guardando = true)
        viewModelScope.launch {
            when (val resultado = repositorio.agregarAlimento(id, datos, File(ruta))) {
                is Resultado.Exito -> {
                    // Ya está en el servidor: la copia temporal no sirve.
                    FotosAlimento.borrar(app, ruta)
                    formulario.terminar()
                    // Se deja "guardando" mientras la pantalla se cierra.
                    _alimentoGuardado.send(Unit)
                }
                is Resultado.Error -> mostrarError(resultado)
            }
        }
    }

    /** Errores de campo a su campo; el resto al aviso rojo, salvo que la donación ya no se pueda editar. */
    private suspend fun mostrarError(error: Resultado.Error) {
        val separados = ErroresAccion.separar(error.erroresCampo, CAMPOS_API)
        val aviso = ErroresAccion.aviso(error, separados)
        if (ErroresAccion.debeRevisarEstado(error) &&
            !ErroresAccion.sigueEditable(repositorio.obtenerDonacion(id))
        ) {
            formulario.cancelar()
            _yaNoEditable.send(aviso ?: error.mensaje)
            return
        }
        formulario.mostrarErrores(separados.porCampo.mapValues { (_, mensaje) -> TextoUi.Texto(mensaje) })
        _estadoGuardado.update { EstadoGuardadoAlimento(guardando = false, error = aviso) }
    }

    override fun cancelarEdicion() = formulario.cancelar()

    override fun onCleared() {
        // Si se cerró sin guardar (p. ej. cierre de sesión), borra la foto que quedó a medias.
        formulario.cancelar()
    }

    companion object {
        /** Campos de AlimentoNuevoRequest en la respuesta de error. */
        private val CAMPOS_API = mapOf(
            "categoria" to CampoAlimento.CATEGORIA,
            "nombre" to CampoAlimento.NOMBRE,
            "descripcion" to CampoAlimento.DESCRIPCION,
            "unidad_medida" to CampoAlimento.UNIDAD,
            "cantidad_total" to CampoAlimento.CANTIDAD,
            "fecha_vencimiento" to CampoAlimento.FECHA,
            "foto" to CampoAlimento.FOTO,
        )

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KumoraApp
                AgregarAlimentoViewModel(app, app.donacionesRepositorio, createSavedStateHandle())
            }
        }
    }
}
