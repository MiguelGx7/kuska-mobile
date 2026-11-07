package com.proyecto.kuska.ui.carrito

import com.proyecto.kuska.data.api.modelos.CarritoGrupo
import com.proyecto.kuska.data.api.modelos.CarritoItem
import com.proyecto.kuska.ui.comun.TextoUi
import com.proyecto.kuska.ui.detallealimento.CantidadPedido

/** Filas de la lista del carrito (un solo RecyclerView con varios tipos). */
sealed interface FilaCarrito {
    val clave: String

    /** Avisos generales del carrito (límites) y errores generales del último envío. */
    data class Avisos(val mensajes: List<String>) : FilaCarrito {
        override val clave = "avisos"
    }

    /**
     * Encabezado de una donación. [eleccion] solo para las "Ambas" (null en las demás). [grupo] va sin sus ítems,
     * para que cambiar un ítem no vuelva a dibujar el encabezado (y su campo de dirección).
     */
    data class Grupo(
        val grupo: CarritoGrupo,
        val eleccion: EleccionEntrega?,
        val error: TextoUi?,
        val habilitado: Boolean,
    ) : FilaCarrito {
        override val clave = "grupo_${grupo.donacion}"
    }

    /** [editable]: queda algo que pedir de ese alimento. [quitando]: se está quitando este ítem. */
    data class Item(
        val item: CarritoItem,
        val error: String?,
        val editable: Boolean,
        val quitando: Boolean,
        val habilitado: Boolean,
    ) : FilaCarrito {
        override val clave = "item_${item.id}"
    }
}

object FilasCarrito {

    fun armar(estado: CarritoEstado): List<FilaCarrito> {
        val carrito = estado.carrito ?: return emptyList()
        if (carrito.grupos.isEmpty()) return emptyList()
        val habilitado = !estado.ocupado
        return buildList {
            val avisos = (carrito.avisos.orEmpty() + estado.errores.generales).filter { it.isNotBlank() }.distinct()
            if (avisos.isNotEmpty()) add(FilaCarrito.Avisos(avisos))
            carrito.grupos.forEach { grupo ->
                val eleccion = if (EnvioCarrito.pideEleccion(grupo)) {
                    estado.elecciones[grupo.donacion] ?: EleccionEntrega()
                } else {
                    null
                }
                val error = estado.erroresLocales[grupo.donacion]
                    ?: estado.errores.porDonacion[grupo.donacion]?.let(TextoUi::Texto)
                add(FilaCarrito.Grupo(grupo.copy(items = emptyList()), eleccion, error, habilitado))
                grupo.items.orEmpty().forEach { item ->
                    add(
                        FilaCarrito.Item(
                            item = item,
                            error = estado.errores.porItem[item.id],
                            editable = item.alimento?.let(CantidadPedido::maximo) != null,
                            quitando = estado.quitandoId == item.id,
                            habilitado = habilitado,
                        )
                    )
                }
            }
        }
    }
}
