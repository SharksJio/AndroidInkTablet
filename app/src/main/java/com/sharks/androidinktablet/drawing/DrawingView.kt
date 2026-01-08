package com.sharks.androidinktablet.drawing

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.strokes.Stroke as InkStroke
import androidx.ink.geometry.MutableVec
import com.sharks.androidinktablet.R
import com.sharks.androidinktablet.model.BackgroundType
import com.sharks.androidinktablet.model.EraserMode
import com.sharks.androidinktablet.model.ShapeType
import java.util.*
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Custom view for handling drawing with AndroidX Ink Library support
 * Now properly uses InProgressStrokesView for stroke handling
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

    // AndroidX Ink Library - properly managed
    private val inkStrokes = mutableListOf<InkStroke>()
    
    // InProgressStrokesView for handling in-progress strokes
    private var inProgressStrokesView: InProgressStrokesView? = null

    // Background
    private var backgroundType = BackgroundType.PLAIN
    private val backgroundPaint = Paint().apply {
        color = Color.WHITE
    }

    // Canvas bitmap for performance
    private var canvasBitmap: Bitmap? = null
    private var drawCanvas: Canvas? = null
    
    // Eraser mode
    private var eraserMode = EraserMode.PART
    
    // Text and shape support
    private var onTextInsertListener: ((Float, Float) -> Unit)? = null
    private var onShapeInsertListener: ((ShapeType, Float, Float, Float, Float) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawCanvas = Canvas(canvasBitmap!!)
            drawBackgroundToCanvas()
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
    
    /**
     * Set the InProgressStrokesView for handling stroke input
     * This should be called during initialization to properly integrate with AndroidX Ink authoring API
     */
    fun setInProgressStrokesView(view: InProgressStrokesView) {
        inProgressStrokesView = view
        
        // Setup listener to get finalized strokes
        view.addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
            override fun onStrokesFinished(event: InProgressStrokesView.FinishedStrokesEvent) {
                // Handle the finalized strokes
                for (stroke in event.finishedStrokes) {
                    inkStrokes.add(stroke)
                }
                onStrokeChangedListener?.invoke()
            }
        })
    }
    
    /**
     * Draw background pattern to canvas
     */
    private fun drawBackgroundToCanvas() {
        drawCanvas?.let { canvas ->
            // Draw white background first
            canvas.drawColor(Color.WHITE)
            
            // Draw pattern based on background type
            when (backgroundType) {
                BackgroundType.PLAIN -> {
                    // Already white, nothing more to do
                }
                BackgroundType.GRID -> {
                    drawGridPattern(canvas)
                }
                BackgroundType.DOTS -> {
                    drawDotsPattern(canvas)
                }
                BackgroundType.LINES -> {
                    drawLinesPattern(canvas)
                }
            }
        }
    }
    
    private fun drawGridPattern(canvas: Canvas) {
        val gridPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        
        val gridSize = 40f
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()
        
        // Draw vertical lines
        var x = gridSize
        while (x < width) {
            canvas.drawLine(x, 0f, x, height, gridPaint)
            x += gridSize
        }
        
        // Draw horizontal lines
        var y = gridSize
        while (y < height) {
            canvas.drawLine(0f, y, width, y, gridPaint)
            y += gridSize
        }
    }
    
    private fun drawDotsPattern(canvas: Canvas) {
        val dotPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            style = Paint.Style.FILL
        }
        
        val dotSpacing = 20f
        val dotRadius = 1.5f
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()
        
        var y = dotSpacing
        while (y < height) {
            var x = dotSpacing
            while (x < width) {
                canvas.drawCircle(x, y, dotRadius, dotPaint)
                x += dotSpacing
            }
            y += dotSpacing
        }
    }
    
    private fun drawLinesPattern(canvas: Canvas) {
        val linePaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        
        val lineSpacing = 40f
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()
        
        // Draw horizontal lines only
        var y = lineSpacing
        while (y < height) {
            canvas.drawLine(0f, y, width, y, linePaint)
            y += lineSpacing
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Always request unbuffered dispatch for lower latency ink
        requestUnbufferedDispatch(event)
        
        val x = event.x
        val y = event.y
        val pressure = event.pressure
        val timestamp = System.currentTimeMillis()
        
        val action = event.actionMasked
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                startNewStroke(x, y, pressure, timestamp, event, pointerId)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                continueStroke(x, y, pressure, timestamp, event)
                return true
            }
            MotionEvent.ACTION_UP -> {
                finishStroke(event, pointerId)
                return true
            }
        }
        return false
    }

    private fun startNewStroke(x: Float, y: Float, pressure: Float, timestamp: Long, event: MotionEvent, pointerId: Int) {
        val adjustedPressure = pressure * currentTool.pressureSensitivity
        
        currentStroke = Stroke(Path(), currentTool.copy()).apply {
            path.moveTo(x, y)
            points.add(PointF(x, y))
            pressures.add(adjustedPressure)
            timestamps.add(timestamp)
        }

        // Use InProgressStrokesView to start the stroke if available
        inProgressStrokesView?.startStroke(event, pointerId)

        invalidate()
    }

    private fun continueStroke(x: Float, y: Float, pressure: Float, timestamp: Long, event: MotionEvent) {
        currentStroke?.let { stroke ->
            val adjustedPressure = pressure * currentTool.pressureSensitivity
            
            stroke.path.lineTo(x, y)
            stroke.points.add(PointF(x, y))
            stroke.pressures.add(adjustedPressure)
            stroke.timestamps.add(timestamp)

            // InProgressStrokesView automatically handles ACTION_MOVE events
            // through its own touch handling, so we just need to update our local stroke
            inProgressStrokesView?.addToStroke(event, 0)

            invalidate()
        }
    }

    private fun finishStroke(event: MotionEvent, pointerId: Int) {
        currentStroke?.let { stroke ->
            // Add to strokes list
            strokes.add(stroke)
            
            // Draw to canvas bitmap
            drawCanvas?.let { canvas ->
                configurePaintForTool(stroke.tool)
                canvas.drawPath(stroke.path, paint)
            }

            // Finish the stroke in InProgressStrokesView
            inProgressStrokesView?.finishStroke(event, pointerId)

            // Add to command history
            addCommand(DrawingCommand.AddStroke(stroke))
            currentStroke = null

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
                paint.style = Paint.Style.STROKE
            }
            ToolType.PENCIL -> {
                paint.alpha = 200
                paint.pathEffect = null
                paint.style = Paint.Style.STROKE
            }
            ToolType.MARKER -> {
                paint.alpha = 150
                paint.strokeWidth = tool.size * 2f // Markers are wider
                paint.pathEffect = null
                paint.style = Paint.Style.STROKE
            }
            ToolType.HIGHLIGHTER -> {
                paint.alpha = 100  // More transparent than marker
                paint.strokeWidth = tool.size * 3f // Highlighters are wider
                paint.pathEffect = null
                paint.style = Paint.Style.STROKE
            }
            ToolType.ERASER -> {
                paint.color = Color.WHITE
                paint.alpha = 255
                paint.pathEffect = null
                paint.style = Paint.Style.STROKE
            }
            ToolType.LASSO -> {
                paint.color = Color.BLUE
                paint.alpha = 128
                paint.pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
                paint.style = Paint.Style.STROKE
            }
            ToolType.TEXT, ToolType.SHAPE -> {
                // These don't draw strokes directly
                paint.alpha = 255
                paint.pathEffect = null
                paint.style = Paint.Style.STROKE
            }
        }
    }

    private fun redrawAllStrokes() {
        drawCanvas?.let { canvas ->
            drawBackgroundToCanvas()
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
    
    /**
     * Set background type
     */
    fun setBackgroundType(type: BackgroundType) {
        backgroundType = type
        drawBackgroundToCanvas()
        redrawAllStrokes()
        invalidate()
    }
    
    /**
     * Get current background type
     */
    fun getBackgroundType(): BackgroundType = backgroundType
    
    /**
     * Set eraser mode
     */
    fun setEraserMode(mode: EraserMode) {
        eraserMode = mode
    }
    
    /**
     * Get current eraser mode
     */
    fun getEraserMode(): EraserMode = eraserMode
    
    /**
     * Get the current canvas bitmap
     */
    fun getCanvasBitmap(): Bitmap? {
        return canvasBitmap?.copy(Bitmap.Config.ARGB_8888, false)
    }
    
    /**
     * Set listener for text insertion
     */
    fun setOnTextInsertListener(listener: (Float, Float) -> Unit) {
        onTextInsertListener = listener
    }
    
    /**
     * Set listener for shape insertion
     */
    fun setOnShapeInsertListener(listener: (ShapeType, Float, Float, Float, Float) -> Unit) {
        onShapeInsertListener = listener
    }
    
    /**
     * Insert text at position
     */
    fun insertText(text: String, x: Float, y: Float, textSize: Float = 40f) {
        drawCanvas?.let { canvas ->
            val textPaint = Paint().apply {
                color = currentTool.color
                this.textSize = textSize
                isAntiAlias = true
            }
            canvas.drawText(text, x, y, textPaint)
            invalidate()
        }
    }
    
    /**
     * Insert shape at position
     */
    fun insertShape(shapeType: ShapeType, startX: Float, startY: Float, endX: Float, endY: Float) {
        drawCanvas?.let { canvas ->
            val shapePaint = Paint().apply {
                color = currentTool.color
                strokeWidth = currentTool.size
                style = Paint.Style.STROKE
                isAntiAlias = true
            }
            
            when (shapeType) {
                ShapeType.CIRCLE -> {
                    val radius = hypot(endX - startX, endY - startY)
                    canvas.drawCircle(startX, startY, radius, shapePaint)
                }
                ShapeType.RECTANGLE -> {
                    canvas.drawRect(startX, startY, endX, endY, shapePaint)
                }
                ShapeType.TRIANGLE -> {
                    val path = Path().apply {
                        moveTo(startX, endY)  // Bottom left
                        lineTo((startX + endX) / 2, startY)  // Top middle
                        lineTo(endX, endY)  // Bottom right
                        close()
                    }
                    canvas.drawPath(path, shapePaint)
                }
            }
            invalidate()
        }
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
        
        drawBackgroundToCanvas()
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

        // For text recognition, we would need to integrate Google ML Kit Digital Ink Recognition
        // This requires training data download and model setup
        // For now, we show a placeholder message
        Toast.makeText(context, "Text recognition feature requires ML Kit Digital Ink Recognition setup", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Convert all strokes to text using ML Kit
     * This is a placeholder - actual implementation requires ML Kit Digital Ink Recognition
     */
    fun convertStrokesToText(callback: (String) -> Unit) {
        if (inkStrokes.isEmpty()) {
            Toast.makeText(context, "No strokes to convert", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Placeholder for ML Kit integration
        // In production, this would:
        // 1. Create a digital ink model
        // 2. Add all ink strokes to it
        // 3. Use ML Kit to recognize text
        // 4. Call the callback with recognized text
        
        Toast.makeText(context, "Text conversion requires ML Kit Digital Ink Recognition model", Toast.LENGTH_LONG).show()
        callback("Recognized text would appear here")
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