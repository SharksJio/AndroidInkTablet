package com.sharks.androidinktablet.drawing

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.Toast
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke as InkStroke
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.BrushPaint
import com.sharks.androidinktablet.model.BackgroundType
import com.sharks.androidinktablet.model.EraserMode
import com.sharks.androidinktablet.model.ShapeType
import kotlin.math.hypot

/**
 * Custom drawing container that properly uses AndroidX Ink Library
 * Architecture based on reference projects:
 * - https://github.com/SharksJio/cahier
 * - https://github.com/NicosNicolaou16/Ink_Api_Compose
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // AndroidX Ink components
    private val inProgressStrokesView: InProgressStrokesView
    private val strokeRenderer: CanvasStrokeRenderer
    
    // Stroke management
    private val finishedStrokes = mutableListOf<InkStroke>()
    private val commandHistory = mutableListOf<DrawingCommand>()
    private var historyIndex = -1
    
    // Current tool and brush settings
    private var currentBrush: Brush
    private var currentColor = Color.BLACK
    private var currentSize = 5f
    
    // Track pointer to stroke ID mapping for multi-touch
    private val pointerToStrokeId = mutableMapOf<Int, androidx.ink.strokes.InProgressStrokeId>()
    
    // Listeners
    private var onStrokeChangedListener: (() -> Unit)? = null
    
    // Background
    private var backgroundType = BackgroundType.PLAIN
    private var backgroundBitmap: Bitmap? = null
    private var backgroundCanvas: Canvas? = null
    
    // Eraser mode
    private var eraserMode = EraserMode.PART
    
    // Text and shape support
    private var onTextInsertListener: ((Float, Float) -> Unit)? = null
    private var onShapeInsertListener: ((ShapeType, Float, Float, Float, Float) -> Unit)? = null
    
    init {
        // Initialize InProgressStrokesView
        inProgressStrokesView = InProgressStrokesView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        }
        addView(inProgressStrokesView)
        
        // Initialize stroke renderer
        strokeRenderer = CanvasStrokeRenderer.create()
        
        // Initialize default brush (pen)
        currentBrush = createBrush(currentColor, currentSize)
        
        // Setup touch handling
        setupTouchHandling()
        
        // Setup finished strokes listener
        setupFinishedStrokesListener()
    }
    
    private fun createBrush(color: Int, size: Float): Brush {
        val brushFamily = StockBrushes.markerLatest  // Using marker as default
        val brushPaint = BrushPaint.createWithColorIntArgb(color)
        return Brush(
            family = brushFamily,
            size = size,
            epsilon = 0.1f
        ).apply {
            paint = brushPaint
        }
    }
    
    private fun setupTouchHandling() {
        inProgressStrokesView.setOnTouchListener { view, event ->
            // Always request unbuffered dispatch for lower latency ink
            view.requestUnbufferedDispatch(event)
            
            handleTouchEvent(event)
            true // Consume the event
        }
    }
    
    private fun handleTouchEvent(event: MotionEvent) {
        val action = event.actionMasked
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)
        
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // START: Creates an internal InProgressStroke
                val strokeId = inProgressStrokesView.startStroke(event, pointerId, currentBrush)
                pointerToStrokeId[pointerId] = strokeId
            }
            
            MotionEvent.ACTION_MOVE -> {
                // MOVE: Updates the internal InProgressStroke
                // Handle all active pointers for multi-touch
                for (i in 0 until event.pointerCount) {
                    val pId = event.getPointerId(i)
                    val strokeId = pointerToStrokeId[pId]
                    if (strokeId != null) {
                        inProgressStrokesView.addToStroke(event, pId, strokeId, prediction = null)
                    }
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                // FINISH: Finalizes the InProgressStroke into a Stroke
                val strokeId = pointerToStrokeId[pointerId]
                if (strokeId != null) {
                    inProgressStrokesView.finishStroke(event, pointerId, strokeId)
                    pointerToStrokeId.remove(pointerId)
                }
            }
            
            MotionEvent.ACTION_CANCEL -> {
                // CANCEL: Cancels the in-progress stroke
                val strokeId = pointerToStrokeId[pointerId]
                if (strokeId != null) {
                    inProgressStrokesView.cancelStroke(strokeId, event)
                    pointerToStrokeId.remove(pointerId)
                }
            }
        }
    }
    
    private fun setupFinishedStrokesListener() {
        inProgressStrokesView.addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
            override fun onStrokesFinished(event: InProgressStrokesView.FinishedStrokesEvent) {
                // Handle the finalized strokes from AndroidX Ink
                for (stroke in event.finishedStrokes) {
                    finishedStrokes.add(stroke)
                    addCommand(DrawingCommand.AddStroke(stroke))
                }
                
                // Trigger refresh and notify listeners
                invalidate()
                onStrokeChangedListener?.invoke()
            }
        })
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            // Create background bitmap
            backgroundBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            backgroundCanvas = Canvas(backgroundBitmap!!)
            drawBackgroundToCanvas()
        }
    }
    
    override fun dispatchDraw(canvas: Canvas) {
        // Draw background
        backgroundBitmap?.let { bitmap ->
            canvas.drawBitmap(bitmap, 0f, 0f, null)
        }
        
        // Draw finished strokes using CanvasStrokeRenderer
        for (stroke in finishedStrokes) {
            strokeRenderer.draw(stroke, canvas, null)
        }
        
        // Let InProgressStrokesView draw in-progress strokes
        super.dispatchDraw(canvas)
    }
    
    /**
     * Draw background pattern to canvas
     */
    private fun drawBackgroundToCanvas() {
        backgroundCanvas?.let { canvas ->
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
    
    // Public API methods
    fun setCurrentTool(tool: Tool) {
        // Update brush based on tool
        currentColor = tool.color
        currentSize = tool.size
        currentBrush = createBrush(currentColor, currentSize)
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
        // Create a bitmap with background and all strokes
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Draw background
        backgroundBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        
        // Draw all finished strokes
        for (stroke in finishedStrokes) {
            strokeRenderer.draw(stroke, canvas, null)
        }
        
        return bitmap
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
        backgroundCanvas?.let { canvas ->
            val textPaint = Paint().apply {
                color = currentColor
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
        backgroundCanvas?.let { canvas ->
            val shapePaint = Paint().apply {
                color = currentColor
                strokeWidth = currentSize
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
                    finishedStrokes.remove(command.inkStroke)
                }
                is DrawingCommand.RemoveStroke -> {
                    finishedStrokes.add(command.inkStroke)
                }
                is DrawingCommand.Clear -> {
                    finishedStrokes.addAll(command.strokes)
                }
            }
            invalidate()
        }
    }

    fun redo() {
        if (canRedo()) {
            historyIndex++
            when (val command = commandHistory[historyIndex]) {
                is DrawingCommand.AddStroke -> {
                    finishedStrokes.add(command.inkStroke)
                }
                is DrawingCommand.RemoveStroke -> {
                    finishedStrokes.remove(command.inkStroke)
                }
                is DrawingCommand.Clear -> {
                    finishedStrokes.clear()
                }
            }
            invalidate()
        }
    }

    fun canUndo(): Boolean = historyIndex >= 0

    fun canRedo(): Boolean = historyIndex < commandHistory.size - 1

    fun clearCanvas() {
        val strokesCopy = finishedStrokes.toList()
        finishedStrokes.clear()
        addCommand(DrawingCommand.Clear(strokesCopy))
        drawBackgroundToCanvas()
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
        if (finishedStrokes.isEmpty()) {
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
        if (finishedStrokes.isEmpty()) {
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
        finishedStrokes.clear()
        pointerToStrokeId.clear()
    }
}

/**
 * Represents a drawing command that can be undone/redone
 */
sealed class DrawingCommand {
    data class AddStroke(val inkStroke: InkStroke) : DrawingCommand()
    data class RemoveStroke(val inkStroke: InkStroke) : DrawingCommand()
    data class Clear(val strokes: List<InkStroke>) : DrawingCommand()
}