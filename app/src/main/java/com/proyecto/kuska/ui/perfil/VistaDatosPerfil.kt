package com.proyecto.kuska.ui.perfil

import android.view.LayoutInflater
import android.widget.LinearLayout
import com.proyecto.kuska.databinding.ItemDatoPerfilBinding
import com.proyecto.kuska.ui.comun.resolver

/** Llena el contenedor con un item_dato_perfil por cada dato (Perfil y la parte de solo lectura de Editar perfil). */
fun LinearLayout.mostrarDatos(datos: List<DatoPerfil>) {
    removeAllViews()
    val inflador = LayoutInflater.from(context)
    datos.forEach { dato ->
        val fila = ItemDatoPerfilBinding.inflate(inflador, this, true)
        fila.textoEtiqueta.setText(dato.etiqueta)
        fila.textoValor.text = dato.valor.resolver(context)
    }
}
