package com.proyecto.kuska.ui.creardonacion

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
import androidx.navigation.navGraphViewModels
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
import com.proyecto.kuska.ui.comun.mostrarAviso
import com.proyecto.kuska.ui.comun.resolver
import kotlinx.coroutines.launch
import java.io.File

/** Agregar (indice = -1) o editar un alimento de la donación. Comparte el ViewModel del flujo. */
class AlimentoFragment : Fragment() {

    private var _binding: FragmentAlimentoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CrearDonacionViewModel by navGraphViewModels(R.id.nav_crear_donacion) {
        CrearDonacionViewModel.Factory
    }

    private lateinit var campos: Map<CampoAlimento, Pair<TextInputLayout, EditText>>

    /** Archivo donde la cámara está escribiendo la foto. Se guarda al rotar o si el sistema cierra la app. */
    private var archivoCamara: File? = null

    private var categoriasMostradas: List<CategoriaPublica>? = null
    private var fotoMostrada: String? = null

    private val tomarFoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { tomada ->
        val archivo = archivoCamara ?: return@registerForActivityResult
        archivoCamara = null
        if (tomada && archivo.length() > 0) {
            viewModel.procesarFoto(Uri.fromFile(archivo), archivoCamara = archivo)
        } else {
            archivo.delete()
        }
    }

    /** Photo Picker: no necesita permisos de almacenamiento. */
    private val elegirFoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.procesarFoto(it) }
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

        val indice = requireArguments().getInt(ARG_INDICE, -1)
        viewModel.iniciarEdicion(indice)
        binding.barra.setTitle(if (indice >= 0) R.string.titulo_editar_alimento else R.string.titulo_agregar_alimento)

        campos = with(binding) {
            mapOf(
                CampoAlimento.NOMBRE to (campoNombre to entradaNombre),
                CampoAlimento.DESCRIPCION to (campoDescripcion to entradaDescripcion),
                CampoAlimento.CANTIDAD to (campoCantidad to entradaCantidad),
            )
        }
        val actual = viewModel.edicion.value?.alimento
        campos.forEach { (campo, par) ->
            val valor = actual?.let { textoDe(it, campo) }.orEmpty()
            if (par.second.text.toString() != valor) par.second.setText(valor)
            par.second.doAfterTextChanged { viewModel.cambiarCampoAlimento(campo, it?.toString().orEmpty()) }
        }
        binding.entradaCantidad.alPulsarListo { ocultarTeclado() }

        binding.entradaUnidad.setSimpleItems(
            Donacion.UNIDADES.map { getString(FormatoAlimento.etiquetaUnidad(it)) }.toTypedArray()
        )
        binding.entradaUnidad.setOnItemClickListener { _, _, posicion, _ ->
            viewModel.elegirUnidad(Donacion.UNIDADES[posicion])
        }
        binding.entradaCategoria.setOnItemClickListener { _, _, posicion, _ ->
            categoriasMostradas?.getOrNull(posicion)?.let(viewModel::elegirCategoria)
        }
        binding.botonReintentarCategorias.setOnClickListener { viewModel.cargarCategorias() }

        binding.botonCamara.setOnClickListener { abrirCamara() }
        binding.botonGaleria.setOnClickListener {
            elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.entradaFecha.setOnClickListener { abrirCalendario() }
        // Si el calendario quedó abierto al rotar, se vuelve a conectar.
        (childFragmentManager.findFragmentByTag(ETIQUETA_CALENDARIO) as? MaterialDatePicker<*>)
            ?.let { conectarCalendario(it) }

        binding.botonGuardar.setOnClickListener {
            ocultarTeclado()
            viewModel.guardarAlimento()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { cancelar() }
        binding.barra.setNavigationOnClickListener { cancelar() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.edicion.collect { it?.let(::mostrar) } }
                launch { viewModel.estado.collect(::mostrarCategorias) }
                launch { viewModel.alimentoGuardado.collect { findNavController().popBackStack() } }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        archivoCamara?.let { outState.putString(ESTADO_ARCHIVO_CAMARA, it.path) }
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
        val hoy = MaterialDatePicker.todayInUtcMilliseconds()
        val manana = hoy + UN_DIA
        val restricciones = CalendarConstraints.Builder()
            .setStart(hoy)
            .setOpenAt(viewModel.edicion.value?.alimento?.fechaVencimiento ?: manana)
            .setValidator(DateValidatorPointForward.from(manana))
            .build()
        val calendario = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.alimento_fecha)
            .setCalendarConstraints(restricciones)
            .setSelection(viewModel.edicion.value?.alimento?.fechaVencimiento)
            .build()
        conectarCalendario(calendario)
        calendario.show(childFragmentManager, ETIQUETA_CALENDARIO)
    }

    private fun conectarCalendario(calendario: MaterialDatePicker<*>) {
        calendario.addOnPositiveButtonClickListener { seleccion ->
            (seleccion as? Long)?.let(viewModel::elegirFecha)
        }
    }

    private fun cancelar() {
        viewModel.cancelarEdicion()
        findNavController().popBackStack()
    }

    private fun ocultarTeclado() {
        WindowCompat.getInsetsController(requireActivity().window, binding.root)
            .hide(WindowInsetsCompat.Type.ime())
    }

    private fun mostrar(edicion: EdicionAlimento) = with(binding) {
        val contexto = requireContext()
        val alimento = edicion.alimento

        if (alimento.rutaFoto != fotoMostrada) {
            fotoMostrada = alimento.rutaFoto
            if (alimento.rutaFoto == null) imagenFoto.setImageDrawable(null) else imagenFoto.load(File(alimento.rutaFoto))
            imagenFoto.contentDescription = getString(
                if (alimento.rutaFoto == null) R.string.alimento_foto_vacia else R.string.alimento_foto_elegida
            )
        }
        indicadorFoto.isVisible = edicion.procesandoFoto
        botonCamara.isEnabled = !edicion.procesandoFoto
        botonGaleria.isEnabled = !edicion.procesandoFoto
        errorFoto.mostrarAviso(edicion.errores[CampoAlimento.FOTO]?.resolver(contexto))

        campos.forEach { (campo, par) ->
            par.first.error = edicion.errores[campo]?.resolver(contexto)
        }

        val textoUnidad = alimento.unidadMedida?.let { getString(FormatoAlimento.etiquetaUnidad(it)) }.orEmpty()
        if (entradaUnidad.text.toString() != textoUnidad) entradaUnidad.setText(textoUnidad, false)
        campoUnidad.error = edicion.errores[CampoAlimento.UNIDAD]?.resolver(contexto)

        val textoCategoria = alimento.categoriaNombre.orEmpty()
        if (entradaCategoria.text.toString() != textoCategoria) entradaCategoria.setText(textoCategoria, false)

        val textoFecha = alimento.fechaVencimiento?.let(FormatoAlimento::fecha).orEmpty()
        if (entradaFecha.text.toString() != textoFecha) entradaFecha.setText(textoFecha)
        campoFecha.error = edicion.errores[CampoAlimento.FECHA]?.resolver(contexto)

        botonGuardar.isEnabled = !edicion.procesandoFoto
        mostrarErrorCategoria()
    }

    private fun mostrarCategorias(estado: CrearDonacionEstado) {
        if (estado.categorias != categoriasMostradas) {
            categoriasMostradas = estado.categorias
            binding.entradaCategoria.setSimpleItems(estado.categorias.map { it.nombre }.toTypedArray())
        }
        binding.botonReintentarCategorias.isVisible = estado.errorCategorias != null && !estado.cargandoCategorias
        mostrarErrorCategoria()
    }

    /** El error de carga de las categorías tiene prioridad sobre "Elige la categoría". */
    private fun mostrarErrorCategoria() {
        val estado = viewModel.estado.value
        val errorLocal = viewModel.edicion.value?.errores?.get(CampoAlimento.CATEGORIA)
        binding.campoCategoria.error = estado.errorCategorias ?: errorLocal?.resolver(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        categoriasMostradas = null
        fotoMostrada = null
    }

    companion object {
        /** Argumento de navegación: posición del alimento a editar, o -1 para uno nuevo. */
        const val ARG_INDICE = "indice"

        private const val ESTADO_ARCHIVO_CAMARA = "archivo_camara"
        private const val ETIQUETA_CALENDARIO = "calendario_vencimiento"
        private const val UN_DIA = 24 * 60 * 60 * 1000L
    }
}
