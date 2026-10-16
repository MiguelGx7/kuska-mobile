package com.proyecto.kuska.ui.comun

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Cuenta regresiva para habilitar "Reenviar código". Corre en el [scope] del ViewModel,
 * así que sigue al rotar. [alCambiar] recibe los segundos restantes (llega a 0).
 */
class ContadorReenvio(
    private val scope: CoroutineScope,
    private val alCambiar: (Int) -> Unit,
) {
    private var trabajo: Job? = null

    /** Empieza (o reinicia) la cuenta desde [SEGUNDOS]. */
    fun iniciar() {
        trabajo?.cancel()
        trabajo = scope.launch {
            for (segundos in SEGUNDOS downTo 0) {
                alCambiar(segundos)
                if (segundos > 0) delay(1_000)
            }
        }
    }

    companion object {
        const val SEGUNDOS = 60
    }
}
