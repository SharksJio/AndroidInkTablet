package com.sharks.androidinktablet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.Slider
import com.sharks.androidinktablet.drawing.DrawingView
import com.sharks.androidinktablet.drawing.Tool
import com.sharks.androidinktablet.drawing.ToolType

/**
 * Main activity for the Android Ink Tablet application.
 * Handles the main drawing interface and tool interactions.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var drawingView: DrawingView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var settingsPanel: MaterialCardView
    private lateinit var sizeSlider: Slider
    private lateinit var pressureSlider: Slider
    
    // Tool buttons
    private lateinit var btnPen: ImageButton
    private lateinit var btnPencil: ImageButton
    private lateinit var btnMarker: ImageButton
    private lateinit var btnEraser: ImageButton
    private lateinit var btnLasso: ImageButton
    private lateinit var btnImage: ImageButton
    private lateinit var btnColorPicker: ImageButton
    
    // FABs
    private lateinit var fabUndo: FloatingActionButton
    private lateinit var fabRedo: FloatingActionButton
    
    private var currentTool = ToolType.PEN
    private var currentColor = Color.BLACK
    private var currentSize = 5f
    private var pressureSensitivity = 1.0f

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                drawingView.insertImage(uri)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openImagePicker()
        } else {
            Toast.makeText(this, "Permission required to access images", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Enable edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.canvasContainer)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        initializeViews()
        setupDrawingView()
        setupToolbar()
        setupToolButtons()
        setupFloatingActionButtons()
        setupSettingsPanel()
        
        // Set initial tool
        selectTool(ToolType.PEN)
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        settingsPanel = findViewById(R.id.settingsPanel)
        sizeSlider = findViewById(R.id.sizeSlider)
        pressureSlider = findViewById(R.id.pressureSlider)
        
        btnPen = findViewById(R.id.btnPen)
        btnPencil = findViewById(R.id.btnPencil)
        btnMarker = findViewById(R.id.btnMarker)
        btnEraser = findViewById(R.id.btnEraser)
        btnLasso = findViewById(R.id.btnLasso)
        btnImage = findViewById(R.id.btnImage)
        btnColorPicker = findViewById(R.id.btnColorPicker)
        
        fabUndo = findViewById(R.id.fabUndo)
        fabRedo = findViewById(R.id.fabRedo)
    }

    private fun setupDrawingView() {
        drawingView = DrawingView(this)
        drawingView.setOnStrokeChangedListener {
            updateUndoRedoButtons()
        }
        
        val canvasContainer = findViewById<android.widget.FrameLayout>(R.id.canvasContainer)
        canvasContainer.addView(drawingView)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = getString(R.string.app_name)
            setDisplayShowTitleEnabled(true)
        }
    }

    private fun setupToolButtons() {
        btnPen.setOnClickListener { selectTool(ToolType.PEN) }
        btnPencil.setOnClickListener { selectTool(ToolType.PENCIL) }
        btnMarker.setOnClickListener { selectTool(ToolType.MARKER) }
        btnEraser.setOnClickListener { selectTool(ToolType.ERASER) }
        btnLasso.setOnClickListener { selectTool(ToolType.LASSO) }
        btnImage.setOnClickListener { handleImageInsertion() }
        btnColorPicker.setOnClickListener { showColorPicker() }
    }

    private fun setupFloatingActionButtons() {
        fabUndo.setOnClickListener { 
            drawingView.undo()
            updateUndoRedoButtons()
        }
        
        fabRedo.setOnClickListener { 
            drawingView.redo()
            updateUndoRedoButtons()
        }
        
        updateUndoRedoButtons()
    }

    private fun setupSettingsPanel() {
        sizeSlider.addOnChangeListener { _, value, _ ->
            currentSize = value
            updateCurrentTool()
        }
        
        pressureSlider.addOnChangeListener { _, value, _ ->
            pressureSensitivity = value
            updateCurrentTool()
        }
    }

    private fun selectTool(toolType: ToolType) {
        currentTool = toolType
        
        // Update button states
        resetToolButtonStates()
        when (toolType) {
            ToolType.PEN -> btnPen.isSelected = true
            ToolType.PENCIL -> btnPencil.isSelected = true
            ToolType.MARKER -> btnMarker.isSelected = true
            ToolType.ERASER -> btnEraser.isSelected = true
            ToolType.LASSO -> btnLasso.isSelected = true
        }
        
        updateCurrentTool()
        
        // Show/hide settings panel for drawing tools
        val shouldShowSettings = toolType in listOf(ToolType.PEN, ToolType.PENCIL, ToolType.MARKER)
        settingsPanel.visibility = if (shouldShowSettings) View.VISIBLE else View.GONE
    }

    private fun resetToolButtonStates() {
        btnPen.isSelected = false
        btnPencil.isSelected = false
        btnMarker.isSelected = false
        btnEraser.isSelected = false
        btnLasso.isSelected = false
    }

    private fun updateCurrentTool() {
        val tool = Tool(
            type = currentTool,
            color = if (currentTool == ToolType.ERASER) Color.TRANSPARENT else currentColor,
            size = currentSize,
            pressureSensitivity = pressureSensitivity
        )
        drawingView.setCurrentTool(tool)
    }

    private fun showColorPicker() {
        // TODO: Implement color picker dialog
        // For now, cycle through basic colors
        val colors = arrayOf(
            Color.BLACK, Color.BLUE, Color.RED, Color.GREEN,
            Color.parseColor("#9C27B0"), // Purple
            Color.parseColor("#FF9800"), // Orange
            Color.parseColor("#795548"), // Brown
            Color.GRAY
        )
        
        val currentIndex = colors.indexOf(currentColor)
        val nextIndex = (currentIndex + 1) % colors.size
        currentColor = colors[nextIndex]
        updateCurrentTool()
        
        // Update color picker button background to show current color
        btnColorPicker.setColorFilter(currentColor)
    }

    private fun handleImageInsertion() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            openImagePicker()
        } else {
            // Request permission
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun updateUndoRedoButtons() {
        fabUndo.isEnabled = drawingView.canUndo()
        fabRedo.isEnabled = drawingView.canRedo()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_new -> {
                drawingView.clearCanvas()
                updateUndoRedoButtons()
                true
            }
            R.id.action_save -> {
                drawingView.saveDrawing()
                true
            }
            R.id.action_load -> {
                drawingView.loadDrawing()
                true
            }
            R.id.action_clear -> {
                drawingView.clearCanvas()
                updateUndoRedoButtons()
                true
            }
            R.id.action_ai_text -> {
                drawingView.performTextRecognition()
                true
            }
            R.id.action_ai_shape -> {
                drawingView.performShapeDetection()
                true
            }
            R.id.action_settings -> {
                // Toggle settings panel visibility
                settingsPanel.visibility = if (settingsPanel.visibility == View.VISIBLE) 
                    View.GONE else View.VISIBLE
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}