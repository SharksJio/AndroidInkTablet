package com.sharks.androidinktablet.drawing

import android.graphics.Path
import android.graphics.PointF

/**
 * Legacy stroke representation
 * This is kept for backward compatibility with existing Tool system
 */
data class Stroke(
    val path: Path,
    val tool: Tool,
    val points: MutableList<PointF> = mutableListOf(),
    val pressures: MutableList<Float> = mutableListOf(),
    val timestamps: MutableList<Long> = mutableListOf()
)