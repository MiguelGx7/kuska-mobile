package com.proyecto.kuska.ui.notificaciones

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Notificacion
import com.proyecto.kuska.databinding.ItemNotificacionBinding
import com.proyecto.kuska.util.FechaRelativa
import com.proyecto.kuska.util.FormatoFecha

/** Tarjetas de Notificaciones. [alTocar] recibe la notificación tocada. */
class NotificacionesAdapter(
    private val alTocar: (Notificacion) -> Unit,
) : ListAdapter<Notificacion, NotificacionesAdapter.Tarjeta>(Diferencia) {

    override fun onCreateViewHolder(padre: ViewGroup, tipo: Int) =
        Tarjeta(ItemNotificacionBinding.inflate(LayoutInflater.from(padre.context), padre, false))

    override fun onBindViewHolder(tarjeta: Tarjeta, posicion: Int) = tarjeta.mostrar(getItem(posicion))

    inner class Tarjeta(private val binding: ItemNotificacionBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val posicion = bindingAdapterPosition
                if (posicion != RecyclerView.NO_POSITION) alTocar(getItem(posicion))
            }
        }

        fun mostrar(notificacion: Notificacion) = with(binding) {
            val contexto = root.context
            icono.setImageResource(ReglasNotificacion.icono(notificacion.tipo))
            textoTitulo.text = notificacion.titulo
            textoMensaje.text = notificacion.mensaje
            textoFecha.text = textoFecha(contexto, notificacion.fecha)
            textoFecha.isVisible = textoFecha.text.isNotEmpty()

            // No leída: fondo teñido del color principal, título en negrita y un punto.
            val noLeida = !notificacion.leida
            val superficie = MaterialColors.getColor(tarjeta, com.google.android.material.R.attr.colorSurface)
            val principal = MaterialColors.getColor(tarjeta, androidx.appcompat.R.attr.colorPrimary)
            tarjeta.setCardBackgroundColor(
                if (noLeida) ColorUtils.compositeColors(ColorUtils.setAlphaComponent(principal, ALFA_TINTE), superficie)
                else superficie
            )
            textoTitulo.setTypeface(null, if (noLeida) Typeface.BOLD else Typeface.NORMAL)
            puntoNoLeida.isVisible = noLeida
            root.contentDescription = if (noLeida) {
                contexto.getString(R.string.notificaciones_no_leida, notificacion.titulo)
            } else {
                null
            }
        }

        private fun textoFecha(contexto: Context, fecha: String?): String =
            when (val relativa = FormatoFecha.fechaRelativa(fecha, System.currentTimeMillis())) {
                FechaRelativa.Ahora -> contexto.getString(R.string.notificaciones_ahora)
                is FechaRelativa.Minutos -> contexto.getString(R.string.notificaciones_hace_minutos, relativa.cantidad)
                is FechaRelativa.Horas -> contexto.getString(R.string.notificaciones_hace_horas, relativa.cantidad)
                is FechaRelativa.Fecha -> relativa.texto
                null -> ""
            }
    }

    private object Diferencia : DiffUtil.ItemCallback<Notificacion>() {
        override fun areItemsTheSame(vieja: Notificacion, nueva: Notificacion) = vieja.id == nueva.id
        override fun areContentsTheSame(vieja: Notificacion, nueva: Notificacion) = vieja == nueva
    }

    private companion object {
        /** ~8 % del color principal sobre la superficie. */
        const val ALFA_TINTE = 0x14
    }
}
