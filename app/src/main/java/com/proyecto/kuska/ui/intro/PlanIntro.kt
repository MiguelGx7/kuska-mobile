package com.proyecto.kuska.ui.intro

/**
 * Tiempos de la intro en milisegundos desde que aparece (diseño aprobado del paso 16). Los del dibujo están
 * también en res/animator/intro_*.xml: si cambian aquí, hay que cambiarlos allá.
 */
object TiemposIntro {
    /** La hormiga empieza a entrar desde la izquierda. */
    const val ENTRADA = 120L
    const val CAMINATA = 1500L

    /** Llega al centro: salta el grano y aparecen los textos. */
    const val LLEGADA = ENTRADA + CAMINATA
    const val SALTO_GRANO = 450L
    const val RETRASO_PARPADEO = 350L
    const val PARPADEO = 280L
    const val TEXTO = 500L
    const val RETRASO_SUBTITULO = 300L

    /** Todo se desvanece y se queda el destino. */
    const val SALIDA = 3300L
    const val FUNDIDO = 400L

    /** Con las animaciones del sistema desactivadas: todo quieto y se quita la capa, sin fundido. */
    const val SALIDA_SIN_ANIMACIONES = 2600L

    /** Cuánto entra desde la izquierda (dp) y cuánto suben los textos al aparecer (dp). */
    const val DESPLAZAMIENTO_ENTRADA_DP = -340f
    const val SUBIDA_TEXTO_DP = 12f
}

/** Cómo se reproduce la intro. [salirEn]: cuándo empieza a irse; [fundido]: cuánto dura el fundido (0 = sin él). */
data class PlanIntro(
    val animada: Boolean,
    val salirEn: Long,
    val fundido: Long,
) {
    /** Cuándo queda el destino a la vista. */
    val total get() = salirEn + fundido

    companion object {
        fun para(animacionesActivas: Boolean): PlanIntro = if (animacionesActivas) {
            PlanIntro(animada = true, salirEn = TiemposIntro.SALIDA, fundido = TiemposIntro.FUNDIDO)
        } else {
            PlanIntro(animada = false, salirEn = TiemposIntro.SALIDA_SIN_ANIMACIONES, fundido = 0L)
        }

        /** La escala de animación del sistema en 0 (Opciones de desarrollador o accesibilidad) las desactiva. */
        fun animacionesActivas(escalaDuracion: Float): Boolean = escalaDuracion > 0f

        /**
         * Solo al abrir la app en frío. Con estado guardado (giro, restauración del proceso) no; al volver de
         * segundo plano la actividad no se vuelve a crear.
         */
        fun debeMostrar(hayEstadoGuardado: Boolean): Boolean = !hayEstadoGuardado
    }
}
