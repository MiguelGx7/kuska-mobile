package com.proyecto.kuska.ui.misdonaciones

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.databinding.ItemDonacionBinding
import com.proyecto.kuska.ui.comun.mostrarEstadoDonacion
import com.proyecto.kuska.util.FormatoFecha

/** Tarjetas de Mis donaciones. [alElegir] recibe la donación tocada. */
class DonacionesAdapter(
    private val alElegir: (Donacion) -> Unit,
) : ListAdapter<Donacion, DonacionesAdapter.Tarjeta>(Diferencia) {

    override fun onCreateViewHolder(padre: ViewGroup, tipo: Int) =
        Tarjeta(ItemDonacionBinding.inflate(LayoutInflater.from(padre.context), padre, false))

    override fun onBindViewHolder(tarjeta: Tarjeta, posicion: Int) = tarjeta.mostrar(getItem(posicion))

    inner class Tarjeta(private val binding: ItemDonacionBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val posicion = bindingAdapterPosition
                if (posicion != RecyclerView.NO_POSITION) alElegir(getItem(posicion))
            }
        }

        fun mostrar(donacion: Donacion) = with(binding) {
            val contexto = root.context
            val alimentos = donacion.alimentos.orEmpty()

            // Sin foto queda el recuadro gris (el fondo de la vista).
            val foto = alimentos.firstOrNull()?.fotoUrl
            if (foto == null) {
                miniatura.dispose()
                miniatura.setImageDrawable(null)
            } else {
                miniatura.load(foto) { crossfade(true) }
            }
            miniatura.contentDescription = contexto.getString(R.string.mis_donaciones_miniatura, donacion.titulo)

            textoTitulo.text = donacion.titulo
            textoAlimentos.text = contexto.resources.getQuantityString(
                R.plurals.mis_donaciones_alimentos, alimentos.size, alimentos.size,
            )
            textoFecha.text = FormatoFecha.fechaDeFechaHora(donacion.fechaRegistro)
                ?.let { contexto.getString(R.string.mis_donaciones_creada, it) }
            etiquetaEstado.mostrarEstadoDonacion(donacion.estado)
        }
    }

    private object Diferencia : DiffUtil.ItemCallback<Donacion>() {
        override fun areItemsTheSame(vieja: Donacion, nueva: Donacion) = vieja.id == nueva.id
        override fun areContentsTheSame(vieja: Donacion, nueva: Donacion) = vieja == nueva
    }
}
