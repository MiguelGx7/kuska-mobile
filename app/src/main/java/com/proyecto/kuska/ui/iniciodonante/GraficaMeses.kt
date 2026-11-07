package com.proyecto.kuska.ui.iniciodonante

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.google.android.material.color.MaterialColors

/**
 * Barras verticales agrupadas de los últimos 12 meses (HU-037): donaciones (color principal) y entregas (terciario)
 * por mes, la inicial del mes abajo y el máximo de la escala arriba a la izquierda. Sin librería. La descripción
 * accesible la pone la pantalla con todos los valores.
 */
class GraficaMeses @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var meses: List<BarraMes> = emptyList()

    private val pinturaDonaciones = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pinturaEntregas = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pinturaBase = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pinturaTexto = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()

    private val altoEtiquetas = dp(20f)
    private val altoMaximo = dp(16f)
    private val radio = dp(2f)
    private val separacionBarras = dp(2f)

    init {
        pinturaDonaciones.color = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
        pinturaEntregas.color = MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiary)
        pinturaBase.color = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)
        pinturaBase.strokeWidth = dp(1f)
        pinturaTexto.color = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)
        pinturaTexto.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun mostrar(meses: List<BarraMes>) {
        if (this.meses == meses) return
        this.meses = meses
        invalidate()
    }

    override fun onMeasure(anchoMedida: Int, altoMedida: Int) {
        val ancho = MeasureSpec.getSize(anchoMedida)
        val alto = resolveSize(dp(ALTO_DP).toInt(), altoMedida)
        setMeasuredDimension(ancho, alto)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (meses.isEmpty()) return
        val maximo = EscalaGrafica.maximo(meses.flatMap { listOf(it.donaciones, it.entregas) })

        val izquierda = paddingLeft.toFloat()
        val derecha = width - paddingRight.toFloat()
        val arriba = paddingTop + altoMaximo
        val base = height - paddingBottom - altoEtiquetas
        val altoUtil = (base - arriba).coerceAtLeast(0f)
        val anchoMes = (derecha - izquierda) / meses.size
        // Dos barras por mes, con aire a los lados.
        val anchoBarra = ((anchoMes * 0.7f) - separacionBarras) / 2f

        pinturaTexto.textAlign = Paint.Align.LEFT
        canvas.drawText(maximo.toString(), izquierda, paddingTop + pinturaTexto.textSize, pinturaTexto)
        pinturaTexto.textAlign = Paint.Align.CENTER
        canvas.drawLine(izquierda, base, derecha, base, pinturaBase)

        meses.forEachIndexed { indice, mes ->
            val centro = izquierda + anchoMes * indice + anchoMes / 2f
            dibujarBarra(canvas, centro - separacionBarras / 2f - anchoBarra, anchoBarra, base, altoUtil,
                EscalaGrafica.proporcion(mes.donaciones, maximo), pinturaDonaciones)
            dibujarBarra(canvas, centro + separacionBarras / 2f, anchoBarra, base, altoUtil,
                EscalaGrafica.proporcion(mes.entregas, maximo), pinturaEntregas)
            canvas.drawText(mes.inicial, centro, height - paddingBottom - dp(4f), pinturaTexto)
        }
    }

    private fun dibujarBarra(canvas: Canvas, x: Float, ancho: Float, base: Float, altoUtil: Float, proporcion: Float, pintura: Paint) {
        if (proporcion <= 0f) return
        // Un valor chico igual se ve (mínimo 2dp).
        val alto = (altoUtil * proporcion).coerceAtLeast(dp(2f))
        rect.set(x, base - alto, x + ancho, base)
        canvas.drawRoundRect(rect, radio, radio, pintura)
    }

    private fun dp(valor: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valor, resources.displayMetrics)

    private companion object {
        const val ALTO_DP = 160f
    }
}
