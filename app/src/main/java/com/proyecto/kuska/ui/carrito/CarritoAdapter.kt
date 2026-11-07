package com.proyecto.kuska.ui.carrito

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.CarritoItem
import com.proyecto.kuska.data.api.modelos.Donacion
import com.proyecto.kuska.databinding.ItemCarritoAvisosBinding
import com.proyecto.kuska.databinding.ItemCarritoGrupoBinding
import com.proyecto.kuska.databinding.ItemCarritoItemBinding
import com.proyecto.kuska.ui.comun.FamiliaColor
import com.proyecto.kuska.ui.comun.mostrarAviso
import com.proyecto.kuska.ui.comun.mostrarEtiqueta
import com.proyecto.kuska.ui.comun.resolver
import com.proyecto.kuska.ui.comun.textoCantidad
import com.proyecto.kuska.ui.iniciobeneficiario.textoModalidad

/**
 * Lista del carrito: avisos, encabezado de cada donación (con la elección de modalidad en las "Ambas") y sus ítems.
 * El RecyclerView debe tener supportsChangeAnimations = false para que el campo de dirección no pierda el foco
 * mientras se escribe.
 */
class CarritoAdapter(
    private val alElegirModalidad: (donacion: Int, modalidad: String) -> Unit,
    private val alCambiarDireccion: (donacion: Int, texto: String) -> Unit,
    private val alCambiarCantidad: (CarritoItem) -> Unit,
    private val alQuitar: (CarritoItem) -> Unit,
) : ListAdapter<FilaCarrito, RecyclerView.ViewHolder>(Diferencia) {

    override fun getItemViewType(posicion: Int) = when (getItem(posicion)) {
        is FilaCarrito.Avisos -> TIPO_AVISOS
        is FilaCarrito.Grupo -> TIPO_GRUPO
        is FilaCarrito.Item -> TIPO_ITEM
    }

    override fun onCreateViewHolder(padre: ViewGroup, tipo: Int): RecyclerView.ViewHolder {
        val inflador = LayoutInflater.from(padre.context)
        return when (tipo) {
            TIPO_AVISOS -> AvisosFila(ItemCarritoAvisosBinding.inflate(inflador, padre, false))
            TIPO_GRUPO -> GrupoFila(ItemCarritoGrupoBinding.inflate(inflador, padre, false))
            else -> ItemFila(ItemCarritoItemBinding.inflate(inflador, padre, false))
        }
    }

    override fun onBindViewHolder(fila: RecyclerView.ViewHolder, posicion: Int) {
        when (val dato = getItem(posicion)) {
            is FilaCarrito.Avisos -> (fila as AvisosFila).mostrar(dato)
            is FilaCarrito.Grupo -> (fila as GrupoFila).mostrar(dato)
            is FilaCarrito.Item -> (fila as ItemFila).mostrar(dato)
        }
    }

    class AvisosFila(private val binding: ItemCarritoAvisosBinding) : RecyclerView.ViewHolder(binding.root) {
        fun mostrar(fila: FilaCarrito.Avisos) {
            binding.textoAvisos.text = fila.mensajes.joinToString("\n\n")
        }
    }

    inner class GrupoFila(private val binding: ItemCarritoGrupoBinding) : RecyclerView.ViewHolder(binding.root) {

        private var donacion: Int? = null

        /** Mientras se dibuja, los cambios de la vista no se mandan al ViewModel. */
        private var dibujando = false

        init {
            binding.grupoModalidad.addOnButtonCheckedListener { _, id, marcado ->
                val donacion = donacion ?: return@addOnButtonCheckedListener
                if (dibujando || !marcado) return@addOnButtonCheckedListener
                val modalidad = if (id == R.id.boton_recoger) Donacion.MODALIDAD_RECOGER else Donacion.MODALIDAD_DOMICILIO
                alElegirModalidad(donacion, modalidad)
            }
            binding.entradaDireccion.doAfterTextChanged { texto ->
                val donacion = donacion ?: return@doAfterTextChanged
                if (!dibujando) alCambiarDireccion(donacion, texto?.toString().orEmpty())
            }
        }

        fun mostrar(fila: FilaCarrito.Grupo) = with(binding) {
            dibujando = true
            donacion = fila.grupo.donacion
            val contexto = root.context
            textoTitulo.text = fila.grupo.titulo.orEmpty()
            textoDonante.mostrarAviso(
                fila.grupo.donanteNombre?.takeIf { it.isNotBlank() }?.let { contexto.getString(R.string.alimentos_dona, it) }
            )
            textoModalidad.text = contexto.textoModalidad(fila.grupo.modalidadEntrega)

            val eleccion = fila.eleccion
            contenedorEleccion.isVisible = eleccion != null
            if (eleccion != null) {
                when (eleccion.modalidad) {
                    Donacion.MODALIDAD_RECOGER -> grupoModalidad.check(R.id.boton_recoger)
                    Donacion.MODALIDAD_DOMICILIO -> grupoModalidad.check(R.id.boton_domicilio)
                    else -> grupoModalidad.clearChecked()
                }
                botonRecoger.isEnabled = fila.habilitado
                botonDomicilio.isEnabled = fila.habilitado
                val domicilio = eleccion.modalidad == Donacion.MODALIDAD_DOMICILIO
                contenedorDireccion.isVisible = domicilio
                if (entradaDireccion.text.toString() != eleccion.direccion) {
                    entradaDireccion.setText(eleccion.direccion)
                    entradaDireccion.setSelection(entradaDireccion.length())
                }
                entradaDireccion.isEnabled = fila.habilitado
            }
            textoError.mostrarAviso(fila.error?.resolver(contexto))
            dibujando = false
        }
    }

    inner class ItemFila(private val binding: ItemCarritoItemBinding) : RecyclerView.ViewHolder(binding.root) {

        private var item: CarritoItem? = null

        init {
            binding.botonCambiar.setOnClickListener { item?.let(alCambiarCantidad) }
            binding.botonQuitar.setOnClickListener { item?.let(alQuitar) }
        }

        fun mostrar(fila: FilaCarrito.Item) = with(binding) {
            item = fila.item
            val contexto = root.context
            val alimento = fila.item.alimento
            // Sin foto queda el recuadro gris (el fondo de la vista).
            if (alimento?.fotoUrl == null) {
                miniatura.dispose()
                miniatura.setImageDrawable(null)
            } else {
                miniatura.load(alimento.fotoUrl) { crossfade(true) }
            }
            val nombre = alimento?.nombre.orEmpty()
            miniatura.contentDescription = contexto.getString(R.string.alimento_foto_descripcion, nombre)
            textoNombre.text = nombre
            val cantidad = alimento?.let { contexto.textoCantidad(fila.item.cantidad, it.unidadMedida) } ?: fila.item.cantidad
            textoCantidad.text = contexto.getString(R.string.carrito_item_cantidad, cantidad)

            val valido = fila.item.esValido
            etiquetaInvalido.isVisible = !valido
            if (!valido) etiquetaInvalido.mostrarEtiqueta(contexto.getString(R.string.carrito_no_disponible), FamiliaColor.ROJO)
            textoMotivos.mostrarAviso(
                fila.item.motivos.orEmpty().filter { it.isNotBlank() }.takeIf { !valido && it.isNotEmpty() }
                    ?.joinToString("\n") { "• $it" }
            )
            textoError.mostrarAviso(fila.error)

            botonCambiar.isVisible = fila.editable
            botonCambiar.isEnabled = fila.habilitado
            botonQuitar.isInvisible = fila.quitando
            botonQuitar.isEnabled = fila.habilitado
            indicadorQuitar.isVisible = fila.quitando
        }
    }

    private object Diferencia : DiffUtil.ItemCallback<FilaCarrito>() {
        override fun areItemsTheSame(viejo: FilaCarrito, nuevo: FilaCarrito) = viejo.clave == nuevo.clave
        override fun areContentsTheSame(viejo: FilaCarrito, nuevo: FilaCarrito) = viejo == nuevo
    }

    private companion object {
        const val TIPO_AVISOS = 0
        const val TIPO_GRUPO = 1
        const val TIPO_ITEM = 2
    }
}
