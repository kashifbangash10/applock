package com.applockerapp.presentation.customview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.AnimationUtils
import kotlin.math.cos
import kotlin.math.sin

/**
 * AnimatedGlowBackgroundView — full-screen animated ambient glow background.
 * Renders floating radial blobs of Steel Cyan (#38BDF8), Indigo (#4F46E5)
 * and Deep Cyan (#0EA5E9) over the app's core dark background (#070E1C).
 * All blobs drift slowly and organically to create a living glass-like atmosphere.
 */
class AnimatedGlowBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val blobPaint = Paint().apply {
        isAntiAlias = true
    }

    private class GlowBlob(
        var xFraction: Float,
        var yFraction: Float,
        var radiusFraction: Float,
        val speed: Float,
        val angleOffset: Float,
        val colorStart: Int,
        val colorEnd: Int
    )

    private val blobs = listOf(
        // Steel Cyan large glow — top area
        GlowBlob(0.18f, 0.18f, 0.65f, 0.025f, 0.0f,  0x3038BDF8.toInt(), 0x00000000),
        // Indigo glow — bottom-right area
        GlowBlob(0.82f, 0.72f, 0.70f, 0.018f, 2.1f,  0x224F46E5.toInt(), 0x00000000),
        // Deep Cyan center fill
        GlowBlob(0.50f, 0.42f, 0.75f, 0.012f, 4.3f,  0x1C0EA5E9.toInt(), 0x00000000),
        // Purple accent — top-right
        GlowBlob(0.85f, 0.20f, 0.45f, 0.030f, 1.5f,  0x1A7C3AED.toInt(), 0x00000000),
        // Teal accent — bottom-left
        GlowBlob(0.12f, 0.78f, 0.50f, 0.022f, 3.8f,  0x1806B6D4.toInt(), 0x00000000)
    )

    private var startTime = AnimationUtils.currentAnimationTimeMillis()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Pure dark blue-black background — #070E1C (full rich dark, not light)
        canvas.drawColor(0xFF070E1C.toInt())

        val elapsed = (AnimationUtils.currentAnimationTimeMillis() - startTime) / 1000f

        // Draw 5 floating ambient glow blobs
        for (blob in blobs) {
            val cx = w * (blob.xFraction + 0.07f * sin(blob.speed * elapsed + blob.angleOffset))
            val cy = h * (blob.yFraction + 0.07f * cos(blob.speed * elapsed + blob.angleOffset))
            val radius = Math.max(w, h) * blob.radiusFraction

            blobPaint.shader = RadialGradient(
                cx, cy, radius,
                blob.colorStart, blob.colorEnd,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, radius, blobPaint)
        }

        postInvalidateOnAnimation()
    }
}