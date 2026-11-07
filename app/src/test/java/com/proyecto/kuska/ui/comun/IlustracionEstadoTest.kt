package com.proyecto.kuska.ui.comun

import com.proyecto.kuska.R
import org.junit.Assert.assertEquals
import org.junit.Test

class IlustracionEstadoTest {

    @Test
    fun sinCodigoEsSinConexion() {
        // El repositorio deja el código en null cuando no hubo respuesta (IOException).
        assertEquals(IlustracionEstado.ERROR_RED, IlustracionEstado.deError(null))
    }

    @Test
    fun cuentaBloqueadaOSinPermisoEs403() {
        assertEquals(IlustracionEstado.ERROR_403, IlustracionEstado.deError(403))
    }

    @Test
    fun servidorYCualquierOtroEs500() {
        listOf(400, 401, 404, 429, 500, 502, 503).forEach {
            assertEquals(it.toString(), IlustracionEstado.ERROR_500, IlustracionEstado.deError(it))
        }
    }

    @Test
    fun dibujoYTamanoDeCadaCaso() {
        assertEquals(R.drawable.hormiga_vacia, IlustracionEstado.PLATO_VACIO.dibujo)
        assertEquals(R.dimen.ilustracion_vacia_ancho, IlustracionEstado.PLATO_VACIO.ancho)
        assertEquals(R.dimen.ilustracion_vacia_alto, IlustracionEstado.PLATO_VACIO.alto)
        assertEquals(R.drawable.hormiga_error_red, IlustracionEstado.ERROR_RED.dibujo)
        assertEquals(R.drawable.hormiga_error_403, IlustracionEstado.ERROR_403.dibujo)
        assertEquals(R.drawable.hormiga_error_500, IlustracionEstado.ERROR_500.dibujo)
        listOf(IlustracionEstado.ERROR_RED, IlustracionEstado.ERROR_403, IlustracionEstado.ERROR_500).forEach {
            assertEquals(R.dimen.ilustracion_error_ancho, it.ancho)
            assertEquals(R.dimen.ilustracion_error_alto, it.alto)
        }
    }
}
