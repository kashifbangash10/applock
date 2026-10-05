package com.applockerapp.presentation.customview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

class PatternLockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dots = ArrayList<Dot>()
    private val selectedDots = ArrayList<Dot>()
    private var isDrawing = false
    private var currentX = 0f
    private var currentY = 0f

    // Reusable Path to avoid GC allocations in onDraw
    private val linePath = Path()

    // Reusable Paints to avoid GC allocations in onDraw
    private val dotNormalPaint = Paint().apply {
        color = 0xFF5A6582.toInt() // text_muted
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val dotSelectedPaint = Paint().apply {
        color = 0xFF38BDF8.toInt() // primary Steel Cyan — matches app theme
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val innerDotPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = 0xAA38BDF8.toInt() // semi-translucent Steel Cyan — matches app theme
        style = Paint.Style.STROKE
        strokeWidth = 16f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val errorPaint = Paint().apply {
        color = 0xFFFF5252.toInt() // error/red
        style = Paint.Style.STROKE
        strokeWidth = 16f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val errorDotPaint = Paint().apply {
        color = 0xFFFF5252.toInt()
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var isError = false
    private var onPatternListener: OnPatternListener? = null

    interface OnPatternListener {
        fun onPatternComplete(pattern: List<Int>)
    }

    fun setOnPatternListener(listener: OnPatternListener) {
        this.onPatternListener = listener
    }

    fun clearPattern() {
        selectedDots.clear()
        isError = false
        isDrawing = false
        invalidate()
    }

    fun setDemoPattern(patternIds: List<Int>) {
        initDotsIfNeeded()
        selectedDots.clear()
        for (id in patternIds) {
            val dot = dots.find { it.id == id }
            if (dot != null) {
                selectedDots.add(dot)
            }
        }
        isDrawing = false
        isError = false
        invalidate()
    }

    fun setPatternError() {
        isError = true
        invalidate()
    }

    fun setPatternColors(normalColor: Int, selectedColor: Int, lineColor: Int) {
        dotNormalPaint.color = normalColor
        dotSelectedPaint.color = selectedColor
        linePaint.color = lineColor
        invalidate()
    }

    data class Dot(val id: Int, val x: Float, val y: Float, val row: Int, val col: Int)

    private fun initDotsIfNeeded() {
        // Only initialize when we have real measured dimensions
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return   // wait for onSizeChanged with real dimensions

        if (dots.size == 9) return

        dots.clear()
        selectedDots.clear()

        // Place dots at 1/4, 2/4, 3/4 of each axis — always inside view bounds
        val colStep = w / 4f
        val rowStep = h / 4f

        var count = 0
        for (row in 0..2) {
            for (col in 0..2) {
                val cx = colStep * (col + 1)
                val cy = rowStep * (row + 1)
                dots.add(Dot(count++, cx, cy, row, col))
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        clearPattern()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        clearPattern()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            clearPattern()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        dots.clear()
        initDotsIfNeeded()
        invalidate()
    }

    private var normalEmoji: String? = null
    private var selectedEmoji: String? = null

    private val emojiPaint = Paint().apply {
        textSize = 60f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    fun setPatternEmojis(normal: String?, selected: String?) {
        this.normalEmoji = normal
        this.selectedEmoji = selected
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        initDotsIfNeeded()

        // Draw lines using reusable Path
        if (selectedDots.isNotEmpty()) {
            linePath.reset()
            val first = selectedDots.first()
            linePath.moveTo(first.x, first.y)
            for (i in 1 until selectedDots.size) {
                val dot = selectedDots[i]
                linePath.lineTo(dot.x, dot.y)
            }
            if (isDrawing && !isError) {
                linePath.lineTo(currentX, currentY)
            }
            canvas.drawPath(linePath, if (isError) errorPaint else linePaint)
        }

        // Draw dots
        val radius = 26f
        val selectedRadius = 36f
        val normalEmojiText = normalEmoji
        val selectedEmojiText = selectedEmoji

        for (dot in dots) {
            val isSelected = selectedDots.contains(dot)
            if (isSelected) {
                if (selectedEmojiText != null) {
                    emojiPaint.textSize = 72f
                    val textHeight = emojiPaint.descent() - emojiPaint.ascent()
                    val textOffset = textHeight / 2 - emojiPaint.descent()
                    canvas.drawText(selectedEmojiText, dot.x, dot.y + textOffset, emojiPaint)
                } else {
                    canvas.drawCircle(dot.x, dot.y, selectedRadius, if (isError) errorDotPaint else dotSelectedPaint)
                    canvas.drawCircle(dot.x, dot.y, selectedRadius / 3f, innerDotPaint)
                }
            } else {
                if (normalEmojiText != null) {
                    emojiPaint.textSize = 60f
                    val textHeight = emojiPaint.descent() - emojiPaint.ascent()
                    val textOffset = textHeight / 2 - emojiPaint.descent()
                    canvas.drawText(normalEmojiText, dot.x, dot.y + textOffset, emojiPaint)
                } else {
                    canvas.drawCircle(dot.x, dot.y, radius, dotNormalPaint)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        initDotsIfNeeded()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                clearPattern()
                isDrawing = true
                currentX = event.x
                currentY = event.y
                detectDot(event.x, event.y, isInitialTouch = true)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    currentX = event.x
                    currentY = event.y
                    detectDot(event.x, event.y, isInitialTouch = false)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDrawing) {
                    isDrawing = false
                    if (selectedDots.size >= 3) {
                        val patternIds = selectedDots.map { it.id }
                        onPatternListener?.onPatternComplete(patternIds)
                    } else {
                        clearPattern()
                    }
                    invalidate()
                }
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun detectDot(x: Float, y: Float, isInitialTouch: Boolean) {
        if (dots.isEmpty()) initDotsIfNeeded()
        if (dots.isEmpty()) return

        val spacing = if (width > 0) width / 4f else 200f
        val hitRadius = (spacing * 0.75f).coerceAtLeast(120f)

        var closestDot: Dot? = null
        var minDistanceSq = Float.MAX_VALUE

        for (dot in dots) {
            val dx = dot.x - x
            val dy = dot.y - y
            val distSq = dx * dx + dy * dy
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq
                closestDot = dot
            }
        }

        // Pick closest dot if within hit radius
        if (closestDot != null) {
            val maxHitRadius = if (isInitialTouch) (spacing * 0.85f).coerceAtLeast(130f) else hitRadius
            val threshold = maxHitRadius * maxHitRadius
            if (minDistanceSq <= threshold && !selectedDots.contains(closestDot)) {
                selectedDots.add(closestDot)
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }
}
