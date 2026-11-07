package com.proyecto.kuska.ui.detallesolicitud

import android.content.Context
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import com.google.android.material.color.MaterialColors
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.MiSolicitud
import com.proyecto.kuska.databinding.ItemDetalleSeccionBinding
import com.proyecto.kuska.databinding.ItemSolicitudAlimentoBinding
import com.proyecto.kuska.databinding.ItemSolicitudCancelarBinding
import com.proyecto.kuska.databinding.ItemSolicitudDonanteBinding
import com.proyecto.kuska.databinding.ItemSolicitudEncabezadoBinding
import com.proyecto.kuska.databinding.ItemSolicitudEntregaBinding
import com.proyecto.kuska.ui.comun.FamiliaColor
import com.proyecto.kuska.ui.comun.mostrar
import com.proyecto.kuska.ui.comun.mostrarAviso
import com.proyecto.kuska.ui.comun.mostrarEstadoSolicitud
import com.proyecto.kuska.ui.comun.mostrarEtiqueta
import com.proyecto.kuska.ui.comun.resolver
import com.proyecto.kuska.ui.comun.textoCantidad
import com.proyecto.kuska.util.FormatoFecha

/** Lista del detalle de una solicitud, con un tipo de vista por cada [FilaSolicitud]. */
class DetalleSolicitudAdapter(
    /** Recibe el número ya limpio para "tel:". */
    private val alLlamar: (String) -> Unit,
    private val alCancelar: () -> Unit,
    /** "Sí, lo recibí" (HU-057). */
    private val alConfirmarRecibido: () -> Unit,
    /** "No me llegó" (HU-058). */
    private val alReportarNoRecibido: () -> Unit,
) : ListAdapter<FilaSolicitud, RecyclerView.ViewHolder>(Diferencia) {

    override fun getItemViewType(posicion: Int) = when (getItem(posicion)) {
        is FilaSolicitud.Encabezado -> TIPO_ENCABEZADO
        is FilaSolicitud.Donante -> TIPO_DONANTE
        is FilaSolicitud.Seccion -> TIPO_SECCION
        is FilaSolicitud.AlimentoFila -> TIPO_ALIMENTO
        is FilaSolicitud.Entrega -> TIPO_ENTREGA
        is FilaSolicitud.Cancelar -> TIPO_CANCELAR
    }

    override fun onCreateViewHolder(padre: ViewGroup, tipo: Int): RecyclerView.ViewHolder {
        val inflador = LayoutInflater.from(padre.context)
        return when (tipo) {
            TIPO_ENCABEZADO -> Encabezado(ItemSolicitudEncabezadoBinding.inflate(inflador, padre, false))
            TIPO_DONANTE -> Donante(ItemSolicitudDonanteBinding.inflate(inflador, padre, false))
            TIPO_SECCION -> Seccion(ItemDetalleSeccionBinding.inflate(inflador, padre, false))
            TIPO_ALIMENTO -> AlimentoVista(ItemSolicitudAlimentoBinding.inflate(inflador, padre, false))
            TIPO_ENTREGA -> EntregaVista(ItemSolicitudEntregaBinding.inflate(inflador, padre, false))
            else -> Cancelar(ItemSolicitudCancelarBinding.inflate(inflador, padre, false))
        }
    }

    override fun onBindViewHolder(vista: RecyclerView.ViewHolder, posicion: Int) {
        when (val fila = getItem(posicion)) {
            is FilaSolicitud.Encabezado -> (vista as Encabezado).mostrar(fila)
            is FilaSolicitud.Donante -> (vista as Donante).mostrar(fila)
            is FilaSolicitud.Seccion -> (vista as Seccion).binding.textoSeccion.setText(fila.titulo)
            is FilaSolicitud.AlimentoFila -> (vista as AlimentoVista).mostrar(fila)
            is FilaSolicitud.Entrega -> (vista as EntregaVista).mostrar(fila)
            is FilaSolicitud.Cancelar -> (vista as Cancelar).mostrar(fila)
        }
    }

    class Encabezado(private val binding: ItemSolicitudEncabezadoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun mostrar(fila: FilaSolicitud.Encabezado) = with(binding) {
            val contexto = root.context
            val solicitud = fila.solicitud
            textoCodigo.text = contexto.getString(R.string.mis_solicitudes_pedido, solicitud.codigoPedido)
            etiquetaEstado.mostrarEstadoSolicitud(solicitud.estado)
            textoDonacion.text = contexto.getString(R.string.solicitud_donacion, solicitud.donacionTitulo)
            textoModalidad.text = contexto.getString(R.string.detalle_entrega, etiquetaModalidad(contexto, solicitud.modalidadEntrega))
            textoDireccion.mostrarAviso(fila.direccionEntrega?.let { contexto.getString(R.string.solicitud_entregar_en, it) })
            textoSolicitada.mostrarAviso(
                FormatoFecha.fechaDeFechaHora(solicitud.fechaSolicitud)?.let { contexto.getString(R.string.mis_solicitudes_fecha, it) }
            )
            textoGestionada.mostrarAviso(
                FormatoFecha.fechaDeFechaHora(solicitud.fechaGestion)?.let { contexto.getString(R.string.solicitud_gestionada, it) }
            )
            avisoMotivo.mostrarAviso(
                when (val motivo = fila.motivo) {
                    is MotivoSolicitud.Rechazo -> contexto.getString(R.string.detalle_motivo_rechazo, motivo.texto)
                    is MotivoSolicitud.Cancelacion -> contexto.getString(R.string.solicitud_motivo_cancelacion, motivo.texto)
                    null -> null
                }
            )
            avisoAccion.mostrarAviso(fila.aviso?.resolver(contexto))
        }

        private fun etiquetaModalidad(contexto: Context, modalidad: String) = when (modalidad) {
            MiSolicitud.MODALIDAD_RECOGER -> contexto.getString(R.string.modalidad_recoger)
            MiSolicitud.MODALIDAD_DOMICILIO -> contexto.getString(R.string.modalidad_domicilio)
            else -> modalidad
        }
    }

    inner class Donante(private val binding: ItemSolicitudDonanteBinding) : RecyclerView.ViewHolder(binding.root) {
        private var numero: String? = null

        init {
            binding.textoTelefono.setOnClickListener { numero?.let(alLlamar) }
        }

        fun mostrar(fila: FilaSolicitud.Donante) = with(binding) {
            val contexto = root.context
            val datos = fila.datos
            textoTitulo.setText(if (datos.destacado) R.string.solicitud_datos_recogida else R.string.solicitud_donante)
            // Aprobada: borde del color principal para que se note que hay que ir a recoger.
            tarjeta.strokeColor = if (datos.destacado) {
                MaterialColors.getColor(tarjeta, androidx.appcompat.R.attr.colorPrimary)
            } else {
                MaterialColors.getColor(tarjeta, com.google.android.material.R.attr.colorOutlineVariant)
            }
            val dp = if (datos.destacado) BORDE_DESTACADO_DP else BORDE_NORMAL_DP
            tarjeta.strokeWidth = (dp * contexto.resources.displayMetrics.density).toInt()

            textoNombre.mostrarAviso(datos.nombre.takeIf { it.isNotEmpty() })
            val limite = FormatoFecha.fechaHoraDeFechaHora(datos.fechaLimite)
            textoFechaLimite.mostrarAviso(
                limite?.let {
                    contexto.getString(if (datos.destacado) R.string.solicitud_recoger_antes else R.string.solicitud_fecha_limite, it)
                }
            )
            numero = ReglasSolicitud.numeroMarcable(datos.telefono)
            textoTelefono.mostrarAviso(datos.telefono?.let { contexto.getString(R.string.detalle_telefono, it) })
            textoTelefono.isClickable = numero != null
            textoTelefono.contentDescription = datos.telefono?.let { contexto.getString(R.string.solicitud_llamar, it) }
            textoDireccion.mostrarAviso(datos.direccion?.let { contexto.getString(R.string.solicitud_direccion_recogida, it) })
            textoEspera.isVisible = datos.esperaAprobacion
        }
    }

    class Seccion(val binding: ItemDetalleSeccionBinding) : RecyclerView.ViewHolder(binding.root)

    class AlimentoVista(private val binding: ItemSolicitudAlimentoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun mostrar(fila: FilaSolicitud.AlimentoFila) = with(binding) {
            val contexto = root.context
            val detalle = fila.detalle
            if (detalle.fotoUrl == null) {
                miniatura.dispose()
                miniatura.setImageDrawable(null)
            } else {
                miniatura.load(detalle.fotoUrl) { crossfade(true) }
            }
            miniatura.contentDescription = contexto.getString(R.string.alimento_foto_descripcion, detalle.nombre)
            miniatura.alpha = if (fila.tachado) ALFA_CANCELADO else 1f

            textoNombre.text = detalle.nombre
            textoNombre.paintFlags = if (fila.tachado) {
                textoNombre.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                textoNombre.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            textoCantidad.text = contexto.getString(
                R.string.carrito_item_cantidad, contexto.textoCantidad(detalle.cantidad, detalle.unidadMedida),
            )
            etiquetaCancelado.isVisible = fila.tachado
            if (fila.tachado) etiquetaCancelado.mostrarEtiqueta(contexto.getString(R.string.detalle_cancelado), FamiliaColor.GRIS)
        }
    }

    inner class EntregaVista(private val binding: ItemSolicitudEntregaBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.botonRecibido.boton.setOnClickListener { alConfirmarRecibido() }
            binding.botonNoLlego.setOnClickListener { alReportarNoRecibido() }
        }

        fun mostrar(fila: FilaSolicitud.Entrega) = with(binding) {
            val contexto = root.context
            val entrega = fila.entrega
            textoFechaEntrega.mostrarAviso(
                FormatoFecha.fechaHoraDeFechaHora(entrega.fechaEntrega)?.let { contexto.getString(R.string.solicitud_entregada_el, it) }
            )
            textoObservacion.mostrarAviso(
                entrega.observacion?.takeIf { it.isNotBlank() }?.let { contexto.getString(R.string.solicitud_observacion, it) }
            )

            // Por responder: pregunta, plazo y botones. Si no, la confirmación como etiqueta de color (HU-059).
            val estadoConfirmacion = entrega.estadoConfirmacion?.takeIf { it.isNotBlank() }
            filaConfirmacion.isVisible = !fila.puedeResponder && estadoConfirmacion != null
            if (estadoConfirmacion != null) {
                etiquetaConfirmacion.mostrarEtiqueta(
                    ConfirmacionEntregaSolicitud.etiqueta(estadoConfirmacion)?.let(contexto::getString) ?: estadoConfirmacion,
                    ConfirmacionEntregaSolicitud.familia(estadoConfirmacion),
                )
            }
            textoPregunta.isVisible = fila.puedeResponder
            botonesRespuesta.isVisible = fila.puedeResponder
            textoPlazo.mostrarAviso(
                when {
                    fila.puedeResponder -> FormatoFecha.fechaHoraDeFechaHora(entrega.confirmarHasta)
                        ?.let { contexto.getString(R.string.solicitud_confirmar_hasta, it) }
                    fila.plazoVencido -> contexto.getString(R.string.solicitud_plazo_terminado)
                    else -> null
                }
            )
            botonRecibido.mostrar(
                contexto.getString(R.string.solicitud_si_recibi),
                cargando = fila.enviando == RespuestaEntrega.RECIBIDO,
                habilitado = fila.habilitado,
            )
            val reclamando = fila.enviando == RespuestaEntrega.RECLAMO
            botonNoLlego.isEnabled = fila.habilitado
            botonNoLlego.text = if (reclamando) null else contexto.getString(R.string.solicitud_no_llego)
            botonNoLlego.contentDescription = if (reclamando) contexto.getString(R.string.cargando) else null
            indicadorNoLlego.isVisible = reclamando

            textoComentario.mostrarAviso(
                entrega.comentarioBeneficiario?.takeIf { it.isNotBlank() }
                    ?.let { contexto.getString(R.string.solicitud_tu_comentario, it) }
            )
            textoRespuesta.mostrarAviso(
                entrega.respuestaAdmin?.takeIf { it.isNotBlank() }
                    ?.let { contexto.getString(R.string.solicitud_respuesta_admin, it) }
            )
        }
    }

    inner class Cancelar(private val binding: ItemSolicitudCancelarBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.botonCancelar.setOnClickListener { alCancelar() }
        }

        fun mostrar(fila: FilaSolicitud.Cancelar) = with(binding) {
            val contexto = root.context
            botonCancelar.isEnabled = fila.habilitado
            botonCancelar.text = if (fila.cancelando) null else contexto.getString(R.string.solicitud_cancelar)
            botonCancelar.contentDescription = if (fila.cancelando) contexto.getString(R.string.cargando) else null
            indicador.isVisible = fila.cancelando
        }
    }

    private object Diferencia : DiffUtil.ItemCallback<FilaSolicitud>() {
        override fun areItemsTheSame(vieja: FilaSolicitud, nueva: FilaSolicitud) = vieja.clave == nueva.clave
        override fun areContentsTheSame(vieja: FilaSolicitud, nueva: FilaSolicitud) = vieja == nueva
    }

    private companion object {
        const val TIPO_ENCABEZADO = 0
        const val TIPO_DONANTE = 1
        const val TIPO_SECCION = 2
        const val TIPO_ALIMENTO = 3
        const val TIPO_ENTREGA = 4
        const val TIPO_CANCELAR = 5

        const val ALFA_CANCELADO = 0.5f
        const val BORDE_NORMAL_DP = 1
        const val BORDE_DESTACADO_DP = 2
    }
}
