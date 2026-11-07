package com.proyecto.kuska.ui.creardonacion

import com.proyecto.kuska.data.api.modelos.DatosAlimento
import com.proyecto.kuska.data.api.modelos.DatosDonacion
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Arma el campo "datos" de POST donaciones/ con datos ya validados. Los textos opcionales vacíos van como null. */
object ConstructorDatos {

    fun construir(
        titulo: String,
        descripcion: String,
        direccion: String,
        modalidad: String,
        alimentos: List<AlimentoBorrador>,
    ): DatosDonacion = DatosDonacion(
        titulo = titulo.trim(),
        descripcion = descripcion.trim().ifEmpty { null },
        direccionRecogida = direccion.trim().ifEmpty { null },
        modalidadEntrega = modalidad,
        alimentos = alimentos.map(::construirAlimento),
    )

    /** Un alimento ya validado, con los mismos campos para POST donaciones/ y POST donaciones/{id}/alimentos/. */
    internal fun construirAlimento(alimento: AlimentoBorrador) = DatosAlimento(
        categoria = requireNotNull(alimento.categoriaId),
        nombre = alimento.nombre.trim(),
        descripcion = alimento.descripcion.trim().ifEmpty { null },
        unidadMedida = requireNotNull(alimento.unidadMedida),
        cantidadTotal = ValidacionAlimento.normalizarCantidad(alimento.cantidad),
        fechaVencimiento = formatoApi().format(requireNotNull(alimento.fechaVencimiento)),
    )

    /**
     * Campos de texto del multipart de POST donaciones/{id}/alimentos/ (AlimentoNuevoRequest), en el orden
     * del schema. La descripción vacía no se envía. La foto va aparte.
     */
    fun camposMultipart(alimento: DatosAlimento): List<Pair<String, String>> = buildList {
        add("categoria" to alimento.categoria.toString())
        add("nombre" to alimento.nombre)
        alimento.descripcion?.let { add("descripcion" to it) }
        add("unidad_medida" to alimento.unidadMedida)
        add("cantidad_total" to alimento.cantidadTotal)
        add("fecha_vencimiento" to alimento.fechaVencimiento)
    }

    /** yyyy-MM-dd en UTC, porque la fecha es la medianoche UTC del día elegido. */
    private fun formatoApi() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}
