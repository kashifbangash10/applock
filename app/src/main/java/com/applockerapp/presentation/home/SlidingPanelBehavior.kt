package com.applockerapp.presentation.home

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import kotlin.math.abs

class SlidingPanelBehavior(context: Context, attrs: AttributeSet? = null) :
    CoordinatorLayout.Behavior<View>(context, attrs) {

    var layer1Height: Int = 0
        set(value) {
            field = value
        }

    var onPanelTranslationChanged: ((currentTransY: Float, maxTransY: Int) -> Unit)? = null

    private var isDraggingHeader = false
    private var lastTouchY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ): Boolean {
        return (axes and ViewCompat.SCROLL_AXIS_VERTICAL) != 0
    }

    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int
    ) {
        if (layer1Height <= 0) return

        val currentTransY = child.translationY

        if (dy > 0) {
            // Dragging UP / Scrolling DOWN list: panel slides UP (translationY decreases to 0)
            if (currentTransY > 0f) {
                val newTransY = (currentTransY - dy).coerceAtLeast(0f)
                val consumedY = (currentTransY - newTransY).toInt()
                child.translationY = newTransY
                consumed[1] = consumedY
                onPanelTranslationChanged?.invoke(newTransY, layer1Height)
            }
        } else if (dy < 0) {
            // Dragging DOWN / Scrolling UP list:
            // Only consume if RecyclerView cannot scroll up further
            if (!target.canScrollVertically(-1) && currentTransY < layer1Height) {
                val newTransY = (currentTransY - dy).coerceAtMost(layer1Height.toFloat())
                val consumedY = (currentTransY - newTransY).toInt()
                child.translationY = newTransY
                consumed[1] = consumedY
                onPanelTranslationChanged?.invoke(newTransY, layer1Height)
            }
        }
    }

    override fun onNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray
    ) {
        if (layer1Height <= 0) return
        if (dyUnconsumed < 0 && !target.canScrollVertically(-1)) {
            val currentTransY = child.translationY
            if (currentTransY < layer1Height) {
                val newTransY = (currentTransY - dyUnconsumed).coerceAtMost(layer1Height.toFloat())
                val consumedY = (currentTransY - newTransY).toInt()
                child.translationY = newTransY
                consumed[1] += consumedY
                onPanelTranslationChanged?.invoke(newTransY, layer1Height)
            }
        }
    }

    override fun onInterceptTouchEvent(
        parent: CoordinatorLayout,
        child: View,
        ev: MotionEvent
    ): Boolean {
        if (layer1Height <= 0) return super.onInterceptTouchEvent(parent, child, ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = ev.rawY
                isDraggingHeader = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = lastTouchY - ev.rawY
                // If touch is on header area or panel is partially translated
                if (abs(dy) > touchSlop && isTouchOnPanelHeader(child, ev.x, ev.y)) {
                    isDraggingHeader = true
                    lastTouchY = ev.rawY
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDraggingHeader = false
            }
        }
        return super.onInterceptTouchEvent(parent, child, ev)
    }

    override fun onTouchEvent(
        parent: CoordinatorLayout,
        child: View,
        ev: MotionEvent
    ): Boolean {
        if (layer1Height <= 0) return super.onTouchEvent(parent, child, ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = ev.rawY
                return isTouchOnPanelHeader(child, ev.x, ev.y)
            }
            MotionEvent.ACTION_MOVE -> {
                val rawY = ev.rawY
                val dy = (lastTouchY - rawY).toInt()
                lastTouchY = rawY

                if (dy != 0) {
                    val currentTransY = child.translationY
                    val newTransY = (currentTransY - dy).coerceIn(0f, layer1Height.toFloat())
                    child.translationY = newTransY
                    onPanelTranslationChanged?.invoke(newTransY, layer1Height)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDraggingHeader = false
                return true
            }
        }
        return super.onTouchEvent(parent, child, ev)
    }

    private fun isTouchOnPanelHeader(child: View, x: Float, y: Float): Boolean {
        // Returns true if touch y position is within the panel child bounds but above RecyclerView (or anywhere on child header)
        val childTop = child.top + child.translationY
        return y >= childTop && y <= childTop + 200 // header zone threshold
    }
}
