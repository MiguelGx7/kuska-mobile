package com.proyecto.kuska.ui.iniciobeneficiario

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.AlimentoDisponible
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.databinding.ItemAlimentoDisponibleBinding
import com.proyecto.kuska.ui.comun.mostrarAviso
import com.proyecto.kuska.ui.comun.textoCantidad
import com.proyecto.kuska.util.FormatoFecha

/** Tarjetas de alimentos disponibles. [alElegir] recibe el alimento tocado. */
class AlimentosDisponiblesAdapter(
    private val alElegir: (AlimentoDisponible) -> Unit,
) : ListAdapter<AlimentoDisponible, AlimentosDisponiblesAdapter.Tarjeta>(Diferencia) {

    override fun onCreateViewHolder(padre: ViewGroup, tipo: Int) =
        Tarjeta(ItemAlimentoDisponibleBinding.inflate(LayoutInflater.from(padre.context), padre, false))

    override fun onBindViewHolder(tarjeta: Tarjeta, posicion: Int) = tarjeta.mostrar(getItem(posicion))

    inner class Tarjeta(private val binding: ItemAlimentoDisponibleBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val posicion = bindingAdapterPosition
                if (posicion != RecyclerView.NO_POSITION) alElegir(getItem(posicion))
            }
        }

        fun mostrar(alimento: AlimentoDisponible) = with(binding) {
            val contexto = root.context
            // Sin foto queda el recuadro gris (el fondo de la vista).
            if (alimento.fotoUrl == null) {
                miniatura.dispose()
                miniatura.setImageDrawable(null)
            } else {
                miniatura.load(alimento.fotoUrl) { crossfade(true) }
            }
            miniatura.contentDescription = contexto.getString(R.string.alimento_foto_descripcion, alimento.nombre)

            textoNombre.text = alimento.nombre
            textoCategoria.mostrarAviso(alimento.categoriaNombre?.takeIf { it.isNotBlank() })
            textoDisponible.text = contexto.textoDisponible(alimento)
            textoVence.mostrarAviso(FormatoFecha.fecha(alimento.fechaVencimiento)?.let { contexto.getString(R.string.alimento_vence, it) })
            textoModalidad.text = contexto.textoModalidad(alimento.modalidadEntrega)
            textoDonante.mostrarAviso(contexto.textoDonante(alimento))
        }
    }

    private object Diferencia : DiffUtil.ItemCallback<AlimentoDisponible>() {
        override fun areItemsTheSame(viejo: AlimentoDisponible, nuevo: AlimentoDisponible) = viejo.id == nuevo.id
        override fun areContentsTheSame(viejo: AlimentoDisponible, nuevo: AlimentoDisponible) = viejo == nuevo
    }
}

/** "Disponible 2,5 kg". */
fun Context.textoDisponible(alimento: AlimentoDisponible): String =
    getString(R.string.alimentos_disponible, textoCantidad(alimento.cantidadDisponible, alimento.unidadMedida))

/** "Entrega: Recoger". */
fun Context.textoModalidad(modalidad: String): String = getString(
    R.string.detalle_entrega,
    when (modalidad) {
        Donacion.MODALIDAD_RECOGER -> getString(R.string.modalidad_recoger)
        Donacion.MODALIDAD_DOMICILIO -> getString(R.string.modalidad_domicilio)
        Donacion.MODALIDAD_AMBAS -> getString(R.string.modalidad_ambas)
        else -> modalidad
    },
)

/** "Dona: Panadería Sol" (null si no viene el nombre). */
fun Context.textoDonante(alimento: AlimentoDisponible): String? =
    alimento.donanteNombre?.takeIf { it.isNotBlank() }?.let { getString(R.string.alimentos_dona, it) }
