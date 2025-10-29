package com.sharks.androidinktablet.drawing

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.ink.strokes.InProgressStroke
import androidx.ink.strokes.Stroke as InkStroke
import androidx.ink.geometry.MutableVec
import androidx.ink.strokes.StrokeInput
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * Custom view for handling drawing with AndroidX Ink Library support
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var currentTool = Tool(ToolType.PEN, Color.BLACK, 5f)
    private var currentStroke: Stroke? = null
    private val strokes = mutableListOf<Stroke>()
    private val commandHistory = mutableListOf<DrawingCommand>()
    private var historyIndex = -1

    private var onStrokeChangedListener: (() -> Unit)? = null

    // AndroidX Ink Library
    private var inProgressStroke: InProgressStroke? = null
    private val inkStrokes = mutableListOf<InkStroke>()

    private val backgroundPaint = Paint().apply {
        color = Color.WHITE
    }

    // Canvas bitmap for performance
    private var canvasBitmap: Bitmap? = null
    private var drawCanvas: Canvas? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawCanvas = Canvas(canvasBitmap!!)
            drawCanvas?.drawColor(Color.WHITE)
            redrawAllStrokes()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw the cached bitmap
        canvasBitmap?.let { bitmap ->
            canvas.drawBitmap(bitmap, 0f, 0f, null)
        }

        // Draw current stroke if in progress
        currentStroke?.let { stroke ->
            configurePaintForTool(stroke.tool)
            canvas.drawPath(stroke.path, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val pressure = event.pressure
        val timestamp = System.currentTimeMillis()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startNewStroke(x, y, pressure, timestamp)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                continueStroke(x, y, pressure, timestamp)
                return true
            }
            MotionEvent.ACTION_UP -> {
                finishStroke()
                return true
            }
        }
        return false
    }

    private fun startNewStroke(x: Float, y: Float, pressure: Float, timestamp: Long) {
        val adjustedPressure = pressure * currentTool.pressureSensitivity
        
        currentStroke = Stroke(Path(), currentTool.copy()).apply {
            path.moveTo(x, y)
            points.add(PointF(x, y))
            pressures.add(adjustedPressure)
            timestamps.add(timestamp)
        }

        // Start AndroidX Ink stroke
        inProgressStroke = InProgressStroke.Builder().build()

        invalidate()
    }

    private fun continueStroke(x: Float, y: Float, pressure: Float, timestamp: Long) {
        currentStroke?.let { stroke ->
            val adjustedPressure = pressure * currentTool.pressureSensitivity
            
            stroke.path.lineTo(x, y)
            stroke.points.add(PointF(x, y))
            stroke.pressures.add(adjustedPressure)
            stroke.timestamps.add(timestamp)

            // Add point to AndroidX Ink stroke
            inProgressStroke?.enqueueInput(
                StrokeInput(
                    x = x,
                    y = y,
                    elapsedTimeMillis = timestamp
                )
            )

            invalidate()
        }
    }

    private fun finishStroke() {
        currentStroke?.let { stroke ->
            // Add to strokes list
            strokes.add(stroke)
            
            // Draw to canvas bitmap
            drawCanvas?.let { canvas ->
                configurePaintForTool(stroke.tool)
                canvas.drawPath(stroke.path, paint)
            }

            // Finish AndroidX Ink stroke
            inProgressStroke?.let { inkStroke ->
                try {
                    val finishedStroke = inkStroke.finishStroke()
                    inkStrokes.add(finishedStroke)
                } catch (e: Exception) {
                    // Handle error in finishing stroke
                }
            }

            // Add to command history
            addCommand(DrawingCommand.AddStroke(stroke))
            currentStroke = null
            inProgressStroke = null

            onStrokeChangedListener?.invoke()
            invalidate()
        }
    }

    private fun configurePaintForTool(tool: Tool) {
        paint.color = tool.color
        paint.strokeWidth = tool.size
        
        when (tool.type) {
            ToolType.PEN -> {
                paint.alpha = 255
                paint.pathEffect = null
            }
            ToolType.PENCIL -> {
                paint.alpha = 200
                paint.pathEffect = null
            }
            ToolType.MARKER -> {
                paint.alpha = 150
                paint.strokeWidth = tool.size * 2f // Markers are wider
                paint.pathEffect = null
            }
            ToolType.ERASER -> {
                paint.color = Color.WHITE
                paint.alpha = 255
                paint.pathEffect = null
            }
            ToolType.LASSO -> {
                paint.color = Color.BLUE
                paint.alpha = 128
                paint.pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
            }
        }
    }

    private fun redrawAllStrokes() {
        drawCanvas?.let { canvas ->
            canvas.drawColor(Color.WHITE)
            for (stroke in strokes) {
                configurePaintForTool(stroke.tool)
                canvas.drawPath(stroke.path, paint)
            }
        }
    }

    // Public API methods
    fun setCurrentTool(tool: Tool) {
        currentTool = tool
    }

    fun setOnStrokeChangedListener(listener: () -> Unit) {
        onStrokeChangedListener = listener
    }

    fun undo() {
        if (canUndo()) {
            historyIndex--
            when (val command = commandHistory[historyIndex + 1]) {
                is DrawingCommand.AddStroke -> {
                    strokes.remove(command.stroke)
                }
                is DrawingCommand.RemoveStroke -> {
                    strokes.add(command.stroke)
                }
                is DrawingCommand.Clear -> {
                    strokes.addAll(command.strokes)
                }
            }
            redrawAllStrokes()
            invalidate()
        }
    }

    fun redo() {
        if (canRedo()) {
            historyIndex++
            when (val command = commandHistory[historyIndex]) {
                is DrawingCommand.AddStroke -> {
                    strokes.add(command.stroke)
                }
                is DrawingCommand.RemoveStroke -> {
                    strokes.remove(command.stroke)
                }
                is DrawingCommand.Clear -> {
                    strokes.clear()
                }
            }
            redrawAllStrokes()
            invalidate()
        }
    }

    fun canUndo(): Boolean = historyIndex >= 0

    fun canRedo(): Boolean = historyIndex < commandHistory.size - 1

    fun clearCanvas() {
        val strokesCopy = strokes.toList()
        strokes.clear()
        addCommand(DrawingCommand.Clear(strokesCopy))
        
        drawCanvas?.drawColor(Color.WHITE)
        inkStrokes.clear() // Clear AndroidX Ink strokes
        invalidate()
    }

    private fun addCommand(command: DrawingCommand) {
        // Remove any commands after current position
        if (historyIndex < commandHistory.size - 1) {
            commandHistory.subList(historyIndex + 1, commandHistory.size).clear()
        }
        
        commandHistory.add(command)
        historyIndex = commandHistory.size - 1
        
        // Limit history size
        if (commandHistory.size > 50) {
            commandHistory.removeFirst()
            historyIndex--
        }
    }

    // Image insertion
    fun insertImage(uri: Uri) {
        // TODO: Implement image insertion
        Toast.makeText(context, "Image insertion not yet implemented", Toast.LENGTH_SHORT).show()
    }

    // Save/Load functionality
    fun saveDrawing() {
        // TODO: Implement save functionality
        Toast.makeText(context, "Save functionality not yet implemented", Toast.LENGTH_SHORT).show()
    }

    fun loadDrawing() {
        // TODO: Implement load functionality
        Toast.makeText(context, "Load functionality not yet implemented", Toast.LENGTH_SHORT).show()
    }

    // AI Features
    fun performTextRecognition() {
        if (strokes.isEmpty()) {
            Toast.makeText(context, "No strokes to recognize", Toast.LENGTH_SHORT).show()
            return
        }

        // AndroidX Ink Library doesn't include text recognition out of the box
        // Text recognition would require integration with a separate ML model
        Toast.makeText(context, "Text recognition requires ML model integration", Toast.LENGTH_SHORT).show()
    }

    fun performShapeDetection() {
        // TODO: Implement shape detection
        Toast.makeText(context, "Shape detection not yet implemented", Toast.LENGTH_SHORT).show()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Clean up AndroidX Ink resources
        inkStrokes.clear()
    }
}