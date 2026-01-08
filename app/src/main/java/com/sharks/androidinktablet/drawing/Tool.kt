package com.sharks.androidinktablet.drawing

/**
 * Enum representing different drawing tool types
 */
enum class ToolType {
    PEN,
    PENCIL,
    MARKER,
    HIGHLIGHTER,
    ERASER,
    LASSO,
    TEXT,
    SHAPE
}

/**
 * Data class representing a drawing tool with its properties
 */
data class Tool(
    val type: ToolType,
    val color: Int,
    val size: Float,
    val pressureSensitivity: Float = 1.0f
)