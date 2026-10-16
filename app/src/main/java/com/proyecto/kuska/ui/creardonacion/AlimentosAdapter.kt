package com.proyecto.kuska.ui.creardonacion

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.proyecto.kuska.R
import com.proyecto.kuska.databinding.ItemAlimentoDonacionBinding
import com.proyecto.kuska.ui.comun.mostrarAviso
import java.io.File

/** Tarjetas de los alimentos agregados. Los callbacks reciben la posición en la lista. */
class AlimentosAdapter(
    private val alEditar: (Int) -> Unit,
    private val alQuitar: (Int) -> Unit,
) : ListAdapter<AlimentoEnLista, AlimentosAdapter.Tarjeta>(Diferencia) {

    override fun onCreateViewHolder(padre: ViewGroup, tipo: Int): Tarjeta {
        val binding = ItemAlimentoDonacionBinding.inflate(LayoutInflater.from(padre.context), padre, false)
        return Tarjeta(binding)
    }

    override fun onBindViewHolder(tarjeta: Tarjeta, posicion: Int) = tarjeta.mostrar(getItem(posicion))

    inner class Tarjeta(private val binding: ItemAlimentoDonacionBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.botonEditar.setOnClickListener { posicionValida()?.let(alEditar) }
            binding.botonQuitar.setOnClickListener { posicionValida()?.let(alQuitar) }
        }

        private fun posicionValida() = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }

        fun mostrar(item: AlimentoEnLista) = with(binding) {
            val contexto = root.context
            val alimento = item.alimento
            miniatura.load(alimento.rutaFoto?.let(::File))
            miniatura.contentDescription = contexto.getString(R.string.alimento_foto_descripcion, alimento.nombre.trim())
            textoNombre.text = alimento.nombre.trim()
            textoCantidad.text = FormatoAlimento.cantidad(contexto, alimento.cantidad, alimento.unidadMedida.orEmpty())
            textoVence.text = alimento.fechaVencimiento
                ?.let { contexto.getString(R.string.alimento_vence, FormatoAlimento.fecha(it)) }
            textoCategoria.text = alimento.categoriaNombre
            avisoError.mostrarAviso(item.errorServidor)
        }
    }

    private object Diferencia : DiffUtil.ItemCallback<AlimentoEnLista>() {
        // Los alimentos no tienen id antes de enviarse: la foto es única para cada uno.
        override fun areItemsTheSame(viejo: AlimentoEnLista, nuevo: AlimentoEnLista) =
            viejo.alimento.rutaFoto == nuevo.alimento.rutaFoto

        override fun areContentsTheSame(viejo: AlimentoEnLista, nuevo: AlimentoEnLista) = viejo == nuevo
    }
}
