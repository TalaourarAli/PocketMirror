package com.pocketmirror.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.widget.FrameLayout

class MaskedMirrorContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var shapeMode: String = MirrorPrefs.SHAPE_CIRCLE
        set(value) {
            field = value
            rebuildShapePath()
            invalidate()
        }

    /** Progression de l'animation entre 0f (dans la caméra) et 1f (ouvert) */
    var revealProgress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val shapePath = Path()
    private val rectF = RectF()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(225, 255, 255, 255)
        strokeWidth = 3f * resources.displayMetrics.density
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShapePath()
    }

    private fun rebuildShapePath() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val halfStroke = borderPaint.strokeWidth / 2f
        rectF.set(halfStroke, halfStroke, w - halfStroke, h - halfStroke)
        shapePath.reset()

        when (shapeMode) {
            MirrorPrefs.SHAPE_ROUNDED_SQ -> {
                val r = 42f * resources.displayMetrics.density
                shapePath.addRoundRect(rectF, r, r, Path.Direction.CW)
            }
            MirrorPrefs.SHAPE_PILL -> {
                val r = rectF.width() / 2f
                shapePath.addRoundRect(rectF, r, r, Path.Direction.CW)
            }
            else -> {
                shapePath.addOval(rectF, Path.Direction.CW)
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return

        // Si progress <= 0, on ne dessine rien à l'écran MAIS il ne faut surtout pas bloquer
        // l'initialisation du SurfaceTexture interne de TextureView au 1er layout !
        // On laisse donc au moins un alpha/scale infime ou on clippe hors champ tant que revealProgress == 0f
        if (revealProgress <= 0.005f) {
            val s = canvas.save()
            canvas.clipRect(0f, 0f, 0f, 0f)
            super.dispatchDraw(canvas)
            canvas.restoreToCount(s)
            return
        }

        val saveCount = canvas.save()

        // Sort directement du poinçon de la caméra (haut-centre : width/2, 0)
        val pivotX = width / 2f
        val pivotY = 0f
        canvas.scale(revealProgress, revealProgress, pivotX, pivotY)

        // Découpe stricte selon la forme choisie -> zéro rectangle noir !
        canvas.clipPath(shapePath)
        super.dispatchDraw(canvas)
        canvas.drawPath(shapePath, borderPaint)

        canvas.restoreToCount(saveCount)
    }
}
