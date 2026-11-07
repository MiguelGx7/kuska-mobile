package com.proyecto.kuska.ui.missolicitudes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.MiSolicitud
import com.proyecto.kuska.databinding.ItemSolicitudBinding
import com.proyecto.kuska.ui.comun.mostrarAviso
import com.proyecto.kuska.ui.comun.mostrarEstadoSolicitud
import com.proyecto.kuska.ui.detallesolicitud.ReglasSolicitud
import com.proyecto.kuska.util.FormatoFecha

/** Tarjetas de Mis solicitudes. [alElegir] recibe la solicitud tocada. */
class SolicitudesAdapter(
    private val alElegir: (MiSolicitud) -> Unit,
) : ListAdapter<MiSolicitud, SolicitudesAdapter.Tarjeta>(Diferencia) {

    override fun onCreateViewHolder(padre: ViewGroup, tipo: Int) =
        Tarjeta(ItemSolicitudBinding.inflate(LayoutInflater.from(padre.context), padre, false))

    override fun onBindViewHolder(tarjeta: Tarjeta, posicion: Int) = tarjeta.mostrar(getItem(posicion))

    inner class Tarjeta(private val binding: ItemSolicitudBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val posicion = bindingAdapterPosition
                if (posicion != RecyclerView.NO_POSITION) alElegir(getItem(posicion))
            }
        }

        fun mostrar(solicitud: MiSolicitud) = with(binding) {
            val contexto = root.context
            textoCodigo.text = contexto.getString(R.string.mis_solicitudes_pedido, solicitud.codigoPedido)
            textoTitulo.text = solicitud.donacionTitulo
            // Los alimentos cancelados no cuentan.
            val cuantos = ReglasSolicitud.alimentosVigentes(solicitud)
            textoAlimentos.text = contexto.resources.getQuantityString(R.plurals.mis_donaciones_alimentos, cuantos, cuantos)
            textoFecha.mostrarAviso(
                FormatoFecha.fechaDeFechaHora(solicitud.fechaSolicitud)?.let { contexto.getString(R.string.mis_solicitudes_fecha, it) }
            )
            etiquetaEstado.mostrarEstadoSolicitud(solicitud.estado)
        }
    }

    private object Diferencia : DiffUtil.ItemCallback<MiSolicitud>() {
        override fun areItemsTheSame(vieja: MiSolicitud, nueva: MiSolicitud) = vieja.id == nueva.id
        override fun areContentsTheSame(vieja: MiSolicitud, nueva: MiSolicitud) = vieja == nueva
    }
}
