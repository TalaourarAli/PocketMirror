package com.pocketmirror.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.widget.FrameLayout
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Conteneur du miroir : découpe la preview caméra selon la forme choisie et
 * dessine un cadre décoratif autour, pour l'effet « vrai miroir ».
 */
class MaskedMirrorContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var shapeMode: MirrorShape = MirrorShape.CIRCLE
        set(value) {
            field = value
            rebuildPaths()
            invalidate()
        }

    /** Progression de l'animation entre 0f (dans la caméra) et 1f (ouvert) */
    var revealProgress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val density = context.resources.displayMetrics.density
    private val frameWidthPx: Float
        get() = (min(width, height) * FRAME_RATIO).coerceAtLeast(MIN_FRAME_DP * density)

    private val contentPath = Path()
    private val outerPath = Path()
    private val framePath = Path()
    private val shapeRect = RectF()

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val outerEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x40000000
        strokeWidth = 1f * density
    }
    private val innerHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x99FFFFFF.toInt()
        strokeWidth = 1.6f * density
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildPaths()
    }

    private fun rebuildPaths() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val frame = frameWidthPx
        if (frame <= 0f) return

        contentPath.reset()
        outerPath.reset()
        framePath.reset()

        if (shapeMode == MirrorShape.CIRCLE) {
            val centerX = w / 2f
            val centerY = h / 2f
            val outerRadius = min(w, h) / 2f
            // Les festons mordent sur le bord extérieur, pas sur la découpe caméra.
            val amplitude = frame * SCALLOP_DEPTH
            buildScallopedCircle(
                outerPath,
                centerX,
                centerY,
                outerRadius - amplitude,
                SCALLOP_LOBES,
                amplitude
            )
            contentPath.addCircle(centerX, centerY, outerRadius - frame, Path.Direction.CW)
        } else {
            shapeRect.set(0f, 0f, w, h)
            addRoundedShape(outerPath, shapeRect, shapeMode)
            shapeRect.set(frame, frame, w - frame, h - frame)
            addRoundedShape(contentPath, shapeRect, shapeMode)
        }

        framePath.op(outerPath, contentPath, Path.Op.DIFFERENCE)
        framePaint.shader = SweepGradient(w / 2f, h / 2f, FRAME_COLORS, FRAME_POSITIONS)
    }

    private fun addRoundedShape(path: Path, rect: RectF, shape: MirrorShape) {
        if (shape == MirrorShape.PILL) {
            val radius = rect.width() / 2f
            path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        } else {
            val radius = 42f * density
            path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        }
    }

    /** Cercle festonné : r(θ) = base + amplitude·cos(lobes·θ). */
    private fun buildScallopedCircle(
        path: Path,
        centerX: Float,
        centerY: Float,
        baseRadius: Float,
        lobes: Int,
        amplitude: Float,
    ) {
        val steps = lobes * SCALLOP_STEPS_PER_LOBE
        for (i in 0..steps) {
            val angle = (i.toFloat() / steps) * TWO_PI
            val radius = baseRadius + amplitude * cos(lobes * angle)
            val x = centerX + radius * cos(angle)
            val y = centerY + radius * sin(angle)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return

        val saveCount = canvas.save()

        // Sort directement du poinçon de la caméra (haut-centre : width/2, 0)
        val pivotX = width / 2f
        val pivotY = 0f

        if (revealProgress <= 0.004f) {
            // On dessine malgré tout (clip 1px) pour que le TextureView crée sa surface GPU,
            // sinon CameraX reste bloqué sur "surface requested" et la preview n'arrive jamais.
            canvas.clipRect(pivotX, pivotY, pivotX + 1f, pivotY + 1f)
            super.dispatchDraw(canvas)
            canvas.restoreToCount(saveCount)
            return
        }

        canvas.scale(revealProgress, revealProgress, pivotX, pivotY)

        // Cadre décoratif, puis preview découpée à la forme intérieure.
        canvas.drawPath(framePath, framePaint)
        canvas.drawPath(outerPath, outerEdgePaint)

        canvas.clipPath(contentPath)
        super.dispatchDraw(canvas)

        canvas.restoreToCount(saveCount)

        // Filet lumineux sur le bord intérieur : effet verre / miroir.
        canvas.drawPath(contentPath, innerHighlightPaint)
    }

    private companion object {
        /** Épaisseur du cadre, en fraction du plus petit côté. */
        const val FRAME_RATIO = 0.055f
        const val MIN_FRAME_DP = 6f

        const val SCALLOP_LOBES = 14
        const val SCALLOP_DEPTH = 0.85f
        const val SCALLOP_STEPS_PER_LOBE = 20

        val TWO_PI = (2.0 * Math.PI).toFloat()

        val FRAME_COLORS = intArrayOf(
            0xFF8FD3FF.toInt(),
            0xFF2F79C4.toInt(),
            0xFFA9E0FF.toInt(),
            0xFF1F5C9E.toInt(),
            0xFF6FB7EC.toInt(),
            0xFF8FD3FF.toInt(),
        )
        val FRAME_POSITIONS = floatArrayOf(0f, 0.22f, 0.4f, 0.62f, 0.82f, 1f)
    }
}
