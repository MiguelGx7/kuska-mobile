package com.proyecto.kuska.ui.detalledonacion

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.databinding.ItemDetalleAlimentoBinding
import com.proyecto.kuska.databinding.ItemDetalleEliminarBinding
import com.proyecto.kuska.databinding.ItemDetalleEncabezadoBinding
import com.proyecto.kuska.databinding.ItemDetalleGrupoBinding
import com.proyecto.kuska.databinding.ItemDetalleMensajeBinding
import com.proyecto.kuska.databinding.ItemDetalleSeccionBinding
import com.proyecto.kuska.databinding.ItemDetalleSolicitudBinding
import com.proyecto.kuska.ui.comun.FamiliaColor
import com.proyecto.kuska.ui.comun.FormatoCantidad
import com.proyecto.kuska.ui.comun.mostrarAviso
import com.proyecto.kuska.ui.comun.mostrarEstadoDonacion
import com.proyecto.kuska.ui.comun.mostrarEstadoSolicitud
import com.proyecto.kuska.ui.comun.mostrarEtiqueta
import com.proyecto.kuska.ui.comun.textoCantidad
import com.proyecto.kuska.util.FormatoFecha

/** Lista del detalle con un tipo de vista por cada [FilaDetalle]. */
class DetalleDonacionAdapter(
    private val alReintentarSolicitudes: () -> Unit,
    private val alEliminar: () -> Unit,
) : ListAdapter<FilaDetalle, RecyclerView.ViewHolder>(Diferencia) {

    override fun getItemViewType(posicion: Int) = when (getItem(posicion)) {
        is FilaDetalle.Encabezado -> TIPO_ENCABEZADO
        is FilaDetalle.Seccion -> TIPO_SECCION
        is FilaDetalle.AlimentoFila -> TIPO_ALIMENTO
        is FilaDetalle.GrupoSolicitudes -> TIPO_GRUPO
        is FilaDetalle.SolicitudFila -> TIPO_SOLICITUD
        is FilaDetalle.MensajeSolicitudes -> TIPO_MENSAJE
        is FilaDetalle.Eliminar -> TIPO_ELIMINAR
    }

    override fun onCreateViewHolder(padre: ViewGroup, tipo: Int): RecyclerView.ViewHolder {
        val inflador = LayoutInflater.from(padre.context)
        return when (tipo) {
            TIPO_ENCABEZADO -> Encabezado(ItemDetalleEncabezadoBinding.inflate(inflador, padre, false))
            TIPO_SECCION -> Seccion(ItemDetalleSeccionBinding.inflate(inflador, padre, false))
            TIPO_ALIMENTO -> AlimentoVista(ItemDetalleAlimentoBinding.inflate(inflador, padre, false))
            TIPO_GRUPO -> Grupo(ItemDetalleGrupoBinding.inflate(inflador, padre, false))
            TIPO_SOLICITUD -> Solicitud(ItemDetalleSolicitudBinding.inflate(inflador, padre, false))
            TIPO_MENSAJE -> Mensaje(ItemDetalleMensajeBinding.inflate(inflador, padre, false))
            else -> Eliminar(ItemDetalleEliminarBinding.inflate(inflador, padre, false))
        }
    }

    override fun onBindViewHolder(vista: RecyclerView.ViewHolder, posicion: Int) {
        when (val fila = getItem(posicion)) {
            is FilaDetalle.Encabezado -> (vista as Encabezado).mostrar(fila.donacion)
            is FilaDetalle.Seccion -> (vista as Seccion).binding.textoSeccion.setText(fila.titulo)
            is FilaDetalle.AlimentoFila -> (vista as AlimentoVista).mostrar(fila)
            is FilaDetalle.GrupoSolicitudes -> (vista as Grupo).binding.textoGrupo.text = fila.nombre
            is FilaDetalle.SolicitudFila -> (vista as Solicitud).mostrar(fila)
            is FilaDetalle.MensajeSolicitudes -> (vista as Mensaje).mostrar(fila)
            is FilaDetalle.Eliminar -> (vista as Eliminar).mostrar(fila)
        }
    }

    class Encabezado(private val binding: ItemDetalleEncabezadoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun mostrar(donacion: Donacion) = with(binding) {
            val contexto = root.context
            textoTitulo.text = donacion.titulo
            etiquetaEstado.mostrarEstadoDonacion(donacion.estado)
            textoDescripcion.mostrarAviso(donacion.descripcion?.takeIf { it.isNotBlank() })
            textoModalidad.text = contexto.getString(R.string.detalle_entrega, etiquetaModalidad(contexto, donacion.modalidadEntrega))
            textoDireccion.mostrarAviso(
                donacion.direccionRecogida?.takeIf { it.isNotBlank() }
                    ?.let { contexto.getString(R.string.detalle_recogida_en, it) }
            )
            textoCreada.mostrarAviso(
                FormatoFecha.fechaDeFechaHora(donacion.fechaRegistro)?.let { contexto.getString(R.string.detalle_creada, it) }
            )
            textoRevisada.mostrarAviso(
                FormatoFecha.fechaDeFechaHora(donacion.fechaRevision)?.let { contexto.getString(R.string.detalle_revisada, it) }
            )
            avisoRechazo.mostrarAviso(
                donacion.motivoRechazo?.takeIf { donacion.estado == Donacion.ESTADO_RECHAZADA && it.isNotBlank() }
                    ?.let { contexto.getString(R.string.detalle_motivo_rechazo, it) }
            )
        }

        private fun etiquetaModalidad(contexto: android.content.Context, modalidad: String) = when (modalidad) {
            Donacion.MODALIDAD_RECOGER -> contexto.getString(R.string.modalidad_recoger)
            Donacion.MODALIDAD_DOMICILIO -> contexto.getString(R.string.modalidad_domicilio)
            Donacion.MODALIDAD_AMBAS -> contexto.getString(R.string.modalidad_ambas)
            else -> modalidad
        }
    }

    class Seccion(val binding: ItemDetalleSeccionBinding) : RecyclerView.ViewHolder(binding.root)

    class Grupo(val binding: ItemDetalleGrupoBinding) : RecyclerView.ViewHolder(binding.root)

    class AlimentoVista(private val binding: ItemDetalleAlimentoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun mostrar(fila: FilaDetalle.AlimentoFila) = with(binding) {
            val contexto = root.context
            val alimento = fila.alimento

            // Sin foto (o si falla) queda el recuadro gris del fondo.
            if (alimento.fotoUrl == null) {
                imagenFoto.dispose()
                imagenFoto.setImageDrawable(null)
            } else {
                imagenFoto.load(alimento.fotoUrl) { crossfade(true) }
            }
            imagenFoto.contentDescription = contexto.getString(R.string.alimento_foto_descripcion, alimento.nombre)

            textoNombre.text = alimento.nombre
            etiquetaSinDisponibilidad.isVisible = !alimento.activo
            if (!alimento.activo) {
                etiquetaSinDisponibilidad.mostrarEtiqueta(
                    contexto.getString(R.string.detalle_sin_disponibilidad), FamiliaColor.GRIS,
                )
            }
            textoCategoria.mostrarAviso(alimento.categoriaNombre)
            textoDisponible.text = contexto.getString(
                R.string.detalle_disponible,
                FormatoCantidad.numero(alimento.cantidadDisponible),
                contexto.textoCantidad(alimento.cantidadTotal, alimento.unidadMedida),
            )
            textoVence.mostrarAviso(
                FormatoFecha.fecha(alimento.fechaVencimiento)?.let { contexto.getString(R.string.alimento_vence, it) }
            )
            textoMaximo.mostrarAviso(
                alimento.maxPorSolicitud?.let {
                    contexto.getString(R.string.detalle_maximo_solicitud, contexto.textoCantidad(it, alimento.unidadMedida))
                }
            )
        }
    }

    class Solicitud(private val binding: ItemDetalleSolicitudBinding) : RecyclerView.ViewHolder(binding.root) {
        fun mostrar(fila: FilaDetalle.SolicitudFila) = with(binding) {
            val contexto = root.context
            val solicitud = fila.solicitud
            textoBeneficiario.text = solicitud.beneficiario.nombre
            etiquetaEstado.mostrarEstadoSolicitud(solicitud.estado)
            etiquetaCancelado.isVisible = solicitud.cancelado
            if (solicitud.cancelado) {
                etiquetaCancelado.mostrarEtiqueta(contexto.getString(R.string.detalle_cancelado), FamiliaColor.GRIS)
            }
            textoCantidad.text = contexto.getString(
                R.string.detalle_cantidad_pedida, contexto.textoCantidad(solicitud.cantidad, fila.unidad),
            )
            textoPedido.text = contexto.getString(R.string.detalle_pedido, solicitud.codigoPedido)
            // RN-15: la API solo los manda si la solicitud está Aprobada, Entregada o No recogida.
            textoTelefono.mostrarAviso(
                solicitud.beneficiario.telefono?.takeIf { it.isNotBlank() }
                    ?.let { contexto.getString(R.string.detalle_telefono, it) }
            )
            textoDireccion.mostrarAviso(
                solicitud.beneficiario.direccion?.takeIf { it.isNotBlank() }
                    ?.let { contexto.getString(R.string.detalle_direccion, it) }
            )
        }
    }

    inner class Mensaje(private val binding: ItemDetalleMensajeBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.botonReintentar.setOnClickListener { alReintentarSolicitudes() }
        }

        fun mostrar(fila: FilaDetalle.MensajeSolicitudes) = with(binding) {
            indicador.isVisible = fila.cargando
            textoMensaje.mostrarAviso(fila.mensaje.takeIf { !fila.cargando })
            botonReintentar.isVisible = fila.conReintentar && !fila.cargando
        }
    }

    inner class Eliminar(private val binding: ItemDetalleEliminarBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.botonEliminar.setOnClickListener { alEliminar() }
        }

        fun mostrar(fila: FilaDetalle.Eliminar) = with(binding) {
            avisoError.mostrarAviso(fila.error)
            botonEliminar.isEnabled = !fila.eliminando
            botonEliminar.text = if (fila.eliminando) null else root.context.getString(R.string.detalle_eliminar)
            botonEliminar.contentDescription = if (fila.eliminando) root.context.getString(R.string.cargando) else null
            indicador.isVisible = fila.eliminando
        }
    }

    private object Diferencia : DiffUtil.ItemCallback<FilaDetalle>() {
        override fun areItemsTheSame(vieja: FilaDetalle, nueva: FilaDetalle) = vieja.clave == nueva.clave
        override fun areContentsTheSame(vieja: FilaDetalle, nueva: FilaDetalle) = vieja == nueva
    }

    private companion object {
        const val TIPO_ENCABEZADO = 0
        const val TIPO_SECCION = 1
        const val TIPO_ALIMENTO = 2
        const val TIPO_GRUPO = 3
        const val TIPO_SOLICITUD = 4
        const val TIPO_MENSAJE = 5
        const val TIPO_ELIMINAR = 6
    }
}
