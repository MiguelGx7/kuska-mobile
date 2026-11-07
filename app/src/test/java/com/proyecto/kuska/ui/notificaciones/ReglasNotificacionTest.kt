package com.proyecto.kuska.ui.notificaciones

import com.proyecto.kuska.R
import com.proyecto.kuska.data.api.modelos.Notificacion
import com.proyecto.kuska.data.api.modelos.Usuario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun notificacion(
    id: Int = 1,
    tipo: String = Notificacion.TIPO_SOLICITUD_APROBADA,
    donacion: Int? = null,
    solicitud: Int? = null,
    leida: Boolean = false,
) = Notificacion(
    id = id, tipo = tipo, titulo = "Título $id", mensaje = "Mensaje $id", donacion = donacion,
    solicitud = solicitud, leida = leida, fecha = "2026-11-07T10:00:00-05:00",
)

class ReglasNotificacionTest {

    @Test
    fun contadorSinNumeroSiNoHayNoLeidas() {
        assertNull(ReglasNotificacion.textoContador(null))
        assertNull(ReglasNotificacion.textoContador(0))
        assertNull(ReglasNotificacion.textoContador(-3))
    }

    @Test
    fun contadorHasta99YLuego99Mas() {
        assertEquals("1", ReglasNotificacion.textoContador(1))
        assertEquals("99", ReglasNotificacion.textoContador(99))
        assertEquals("99+", ReglasNotificacion.textoContador(100))
        assertEquals("99+", ReglasNotificacion.textoContador(250))
    }

    @Test
    fun donanteVaAlDetalleDeLaDonacion() {
        assertEquals(
            DestinoNotificacion.Donacion(5),
            ReglasNotificacion.destino(Usuario.ROL_DONANTE, notificacion(donacion = 5, solicitud = 9)),
        )
        assertNull(ReglasNotificacion.destino(Usuario.ROL_DONANTE, notificacion(solicitud = 9)))
    }

    @Test
    fun beneficiarioVaAlDetalleDeLaSolicitud() {
        assertEquals(
            DestinoNotificacion.Solicitud(9),
            ReglasNotificacion.destino(Usuario.ROL_BENEFICIARIO, notificacion(donacion = 5, solicitud = 9)),
        )
        assertNull(ReglasNotificacion.destino(Usuario.ROL_BENEFICIARIO, notificacion(donacion = 5)))
    }

    @Test
    fun sinDestinoSoloSeMarcaComoLeida() {
        val sancion = notificacion(tipo = Notificacion.TIPO_SANCION)
        assertNull(ReglasNotificacion.destino(Usuario.ROL_DONANTE, sancion))
        assertNull(ReglasNotificacion.destino(Usuario.ROL_BENEFICIARIO, sancion))
        // Sin rol conocido (p. ej. sin sesión o administrador) no se abre nada.
        assertNull(ReglasNotificacion.destino(null, notificacion(donacion = 5, solicitud = 9)))
        assertNull(ReglasNotificacion.destino(Usuario.ROL_ADMINISTRADOR, notificacion(donacion = 5, solicitud = 9)))
    }

    @Test
    fun iconoSegunElGrupoDelTipo() {
        val esperados = mapOf(
            Notificacion.TIPO_NUEVA_DONACION to R.drawable.ic_donaciones,
            Notificacion.TIPO_DONACION_VALIDADA to R.drawable.ic_donaciones,
            Notificacion.TIPO_DONACION_RECHAZADA to R.drawable.ic_donaciones,
            Notificacion.TIPO_NUEVA_SOLICITUD to R.drawable.ic_pedidos,
            Notificacion.TIPO_SOLICITUD_APROBADA to R.drawable.ic_pedidos,
            Notificacion.TIPO_SOLICITUD_RECHAZADA to R.drawable.ic_pedidos,
            Notificacion.TIPO_SOLICITUD_CANCELADA to R.drawable.ic_pedidos,
            Notificacion.TIPO_SOLICITUD_NO_RECOGIDA to R.drawable.ic_pedidos,
            Notificacion.TIPO_SOLICITUD_ENTREGADA to R.drawable.ic_entrega,
            Notificacion.TIPO_NOVEDAD_CREADA to R.drawable.ic_novedad,
            Notificacion.TIPO_NOVEDAD_ACEPTADA to R.drawable.ic_novedad,
            Notificacion.TIPO_NOVEDAD_RECHAZADA to R.drawable.ic_novedad,
            Notificacion.TIPO_ALIMENTO_CANCELADO to R.drawable.ic_novedad,
            Notificacion.TIPO_ENTREGA_RECLAMADA to R.drawable.ic_reclamo,
            Notificacion.TIPO_RECLAMO_REVISADO to R.drawable.ic_reclamo,
            Notificacion.TIPO_SANCION to R.drawable.ic_candado,
        )
        assertEquals(16, esperados.size)
        esperados.forEach { (tipo, icono) -> assertEquals(tipo, icono, ReglasNotificacion.icono(tipo)) }
        assertEquals(R.drawable.ic_campana, ReglasNotificacion.icono("tipo_nuevo"))
    }

    @Test
    fun marcarUnaSoloCambiaEsa() {
        val lista = listOf(notificacion(1), notificacion(2), notificacion(3, leida = true))
        val marcada = ReglasNotificacion.conLeida(lista, id = 2, leida = true)
        assertEquals(listOf(false, true, true), marcada.map { it.leida })
        // Deshacer (si falló marcarla en el servidor).
        assertEquals(listOf(false, false, true), ReglasNotificacion.conLeida(marcada, id = 2, leida = false).map { it.leida })
        // Un id que no está no cambia nada.
        assertEquals(lista, ReglasNotificacion.conLeida(lista, id = 99, leida = true))
    }

    @Test
    fun marcarTodasDejaTodasLeidas() {
        val lista = listOf(notificacion(1), notificacion(2, leida = true), notificacion(3))
        assertEquals(listOf(true, true, true), ReglasNotificacion.todasLeidas(lista).map { it.leida })
        assertEquals(listOf(1, 2, 3), ReglasNotificacion.todasLeidas(lista).map { it.id })
    }
}
