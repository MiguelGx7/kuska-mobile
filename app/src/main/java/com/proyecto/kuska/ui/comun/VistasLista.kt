package com.proyecto.kuska.ui.comun

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.proyecto.kuska.R
import com.proyecto.kuska.databinding.ItemPieListaBinding
import com.proyecto.kuska.databinding.VistaEstadoPantallaBinding

/**
 * Estado de pantalla completa (vista_estado_pantalla): indicador si [cargando]; si no, [mensaje] (vacío o
 * error) con "Reintentar" cuando [conReintentar]. Sin mensaje ni carga, la vista no se ve.
 * [ilustracion]: la hormiga arriba del texto (null = solo texto, p. ej. un vacío por filtro). Con poca altura de
 * pantalla no se muestra.
 */
fun VistaEstadoPantallaBinding.mostrar(
    cargando: Boolean,
    mensaje: String?,
    conReintentar: Boolean,
    ilustracion: IlustracionEstado? = null,
) {
    root.isVisible = cargando || mensaje != null
    indicador.isVisible = cargando
    contenedorMensaje.isVisible = !cargando && mensaje != null
    textoMensaje.text = mensaje
    botonReintentar.isVisible = conReintentar

    val recursos = root.resources
    val dibujo = ilustracion?.takeIf { mensaje != null && recursos.getBoolean(R.bool.estado_con_ilustracion) }
    this.ilustracion.isVisible = dibujo != null
    if (dibujo != null) {
        this.ilustracion.setImageResource(dibujo.dibujo)
        this.ilustracion.layoutParams = this.ilustracion.layoutParams.apply {
            width = recursos.getDimensionPixelSize(dibujo.ancho)
            height = recursos.getDimensionPixelSize(dibujo.alto)
        }
    }
}

/** Lo que se muestra al final de una lista paginada. */
sealed interface PieLista {
    data object Cargando : PieLista
    data class Error(val mensaje: String) : PieLista
}

/** Adaptador de 0 o 1 fila para el final de la lista (va en un ConcatAdapter después del de los datos). */
class PieListaAdapter(private val alReintentar: () -> Unit) : RecyclerView.Adapter<PieListaAdapter.Fila>() {

    var pie: PieLista? = null
        set(valor) {
            if (field == valor) return
            val antes = field
            field = valor
            when {
                antes == null -> notifyItemInserted(0)
                valor == null -> notifyItemRemoved(0)
                else -> notifyItemChanged(0)
            }
        }

    override fun getItemCount() = if (pie == null) 0 else 1

    override fun onCreateViewHolder(padre: ViewGroup, tipo: Int) =
        Fila(ItemPieListaBinding.inflate(LayoutInflater.from(padre.context), padre, false))

    override fun onBindViewHolder(fila: Fila, posicion: Int) = fila.mostrar(pie)

    inner class Fila(private val binding: ItemPieListaBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.botonReintentar.setOnClickListener { alReintentar() }
        }

        fun mostrar(pie: PieLista?) = with(binding) {
            indicador.isVisible = pie is PieLista.Cargando
            textoError.isVisible = pie is PieLista.Error
            botonReintentar.isVisible = pie is PieLista.Error
            textoError.text = (pie as? PieLista.Error)?.mensaje
        }
    }
}
