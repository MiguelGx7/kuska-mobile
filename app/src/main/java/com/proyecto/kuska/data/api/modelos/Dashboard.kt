package com.proyecto.kuska.data.api.modelos

import com.google.gson.annotations.SerializedName

/**
 * Inicio del donante (RN-61, HU-035 a HU-037). Las listas son opcionales por si la API no las manda: Gson no
 * respeta la nulabilidad de Kotlin.
 */
data class DashboardDonante(
    @SerializedName("donaciones_por_estado") val donacionesPorEstado: DonacionesPorEstado?,
    /** Solicitudes de sus donaciones que fueron entregadas. */
    @SerializedName("entregas") val entregas: Int,
    @SerializedName("alimentos_entregados") val alimentosEntregados: Int,
    /** Agrupadas por unidad de medida: no se suman unidades distintas. */
    @SerializedName("cantidades_entregadas") val cantidadesEntregadas: List<CantidadPorUnidad>?,
    /** Beneficiarios distintos que recibieron sus alimentos. */
    @SerializedName("personas_ayudadas") val personasAyudadas: Int,
    /** Últimos 12 meses, con los meses sin datos en 0. */
    @SerializedName("por_mes") val porMes: List<MesDonante>?,
    /** Histórico, solo categorías con alimentos donados. */
    @SerializedName("por_categoria") val porCategoria: List<CategoriaSerie>?,
)

data class DonacionesPorEstado(
    @SerializedName("Pendiente") val pendiente: Int,
    @SerializedName("Validada") val validada: Int,
    @SerializedName("Rechazada") val rechazada: Int,
    @SerializedName("Agotada") val agotada: Int,
    @SerializedName("Finalizada") val finalizada: Int,
)

data class CantidadPorUnidad(
    @SerializedName("unidad_medida") val unidadMedida: String,
    /** Alimentos entregados con esta unidad. */
    @SerializedName("alimentos") val alimentos: Int,
    /** Decimal en texto ("12.50"). */
    @SerializedName("cantidad") val cantidad: String,
)

/** Cuenta registros, no cantidades. [mes]: "AAAA-MM" en hora de Bogotá. */
data class MesDonante(
    @SerializedName("mes") val mes: String,
    /** Sus donaciones recibidas (Validada en adelante) registradas ese mes. */
    @SerializedName("donaciones") val donaciones: Int,
    @SerializedName("entregas") val entregas: Int,
    @SerializedName("alimentos_donados") val alimentosDonados: Int,
    @SerializedName("alimentos_entregados") val alimentosEntregados: Int,
)

data class CategoriaSerie(
    @SerializedName("categoria_id") val categoriaId: Int,
    @SerializedName("categoria") val categoria: String,
    @SerializedName("alimentos_donados") val alimentosDonados: Int,
    @SerializedName("alimentos_entregados") val alimentosEntregados: Int,
)

/** Inicio del beneficiario (RN-62, HU-121). */
data class DashboardBeneficiario(
    @SerializedName("solicitudes_pendientes") val solicitudesPendientes: Int,
    @SerializedName("solicitudes_aprobadas") val solicitudesAprobadas: Int,
    /** Límite de la configuración. */
    @SerializedName("max_solicitudes_activas") val maxSolicitudesActivas: Int,
    /** max − (Pendiente + Aprobada), mínimo 0: cuántas solicitudes más puede enviar. */
    @SerializedName("cupos_disponibles") val cuposDisponibles: Int,
    /** La Aprobada con la fecha límite más cercana que aún no vence; null si no tiene. */
    @SerializedName("proxima_recogida") val proximaRecogida: ProximaRecogida?,
    @SerializedName("notificaciones_no_leidas") val notificacionesNoLeidas: Int,
)

data class ProximaRecogida(
    @SerializedName("solicitud") val solicitud: Int,
    @SerializedName("codigo_pedido") val codigoPedido: String,
    @SerializedName("fecha_limite_recogida") val fechaLimiteRecogida: String,
    /** Recoger o Domicilio (MiSolicitud.MODALIDAD_*). */
    @SerializedName("modalidad_entrega") val modalidadEntrega: String,
    @SerializedName("donacion") val donacion: Int,
    @SerializedName("donacion_titulo") val donacionTitulo: String,
)
