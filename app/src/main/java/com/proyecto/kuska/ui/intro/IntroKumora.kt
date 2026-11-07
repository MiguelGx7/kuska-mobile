package com.proyecto.kuska.ui.intro

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.proyecto.kuska.R
import com.proyecto.kuska.databinding.VistaIntroBinding

/**
 * Intro animada de la hormiga (paso 16), como una capa encima de toda la actividad. MainActivity arma el destino
 * (login o inicio del rol) debajo mientras tanto; la intro no decide nada de la sesión.
 *
 * - La hormiga entra desde la izquierda (AnimatedVectorDrawable para cuerpo, patas, grano y ojos) y luego aparecen
 *   los textos. A los 3,3 s todo se desvanece (0,4 s) y se quita la capa.
 * - Tocar la pantalla pasa directo al fundido. Atrás cierra la app.
 * - Con las animaciones del sistema en 0: todo quieto, y la capa se quita a los 2,6 s sin fundido.
 * - Si al salir el destino todavía no está armado, espera a [avisarDestinoListo].
 */
class IntroKumora(
    private val actividad: AppCompatActivity,
    /** Raíz del contenido de la actividad: se oculta al lector de pantalla mientras está la intro. */
    private val contenido: View,
    private val alTerminar: () -> Unit,
) {
    private val binding = VistaIntroBinding.inflate(actividad.layoutInflater)
    private val plan = PlanIntro.para(animacionesActivas())
    private val hormiga: AnimatedVectorDrawable? =
        ContextCompat.getDrawable(actividad, R.drawable.hormiga_intro_animada) as? AnimatedVectorDrawable

    private var destinoListo = false
    private var quiereSalir = false
    private var saliendo = false
    private var reloj: ValueAnimator? = null
    private val salirSinAnimaciones = Runnable { pedirSalida() }

    private val atras = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            actividad.finish()
        }
    }

    private val barras = WindowCompat.getInsetsController(actividad.window, actividad.window.decorView)
    private val barraEstadoClara = barras.isAppearanceLightStatusBars
    private val barraNavegacionClara = barras.isAppearanceLightNavigationBars

    fun mostrar() {
        actividad.addContentView(
            binding.root,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        hormiga?.let(binding.hormiga::setImageDrawable)
        contenido.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        // Fondo siempre blanco: íconos de las barras del sistema oscuros, también en modo oscuro.
        barras.isAppearanceLightStatusBars = true
        barras.isAppearanceLightNavigationBars = true
        actividad.onBackPressedDispatcher.addCallback(atras)
        binding.root.setOnClickListener { pedirSalida() }

        if (plan.animada) animar() else binding.root.postDelayed(salirSinAnimaciones, plan.salirEn)
    }

    /** MainActivity ya armó el destino (leyó la sesión). */
    fun avisarDestinoListo() {
        destinoListo = true
        if (quiereSalir) salir()
    }

    private fun animar() = with(binding) {
        hormiga.translationX = dp(TiemposIntro.DESPLAZAMIENTO_ENTRADA_DP)
        listOf(textoTitulo, textoSubtitulo).forEach {
            it.alpha = 0f
            it.translationY = dp(TiemposIntro.SUBIDA_TEXTO_DP)
        }

        this@IntroKumora.hormiga?.start()
        hormiga.animate()
            .translationX(0f)
            .setStartDelay(TiemposIntro.ENTRADA)
            .setDuration(TiemposIntro.CAMINATA)
            .setInterpolator(PathInterpolator(0.2f, 0.7f, 0.3f, 1f))
        textoTitulo.animate()
            .alpha(1f).translationY(0f)
            .setStartDelay(TiemposIntro.LLEGADA)
            .setDuration(TiemposIntro.TEXTO)
        textoSubtitulo.animate()
            .alpha(1f).translationY(0f)
            .setStartDelay(TiemposIntro.LLEGADA + TiemposIntro.RETRASO_SUBTITULO)
            .setDuration(TiemposIntro.TEXTO)

        // Reloj de la salida: es un animador, así se escala igual que los demás si la escala del sistema no es 1.
        reloj = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = plan.salirEn
            addListener(object : AnimatorListenerAdapter() {
                private var cancelado = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelado = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelado) pedirSalida()
                }
            })
            start()
        }
    }

    /** A los 3,3 s (2,6 s sin animaciones) o al tocar. */
    private fun pedirSalida() {
        if (quiereSalir) return
        quiereSalir = true
        detenerAnimaciones()
        if (destinoListo) salir()
    }

    /** Todo a su pose final: hormiga en el centro y en reposo, textos visibles. */
    private fun detenerAnimaciones() = with(binding) {
        reloj?.cancel()
        root.removeCallbacks(salirSinAnimaciones)
        this@IntroKumora.hormiga?.let {
            it.stop()
            it.reset()
        }
        hormiga.animate().cancel()
        hormiga.translationX = 0f
        listOf(textoTitulo, textoSubtitulo).forEach {
            it.animate().cancel()
            it.alpha = 1f
            it.translationY = 0f
        }
    }

    private fun salir() {
        if (saliendo) return
        saliendo = true
        if (plan.fundido > 0) {
            binding.root.animate().alpha(0f).setStartDelay(0).setDuration(plan.fundido).withEndAction(::quitar)
        } else {
            quitar()
        }
    }

    private fun quitar() {
        (binding.root.parent as? ViewGroup)?.removeView(binding.root)
        contenido.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        barras.isAppearanceLightStatusBars = barraEstadoClara
        barras.isAppearanceLightNavigationBars = barraNavegacionClara
        atras.remove()
        alTerminar()
    }

    private fun animacionesActivas(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ValueAnimator.areAnimatorsEnabled()
    } else {
        PlanIntro.animacionesActivas(
            Settings.Global.getFloat(actividad.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        )
    }

    private fun dp(valor: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valor, actividad.resources.displayMetrics)
}
