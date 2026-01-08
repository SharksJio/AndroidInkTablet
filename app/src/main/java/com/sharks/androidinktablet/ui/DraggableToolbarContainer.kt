package com.sharks.androidinktablet.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.min

/**
 * A custom FrameLayout that can be dragged around the screen
 */
class DraggableToolbarContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var dX = 0f
    private var dY = 0f
    private var lastAction = 0
    private var isDragging = false
    private val dragThreshold = 10f // Threshold to distinguish between click and drag

    init {
        // Make the container clickable so it can receive touch events
        isClickable = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dX = x - event.rawX
                dY = y - event.rawY
                lastAction = MotionEvent.ACTION_DOWN
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val newX = event.rawX + dX
                val newY = event.rawY + dY
                
                // Check if movement exceeds threshold
                val deltaX = kotlin.math.abs(newX - x)
                val deltaY = kotlin.math.abs(newY - y)
                
                if (!isDragging && (deltaX > dragThreshold || deltaY > dragThreshold)) {
                    isDragging = true
                }
                
                if (isDragging) {
                    // Get parent dimensions
                    val parent = parent as? ViewGroup
                    parent?.let { p ->
                        val maxX = (p.width - width).toFloat()
                        val maxY = (p.height - height).toFloat()
                        
                        // Constrain movement within parent bounds
                        x = max(0f, min(newX, maxX))
                        y = max(0f, min(newY, maxY))
                    }
                    lastAction = MotionEvent.ACTION_MOVE
                    return true
                }
                return super.onTouchEvent(event)
            }
            MotionEvent.ACTION_UP -> {
                // If we were dragging, consume the event
                // Otherwise, let child views handle the click
                if (isDragging) {
                    isDragging = false
                    return true
                }
                lastAction = MotionEvent.ACTION_UP
                return super.onTouchEvent(event)
            }
            else -> return super.onTouchEvent(event)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Only intercept if we're dragging
        // This allows child buttons to receive clicks normally
        return isDragging
    }
}
