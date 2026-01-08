package com.sharks.androidinktablet.model

import java.io.File

/**
 * Data class representing a drawing file
 */
data class DrawingFile(
    val file: File,
    val name: String,
    val lastModified: Long
)
