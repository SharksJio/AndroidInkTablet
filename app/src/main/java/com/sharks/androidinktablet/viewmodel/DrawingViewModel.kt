package com.sharks.androidinktablet.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.sharks.androidinktablet.drawing.Tool
import com.sharks.androidinktablet.drawing.ToolType
import com.sharks.androidinktablet.model.BackgroundType
import com.sharks.androidinktablet.model.DrawingFile

/**
 * ViewModel for managing drawing state and operations
 * Follows MVVM architecture pattern
 */
class DrawingViewModel(application: Application) : AndroidViewModel(application) {
    
    // Current tool state
    private val _currentTool = MutableLiveData<Tool>().apply {
        value = Tool(ToolType.PEN, Color.BLACK, 5f, 1.0f)
    }
    val currentTool: LiveData<Tool> = _currentTool
    
    // Current color
    private val _currentColor = MutableLiveData<Int>().apply {
        value = Color.BLACK
    }
    val currentColor: LiveData<Int> = _currentColor
    
    // Current brush size
    private val _brushSize = MutableLiveData<Float>().apply {
        value = 5f
    }
    val brushSize: LiveData<Float> = _brushSize
    
    // Pressure sensitivity
    private val _pressureSensitivity = MutableLiveData<Float>().apply {
        value = 1.0f
    }
    val pressureSensitivity: LiveData<Float> = _pressureSensitivity
    
    // Background type
    private val _backgroundType = MutableLiveData<BackgroundType>().apply {
        value = BackgroundType.PLAIN
    }
    val backgroundType: LiveData<BackgroundType> = _backgroundType
    
    // Toolbar visibility
    private val _isToolbarMinimized = MutableLiveData<Boolean>().apply {
        value = false
    }
    val isToolbarMinimized: LiveData<Boolean> = _isToolbarMinimized
    
    // Undo/Redo state
    private val _canUndo = MutableLiveData<Boolean>().apply {
        value = false
    }
    val canUndo: LiveData<Boolean> = _canUndo
    
    private val _canRedo = MutableLiveData<Boolean>().apply {
        value = false
    }
    val canRedo: LiveData<Boolean> = _canRedo
    
    // Current file
    private val _currentFile = MutableLiveData<DrawingFile?>().apply {
        value = null
    }
    val currentFile: LiveData<DrawingFile?> = _currentFile
    
    // Loading state
    private val _isLoading = MutableLiveData<Boolean>().apply {
        value = false
    }
    val isLoading: LiveData<Boolean> = _isLoading
    
    /**
     * Update the current tool
     */
    fun setCurrentTool(toolType: ToolType) {
        val color = if (toolType == ToolType.ERASER) {
            Color.TRANSPARENT
        } else {
            _currentColor.value ?: Color.BLACK
        }
        
        _currentTool.value = Tool(
            type = toolType,
            color = color,
            size = _brushSize.value ?: 5f,
            pressureSensitivity = _pressureSensitivity.value ?: 1.0f
        )
    }
    
    /**
     * Update the current color
     */
    fun setColor(color: Int) {
        _currentColor.value = color
        _currentTool.value?.let { tool ->
            if (tool.type != ToolType.ERASER) {
                _currentTool.value = tool.copy(color = color)
            }
        }
    }
    
    /**
     * Update brush size
     */
    fun setBrushSize(size: Float) {
        _brushSize.value = size
        _currentTool.value?.let { tool ->
            _currentTool.value = tool.copy(size = size)
        }
    }
    
    /**
     * Update pressure sensitivity
     */
    fun setPressureSensitivity(sensitivity: Float) {
        _pressureSensitivity.value = sensitivity
        _currentTool.value?.let { tool ->
            _currentTool.value = tool.copy(pressureSensitivity = sensitivity)
        }
    }
    
    /**
     * Set background type
     */
    fun setBackgroundType(type: BackgroundType) {
        _backgroundType.value = type
    }
    
    /**
     * Toggle toolbar minimization state
     */
    fun toggleToolbarMinimized() {
        _isToolbarMinimized.value = !(_isToolbarMinimized.value ?: false)
    }
    
    /**
     * Update undo/redo state
     */
    fun updateUndoRedoState(canUndo: Boolean, canRedo: Boolean) {
        _canUndo.value = canUndo
        _canRedo.value = canRedo
    }
    
    /**
     * Set current file
     */
    fun setCurrentFile(file: DrawingFile?) {
        _currentFile.value = file
    }
    
    /**
     * Set loading state
     */
    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }
    
    /**
     * Create a new file
     */
    fun createNewFile() {
        _currentFile.value = null
    }
}
