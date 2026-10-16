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

    private fun construirAlimento(alimento: AlimentoBorrador) = DatosAlimento(
        categoria = requireNotNull(alimento.categoriaId),
        nombre = alimento.nombre.trim(),
        descripcion = alimento.descripcion.trim().ifEmpty { null },
        unidadMedida = requireNotNull(alimento.unidadMedida),
        cantidadTotal = ValidacionAlimento.normalizarCantidad(alimento.cantidad),
        fechaVencimiento = formatoApi().format(requireNotNull(alimento.fechaVencimiento)),
    )

    /** yyyy-MM-dd en UTC, porque la fecha es la medianoche UTC del día elegido. */
    private fun formatoApi() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}
