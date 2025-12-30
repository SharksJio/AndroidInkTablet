package com.sharks.androidinktablet.drawing

import android.graphics.Path
import android.graphics.PointF

/**
 * Represents a stroke drawn on the canvas
 */
data class Stroke(
    val path: Path,
    val tool: Tool,
    val points: MutableList<PointF> = mutableListOf(),
    val pressures: MutableList<Float> = mutableListOf(),
    val timestamps: MutableList<Long> = mutableListOf()
)

/**
 * Represents a drawing command that can be undone/redone
 */
sealed class DrawingCommand {
    data class AddStroke(val stroke: Stroke) : DrawingCommand()
    data class RemoveStroke(val stroke: Stroke) : DrawingCommand()
    data class Clear(val strokes: List<Stroke>) : DrawingCommand()
    data class Transform(val strokes: List<Stroke>, val dx: Float, val dy: Float) : DrawingCommand()
}