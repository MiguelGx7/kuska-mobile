package com.proyecto.kuska.ui.comun.filtros

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import com.proyecto.kuska.R
import com.proyecto.kuska.databinding.FragmentHojaFiltrosBinding
import com.proyecto.kuska.databinding.ItemGrupoFiltroBinding
import kotlinx.coroutines.launch

/** Cada adaptación obtiene su EditorFiltros del ViewModel del Fragment padre. */
abstract class HojaFiltrosFragment : BottomSheetDialogFragment() {
    protected abstract val editor: EditorFiltros

    private var _binding: FragmentHojaFiltrosBinding? = null
    private val binding get() = _binding!!
    private var gruposMostrados: List<GrupoFiltro>? = null
    private val grupos = mutableMapOf<String, ItemGrupoFiltroBinding>()
    private val opciones = mutableMapOf<String, Map<Int, String?>>()

    override fun getTheme(): Int = R.style.ThemeOverlay_Kumora_HojaFiltros

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editor.filtrosHoja.abrir()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHojaFiltrosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setAccessibilityPaneTitle(view, getString(R.string.filtros_titulo))
        // El contenido puede desplazarse; las acciones quedan accesibles sobre la barra del sistema.
        val paddingInferior = binding.acciones.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.acciones) { acciones, insets ->
            val inferior = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            acciones.setPadding(acciones.paddingLeft, acciones.paddingTop, acciones.paddingRight, paddingInferior + inferior)
            insets
        }
        ViewCompat.requestApplyInsets(binding.acciones)
        binding.botonLimpiar.setOnClickListener { editor.filtrosHoja.limpiar() }
        binding.botonAplicar.setOnClickListener {
            editor.aplicarFiltrosHoja()
            dismiss()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                editor.filtrosHoja.estado.collect(::mostrar)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Limita la altura al espacio de la ventana: el scroll cede espacio a las acciones.
        val hoja = (dialog as? BottomSheetDialog) ?: return
        hoja.behavior.setShouldRemoveExpandedCorners(false)
        hoja.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let {
            it.layoutParams = it.layoutParams.apply { height = ViewGroup.LayoutParams.MATCH_PARENT }
        }
        hoja.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun mostrar(estado: EstadoFiltrosHoja) {
        if (gruposMostrados != estado.grupos) crearGrupos(estado.grupos)
        val seleccion = estado.borrador ?: estado.aplicados
        opciones.forEach { (clave, valores) ->
            val grupo = grupos.getValue(clave).opciones
            // Desconecta el listener: dibujar el estado nunca modifica el borrador.
            grupo.setOnCheckedStateChangeListener(null)
            val id = valores.entries.firstOrNull { it.value == seleccion[clave] }?.key
            if (id == null) grupo.clearCheck() else grupo.check(id)
            grupo.setOnCheckedStateChangeListener { _, ids ->
                val elegido = ids.firstOrNull()
                if (elegido != null && elegido in valores) editor.filtrosHoja.seleccionar(clave, valores[elegido])
            }
        }
    }

    private fun crearGrupos(configuracion: List<GrupoFiltro>) {
        gruposMostrados = configuracion
        binding.grupos.removeAllViews()
        grupos.clear()
        opciones.clear()
        configuracion.forEach { grupo ->
            val fila = ItemGrupoFiltroBinding.inflate(layoutInflater, binding.grupos, false)
            fila.titulo.text = grupo.titulo
            fila.indicador.isVisible = grupo.cargando
            fila.error.isVisible = grupo.error != null
            fila.error.text = grupo.error
            fila.botonReintentar.isVisible = grupo.error != null && !grupo.cargando
            fila.botonReintentar.setOnClickListener { editor.reintentarGrupoFiltro(grupo.clave) }
            val valores = mutableMapOf<Int, String?>()
            val etiquetas = listOf(null to getString(R.string.filtros_todas)) + grupo.opciones.map { it.valor to it.etiqueta }
            etiquetas.forEach { (valor, etiqueta) ->
                val chip = layoutInflater.inflate(R.layout.chip_filtro_hoja, fila.opciones, false) as Chip
                chip.id = View.generateViewId()
                chip.isSaveEnabled = false
                chip.text = etiqueta
                chip.isEnabled = valor == null || (!grupo.cargando && grupo.error == null)
                valores[chip.id] = valor
                fila.opciones.addView(chip)
            }
            grupos[grupo.clave] = fila
            opciones[grupo.clave] = valores
            binding.grupos.addView(fila.root)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        // La destrucción de la ventana al girar no equivale a cancelar la edición.
        if (activity?.isChangingConfigurations != true) editor.filtrosHoja.descartar()
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        gruposMostrados = null
        grupos.clear()
        opciones.clear()
        _binding = null
        super.onDestroyView()
    }
}
