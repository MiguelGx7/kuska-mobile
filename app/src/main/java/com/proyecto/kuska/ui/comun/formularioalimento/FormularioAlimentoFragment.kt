package com.proyecto.kuska.ui.comun.formularioalimento

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.addCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil.load
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.CategoriaPublica
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.databinding.FragmentAlimentoBinding
import com.proyecto.kuska.ui.comun.alPulsarListo
import com.proyecto.kuska.ui.comun.mostrar
import com.proyecto.kuska.ui.comun.mostrarAviso
import com.proyecto.kuska.ui.comun.resolver
import com.proyecto.kuska.ui.creardonacion.AlimentoBorrador
import com.proyecto.kuska.ui.creardonacion.CampoAlimento
import com.proyecto.kuska.ui.creardonacion.FormatoAlimento
import com.proyecto.kuska.ui.creardonacion.FotosAlimento
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File

/**
 * Formulario de un alimento: foto (cámara o galería), nombre, descripción, categoría, unidad, cantidad y
 * fecha de vencimiento. Cada subclase dice de dónde sale su [editor] (qué ViewModel guarda el alimento).
 */
abstract class FormularioAlimentoFragment : Fragment() {

    private var _binding: FragmentAlimentoBinding? = null
    protected val binding get() = _binding!!

    protected abstract val editor: EditorAlimento

    /** Posición del alimento a editar, o -1 para uno nuevo. */
    protected open fun indiceInicial(): Int = -1

    private lateinit var campos: Map<CampoAlimento, Pair<TextInputLayout, EditText>>

    /** Archivo donde la cámara está escribiendo la foto. Se guarda al rotar o si el sistema cierra la app. */
    private var archivoCamara: File? = null

    private var categoriasMostradas: List<CategoriaPublica>? = null
    private var fotoMostrada: String? = null

    private val tomarFoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { tomada ->
        val archivo = archivoCamara ?: return@registerForActivityResult
        archivoCamara = null
        if (tomada && archivo.length() > 0) {
            editor.procesarFoto(Uri.fromFile(archivo), archivoCamara = archivo)
        } else {
            archivo.delete()
        }
    }

    /** Photo Picker: no necesita permisos de almacenamiento. */
    private val elegirFoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { editor.procesarFoto(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        archivoCamara = savedInstanceState?.getString(ESTADO_ARCHIVO_CAMARA)?.let(::File)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAlimentoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val indice = indiceInicial()
        editor.iniciarEdicion(indice)
        binding.barra.setTitle(if (indice >= 0) R.string.titulo_editar_alimento else R.string.titulo_agregar_alimento)

        val formulario = editor.formulario
        campos = with(binding) {
            mapOf(
                CampoAlimento.NOMBRE to (campoNombre to entradaNombre),
                CampoAlimento.DESCRIPCION to (campoDescripcion to entradaDescripcion),
                CampoAlimento.CANTIDAD to (campoCantidad to entradaCantidad),
            )
        }
        val actual = formulario.edicion.value?.alimento
        campos.forEach { (campo, par) ->
            val valor = actual?.let { textoDe(it, campo) }.orEmpty()
            if (par.second.text.toString() != valor) par.second.setText(valor)
            par.second.doAfterTextChanged { formulario.cambiarCampo(campo, it?.toString().orEmpty()) }
        }
        binding.entradaCantidad.alPulsarListo { ocultarTeclado() }

        binding.entradaUnidad.setSimpleItems(
            Donacion.UNIDADES.map { getString(FormatoAlimento.etiquetaUnidad(it)) }.toTypedArray()
        )
        binding.entradaUnidad.setOnItemClickListener { _, _, posicion, _ ->
            formulario.elegirUnidad(Donacion.UNIDADES[posicion])
        }
        binding.entradaCategoria.setOnItemClickListener { _, _, posicion, _ ->
            categoriasMostradas?.getOrNull(posicion)?.let(formulario::elegirCategoria)
        }
        binding.botonReintentarCategorias.setOnClickListener { editor.cargarCategorias() }

        binding.botonCamara.setOnClickListener { abrirCamara() }
        binding.botonGaleria.setOnClickListener {
            elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.entradaFecha.setOnClickListener { abrirCalendario() }
        // Si el calendario quedó abierto al rotar, se vuelve a conectar.
        (childFragmentManager.findFragmentByTag(ETIQUETA_CALENDARIO) as? MaterialDatePicker<*>)
            ?.let { conectarCalendario(it) }

        binding.botonGuardar.boton.setOnClickListener {
            ocultarTeclado()
            editor.guardarAlimento()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { alSalir() }
        binding.barra.setNavigationOnClickListener { alSalir() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(formulario.edicion, editor.estadoGuardado) { edicion, guardado -> edicion to guardado }
                        .collect { (edicion, guardado) -> edicion?.let { mostrar(it, guardado) } }
                }
                launch { editor.categorias.collect(::mostrarCategorias) }
                launch { editor.alimentoGuardado.collect { alGuardar() } }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        archivoCamara?.let { outState.putString(ESTADO_ARCHIVO_CAMARA, it.path) }
    }

    /** El alimento quedó guardado: por defecto solo cierra el formulario. */
    protected open fun alGuardar() {
        findNavController().popBackStack()
    }

    /** Atrás o flecha: por defecto sale sin guardar. */
    protected open fun alSalir() {
        if (editor.estadoGuardado.value.guardando) return
        cancelar()
    }

    protected fun cancelar() {
        editor.cancelarEdicion()
        findNavController().popBackStack()
    }

    private fun textoDe(alimento: AlimentoBorrador, campo: CampoAlimento) = when (campo) {
        CampoAlimento.NOMBRE -> alimento.nombre
        CampoAlimento.DESCRIPCION -> alimento.descripcion
        else -> alimento.cantidad
    }

    private fun abrirCamara() {
        val (archivo, uri) = FotosAlimento.nuevoArchivoCamara(requireContext())
        archivoCamara = archivo
        try {
            tomarFoto.launch(uri)
        } catch (_: ActivityNotFoundException) {
            archivoCamara = null
            archivo.delete()
            Snackbar.make(binding.root, R.string.error_camara_no_disponible, Snackbar.LENGTH_LONG).show()
        }
    }

    /** Solo fechas posteriores a hoy. El margen de RN-11 lo valida el servidor. */
    private fun abrirCalendario() {
        if (childFragmentManager.findFragmentByTag(ETIQUETA_CALENDARIO) != null) return
        val fechaActual = editor.formulario.edicion.value?.alimento?.fechaVencimiento
        val hoy = MaterialDatePicker.todayInUtcMilliseconds()
        val manana = hoy + UN_DIA
        val restricciones = CalendarConstraints.Builder()
            .setStart(hoy)
            .setOpenAt(fechaActual ?: manana)
            .setValidator(DateValidatorPointForward.from(manana))
            .build()
        val calendario = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.alimento_fecha)
            .setCalendarConstraints(restricciones)
            .setSelection(fechaActual)
            .build()
        conectarCalendario(calendario)
        calendario.show(childFragmentManager, ETIQUETA_CALENDARIO)
    }

    private fun conectarCalendario(calendario: MaterialDatePicker<*>) {
        calendario.addOnPositiveButtonClickListener { seleccion ->
            (seleccion as? Long)?.let(editor.formulario::elegirFecha)
        }
    }

    protected fun ocultarTeclado() {
        WindowCompat.getInsetsController(requireActivity().window, binding.root)
            .hide(WindowInsetsCompat.Type.ime())
    }

    private fun mostrar(edicion: EdicionAlimento, guardado: EstadoGuardadoAlimento) = with(binding) {
        val contexto = requireContext()
        val alimento = edicion.alimento
        val editable = !guardado.guardando

        if (alimento.rutaFoto != fotoMostrada) {
            fotoMostrada = alimento.rutaFoto
            if (alimento.rutaFoto == null) imagenFoto.setImageDrawable(null) else imagenFoto.load(File(alimento.rutaFoto))
            imagenFoto.contentDescription = getString(
                if (alimento.rutaFoto == null) R.string.alimento_foto_vacia else R.string.alimento_foto_elegida
            )
        }
        indicadorFoto.isVisible = edicion.procesandoFoto
        botonCamara.isEnabled = !edicion.procesandoFoto && editable
        botonGaleria.isEnabled = !edicion.procesandoFoto && editable
        errorFoto.mostrarAviso(edicion.errores[CampoAlimento.FOTO]?.resolver(contexto))

        campos.forEach { (campo, par) ->
            par.first.error = edicion.errores[campo]?.resolver(contexto)
            par.second.isEnabled = editable
        }

        val textoUnidad = alimento.unidadMedida?.let { getString(FormatoAlimento.etiquetaUnidad(it)) }.orEmpty()
        if (entradaUnidad.text.toString() != textoUnidad) entradaUnidad.setText(textoUnidad, false)
        campoUnidad.error = edicion.errores[CampoAlimento.UNIDAD]?.resolver(contexto)
        campoUnidad.isEnabled = editable

        val textoCategoria = alimento.categoriaNombre.orEmpty()
        if (entradaCategoria.text.toString() != textoCategoria) entradaCategoria.setText(textoCategoria, false)
        campoCategoria.isEnabled = editable

        val textoFecha = alimento.fechaVencimiento?.let(FormatoAlimento::fecha).orEmpty()
        if (entradaFecha.text.toString() != textoFecha) entradaFecha.setText(textoFecha)
        campoFecha.error = edicion.errores[CampoAlimento.FECHA]?.resolver(contexto)
        entradaFecha.isEnabled = editable

        avisoError.mostrarAviso(guardado.error)
        botonGuardar.mostrar(getString(R.string.alimento_guardar), guardado.guardando, habilitado = !edicion.procesandoFoto)
        mostrarErrorCategoria()
    }

    private fun mostrarCategorias(estado: EstadoCategorias) {
        if (estado.lista != categoriasMostradas) {
            categoriasMostradas = estado.lista
            binding.entradaCategoria.setSimpleItems(estado.lista.map { it.nombre }.toTypedArray())
        }
        binding.botonReintentarCategorias.isVisible = estado.error != null && !estado.cargando
        mostrarErrorCategoria()
    }

    /** El error de carga de las categorías tiene prioridad sobre "Elige la categoría". */
    private fun mostrarErrorCategoria() {
        val errorCarga = editor.categorias.value.error
        val errorLocal = editor.formulario.edicion.value?.errores?.get(CampoAlimento.CATEGORIA)
        binding.campoCategoria.error = errorCarga ?: errorLocal?.resolver(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        categoriasMostradas = null
        fotoMostrada = null
    }

    private companion object {
        const val ESTADO_ARCHIVO_CAMARA = "archivo_camara"
        const val ETIQUETA_CALENDARIO = "calendario_vencimiento"
        const val UN_DIA = 24 * 60 * 60 * 1000L
    }
}
