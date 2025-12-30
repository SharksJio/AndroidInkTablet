package com.sharks.androidinktablet.drawing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Region
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

    // Lasso Selection
    private val selectedStrokes = mutableListOf<Stroke>()
    private var isSelectionActive = false
    private val selectionBounds = RectF()
    private var isMovingSelection = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var totalSelectionDx = 0f
    private var totalSelectionDy = 0f
    private val selectionPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    // PDF Support
    private var pdfBitmap: Bitmap? = null
    private val pdfPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private val commandHistory = mutableListOf<DrawingCommand>()
    private var historyIndex = -1

    private var onStrokeChangedListener: (() -> Unit)? = null
    var onRequestPdfRefresh: (() -> Unit)? = null

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
            // Make background transparent to show PDF behind
            drawCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            redrawAllStrokes()

            // Refresh PDF if active
            if (pdfBitmap != null) {
                onRequestPdfRefresh?.invoke()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw PDF background if available, otherwise white background
        if (pdfBitmap != null) {
            // Draw white background first to handle transparent PDFs
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(pdfBitmap!!, 0f, 0f, pdfPaint)
        } else {
            canvas.drawColor(Color.WHITE)
        }

        // Draw the cached bitmap (contains unselected strokes)
        canvasBitmap?.let { bitmap ->
            canvas.drawBitmap(bitmap, 0f, 0f, null)
        }

        // Draw selected strokes
        if (isSelectionActive) {
            for (stroke in selectedStrokes) {
                configurePaintForTool(stroke.tool)
                // Highlight selected strokes slightly
                val originalAlpha = paint.alpha
                paint.alpha = (originalAlpha * 0.5f).toInt()
                paint.strokeWidth += 2f
                canvas.drawPath(stroke.path, paint)

                // Restore paint
                configurePaintForTool(stroke.tool)
                canvas.drawPath(stroke.path, paint)
            }
            // Draw selection bounding box
            canvas.drawRect(selectionBounds, selectionPaint)
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
                if (isSelectionActive && selectionBounds.contains(x, y)) {
                    isMovingSelection = true
                    lastTouchX = x
                    lastTouchY = y
                    totalSelectionDx = 0f
                    totalSelectionDy = 0f
                    return true
                } else if (isSelectionActive) {
                    // Tap outside selection deselects
                    deselectAll()
                    // Fall through to start new stroke if tool is not Lasso?
                    // Or just consume event. Let's just deselect and consume.
                    return true
                }

                startNewStroke(x, y, pressure, timestamp)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isMovingSelection) {
                    val dx = x - lastTouchX
                    val dy = y - lastTouchY
                    moveSelection(dx, dy)
                    totalSelectionDx += dx
                    totalSelectionDy += dy
                    lastTouchX = x
                    lastTouchY = y
                    return true
                }

                continueStroke(x, y, pressure, timestamp)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isMovingSelection) {
                    isMovingSelection = false
                    if (totalSelectionDx != 0f || totalSelectionDy != 0f) {
                        addCommand(DrawingCommand.Transform(selectedStrokes.toList(), totalSelectionDx, totalSelectionDy))
                    }
                    return true
                }

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
            if (stroke.tool.type == ToolType.LASSO) {
                // Handle Lasso Selection
                stroke.path.close() // Close the loop
                performLassoSelection(stroke.path)
                currentStroke = null
                inProgressStroke = null
                invalidate()
                return
            }

            // Normal stroke
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

    private fun performLassoSelection(lassoPath: Path) {
        val lassoRegion = Region()
        val clipRegion = Region(0, 0, width, height)
        lassoRegion.setPath(lassoPath, clipRegion)

        val newSelected = mutableListOf<Stroke>()
        val it = strokes.iterator()
        while (it.hasNext()) {
            val stroke = it.next()
            val bounds = RectF()
            stroke.path.computeBounds(bounds, true)
            val strokeRegion = Region()
            strokeRegion.setPath(stroke.path, clipRegion)

            // Check for intersection without modifying the original lassoRegion
            if (!lassoRegion.quickReject(strokeRegion)) {
                val intersection = Region(lassoRegion)
                if (intersection.op(strokeRegion, Region.Op.INTERSECT) && !intersection.isEmpty) {
                    newSelected.add(stroke)
                    it.remove()
                }
            }
        }

        if (newSelected.isNotEmpty()) {
            selectedStrokes.addAll(newSelected)
            isSelectionActive = true
            updateSelectionBounds()
            redrawAllStrokes() // To remove selected strokes from bitmap
        }
    }

    private fun updateSelectionBounds() {
        if (selectedStrokes.isEmpty()) {
            selectionBounds.setEmpty()
            return
        }

        val path = Path()
        selectedStrokes.forEach { path.addPath(it.path) }
        path.computeBounds(selectionBounds, true)
        // Add some padding
        selectionBounds.inset(-10f, -10f)
    }

    private fun moveSelection(dx: Float, dy: Float) {
        transformStrokes(selectedStrokes, dx, dy)
        selectionBounds.offset(dx, dy)
        invalidate()
    }

    private fun transformStrokes(strokesToTransform: List<Stroke>, dx: Float, dy: Float) {
        val matrix = Matrix()
        matrix.setTranslate(dx, dy)

        for (stroke in strokesToTransform) {
            stroke.path.transform(matrix)
            for (point in stroke.points) {
                point.x += dx
                point.y += dy
            }
        }
    }

    private fun deselectAll() {
        if (selectedStrokes.isNotEmpty()) {
            strokes.addAll(selectedStrokes)
            selectedStrokes.clear()
            redrawAllStrokes() // Add them back to bitmap
        }
        isSelectionActive = false
        selectionBounds.setEmpty()
        invalidate()
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
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            for (stroke in strokes) {
                configurePaintForTool(stroke.tool)
                canvas.drawPath(stroke.path, paint)
            }
        }
    }

    // Public API methods
    fun setCurrentTool(tool: Tool) {
        if (isSelectionActive && tool.type != ToolType.LASSO) {
            deselectAll()
        }
        currentTool = tool
    }

    fun setOnStrokeChangedListener(listener: () -> Unit) {
        onStrokeChangedListener = listener
    }

    fun undo() {
        if (canUndo()) {
            // If selection active, deselect first to avoid complications
            if (isSelectionActive) deselectAll()

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
                is DrawingCommand.Transform -> {
                    transformStrokes(command.strokes, -command.dx, -command.dy)
                }
            }
            redrawAllStrokes()
            invalidate()
        }
    }

    fun redo() {
        if (canRedo()) {
             // If selection active, deselect first
            if (isSelectionActive) deselectAll()

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
                is DrawingCommand.Transform -> {
                    transformStrokes(command.strokes, command.dx, command.dy)
                }
            }
            redrawAllStrokes()
            invalidate()
        }
    }

    fun canUndo(): Boolean = historyIndex >= 0

    fun canRedo(): Boolean = historyIndex < commandHistory.size - 1

    fun clearCanvas() {
        if (isSelectionActive) deselectAll()

        val strokesCopy = strokes.toList()
        strokes.clear()
        addCommand(DrawingCommand.Clear(strokesCopy))
        
        drawCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
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

    // PDF Support Methods
    fun setPdfBitmap(bitmap: Bitmap?) {
        pdfBitmap = bitmap
        invalidate()
    }

    fun getStrokes(): List<Stroke> {
        if (isSelectionActive) deselectAll()
        return strokes.toList()
    }

    fun setStrokes(newStrokes: List<Stroke>) {
        if (isSelectionActive) deselectAll()
        strokes.clear()
        strokes.addAll(newStrokes)
        redrawAllStrokes()
        invalidate()
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