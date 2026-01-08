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
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.ink.authoring.InProgressStrokesView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sharks.androidinktablet.drawing.DrawingView
import com.sharks.androidinktablet.drawing.Tool
import com.sharks.androidinktablet.drawing.ToolType
import com.sharks.androidinktablet.model.BackgroundType
import com.sharks.androidinktablet.model.EraserMode
import com.sharks.androidinktablet.model.ShapeType
import com.sharks.androidinktablet.repository.FileRepository
import com.sharks.androidinktablet.ui.ColorPickerDialog
import com.sharks.androidinktablet.viewmodel.DrawingViewModel
import kotlinx.coroutines.launch

/**
 * Main activity for the Android Ink Tablet application.
 * Handles the main drawing interface and tool interactions with MVVM architecture.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var drawingView: DrawingView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var settingsPanel: MaterialCardView
    private lateinit var sizeSlider: Slider
    private lateinit var pressureSlider: Slider
    
    // Bottom toolbar buttons (optional - can be hidden)
    private lateinit var btnPen: ImageButton
    private lateinit var btnPencil: ImageButton
    private lateinit var btnMarker: ImageButton
    private lateinit var btnEraser: ImageButton
    private lateinit var btnLasso: ImageButton
    private lateinit var btnImage: ImageButton
    private lateinit var btnColorPicker: ImageButton
    
    // Floating toolbar
    private lateinit var floatingToolbar: MaterialCardView
    private lateinit var btnToggleToolbar: ImageButton
    private lateinit var btnUndoToolbar: ImageButton
    private lateinit var btnRedoToolbar: ImageButton
    private lateinit var btnLassoToolbar: ImageButton
    private lateinit var btnEraserToolbar: ImageButton
    private lateinit var btnHighlighterToolbar: ImageButton
    private lateinit var btnPenToolbar: ImageButton
    private lateinit var btnTextToolbar: ImageButton
    private lateinit var btnShapeToolbar: ImageButton
    private lateinit var colorGrid: LinearLayout
    private lateinit var toolbarContent: LinearLayout
    
    // ViewModel
    private val viewModel: DrawingViewModel by viewModels()
    
    // Repository
    private lateinit var fileRepository: FileRepository
    
    private var currentColor = Color.BLACK
    private var isToolbarMinimized = false
    
    // Color palette
    private val colorPalette = arrayOf(
        Color.BLACK,
        Color.parseColor("#1976D2"), // Blue
        Color.parseColor("#F44336"), // Red
        Color.parseColor("#4CAF50"), // Green
        Color.parseColor("#9C27B0"), // Purple
        Color.parseColor("#FF9800"), // Orange
        Color.parseColor("#795548"), // Brown
        Color.parseColor("#607D8B")  // Blue Gray
    )

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

        fileRepository = FileRepository(this)
        
        initializeViews()
        setupDrawingView()
        setupToolbar()
        setupBottomToolbarButtons()
        setupFloatingToolbar()
        setupSettingsPanel()
        observeViewModel()
        
        // Set initial tool
        viewModel.setCurrentTool(ToolType.PEN)
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        settingsPanel = findViewById(R.id.settingsPanel)
        sizeSlider = findViewById(R.id.sizeSlider)
        pressureSlider = findViewById(R.id.pressureSlider)
        
        // Bottom toolbar buttons
        btnPen = findViewById(R.id.btnPen)
        btnPencil = findViewById(R.id.btnPencil)
        btnMarker = findViewById(R.id.btnMarker)
        btnEraser = findViewById(R.id.btnEraser)
        btnLasso = findViewById(R.id.btnLasso)
        btnImage = findViewById(R.id.btnImage)
        btnColorPicker = findViewById(R.id.btnColorPicker)
        
        // Floating toolbar
        floatingToolbar = findViewById(R.id.floatingToolbar)
        toolbarContent = findViewById(R.id.toolbarContent)
        btnToggleToolbar = findViewById(R.id.btnToggleToolbar)
        btnUndoToolbar = findViewById(R.id.btnUndoToolbar)
        btnRedoToolbar = findViewById(R.id.btnRedoToolbar)
        btnLassoToolbar = findViewById(R.id.btnLassoToolbar)
        btnEraserToolbar = findViewById(R.id.btnEraserToolbar)
        btnHighlighterToolbar = findViewById(R.id.btnHighlighterToolbar)
        btnPenToolbar = findViewById(R.id.btnPenToolbar)
        btnTextToolbar = findViewById(R.id.btnTextToolbar)
        btnShapeToolbar = findViewById(R.id.btnShapeToolbar)
        colorGrid = findViewById(R.id.colorGrid)
    }

    private fun setupDrawingView() {
        // Create InProgressStrokesView for handling AndroidX Ink strokes
        val inProgressStrokesView = InProgressStrokesView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        
        // Create custom DrawingView for rendering
        drawingView = DrawingView(this)
        drawingView.setOnStrokeChangedListener {
            viewModel.updateUndoRedoState(drawingView.canUndo(), drawingView.canRedo())
        }
        
        // Link the InProgressStrokesView to DrawingView
        drawingView.setInProgressStrokesView(inProgressStrokesView)
        
        val canvasContainer = findViewById<android.widget.FrameLayout>(R.id.canvasContainer)
        // Add DrawingView first (bottom layer - for rendering finished strokes)
        canvasContainer.addView(drawingView)
        // Add InProgressStrokesView on top (top layer - for handling touch and in-progress strokes)
        canvasContainer.addView(inProgressStrokesView)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = getString(R.string.app_name)
            setDisplayShowTitleEnabled(true)
        }
    }

    private fun setupBottomToolbarButtons() {
        btnPen.setOnClickListener { viewModel.setCurrentTool(ToolType.PEN) }
        btnPencil.setOnClickListener { viewModel.setCurrentTool(ToolType.PENCIL) }
        btnMarker.setOnClickListener { viewModel.setCurrentTool(ToolType.MARKER) }
        btnEraser.setOnClickListener { viewModel.setCurrentTool(ToolType.ERASER) }
        btnLasso.setOnClickListener { viewModel.setCurrentTool(ToolType.LASSO) }
        btnImage.setOnClickListener { handleImageInsertion() }
        btnColorPicker.setOnClickListener { showColorPicker() }
    }
    
    private fun setupFloatingToolbar() {
        // Toggle button
        btnToggleToolbar.setOnClickListener {
            toggleToolbarSize()
        }
        
        // Undo/Redo
        btnUndoToolbar.setOnClickListener { 
            drawingView.undo()
            viewModel.updateUndoRedoState(drawingView.canUndo(), drawingView.canRedo())
        }
        
        btnRedoToolbar.setOnClickListener { 
            drawingView.redo()
            viewModel.updateUndoRedoState(drawingView.canUndo(), drawingView.canRedo())
        }
        
        // Tool buttons
        btnLassoToolbar.setOnClickListener { viewModel.setCurrentTool(ToolType.LASSO) }
        
        btnEraserToolbar.setOnClickListener { viewModel.setCurrentTool(ToolType.ERASER) }
        btnEraserToolbar.setOnLongClickListener {
            showEraserModeDialog()
            true
        }
        
        btnHighlighterToolbar.setOnClickListener { viewModel.setCurrentTool(ToolType.HIGHLIGHTER) }
        
        btnPenToolbar.setOnClickListener { viewModel.setCurrentTool(ToolType.PEN) }
        btnPenToolbar.setOnLongClickListener {
            showPenSizeDialog()
            true
        }
        
        btnTextToolbar.setOnClickListener { viewModel.setCurrentTool(ToolType.TEXT) }
        btnShapeToolbar.setOnClickListener { viewModel.setCurrentTool(ToolType.SHAPE) }
        
        // Setup color buttons
        setupColorButtons()
    }
    
    private fun setupColorButtons() {
        colorGrid.removeAllViews()
        
        colorPalette.forEach { color ->
            val colorButton = ImageButton(this).apply {
                layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                    setMargins(4, 4, 4, 4)
                }
                setBackgroundColor(color)
                setOnClickListener {
                    viewModel.setColor(color)
                }
            }
            colorGrid.addView(colorButton)
        }
    }
    
    private fun toggleToolbarSize() {
        isToolbarMinimized = !isToolbarMinimized
        
        if (isToolbarMinimized) {
            // Hide all tools except toggle button
            for (i in 1 until toolbarContent.childCount) {
                toolbarContent.getChildAt(i).visibility = View.GONE
            }
            btnToggleToolbar.setImageResource(R.drawable.ic_expand)
        } else {
            // Show all tools
            for (i in 1 until toolbarContent.childCount) {
                toolbarContent.getChildAt(i).visibility = View.VISIBLE
            }
            btnToggleToolbar.setImageResource(R.drawable.ic_minimize)
        }
    }

    private fun setupSettingsPanel() {
        sizeSlider.addOnChangeListener { _, value, _ ->
            viewModel.setBrushSize(value)
        }
        
        pressureSlider.addOnChangeListener { _, value, _ ->
            viewModel.setPressureSensitivity(value)
        }
    }
    
    private fun observeViewModel() {
        viewModel.currentTool.observe(this) { tool ->
            drawingView.setCurrentTool(tool)
            updateToolButtonStates(tool.type)
        }
        
        viewModel.currentColor.observe(this) { color ->
            currentColor = color
            btnColorPicker.setColorFilter(color)
        }
        
        viewModel.backgroundType.observe(this) { backgroundType ->
            drawingView.setBackgroundType(backgroundType)
        }
        
        viewModel.canUndo.observe(this) { canUndo ->
            btnUndoToolbar.isEnabled = canUndo
            btnUndoToolbar.alpha = if (canUndo) 1.0f else 0.5f
        }
        
        viewModel.canRedo.observe(this) { canRedo ->
            btnRedoToolbar.isEnabled = canRedo
            btnRedoToolbar.alpha = if (canRedo) 1.0f else 0.5f
        }
    }
    
    private fun updateToolButtonStates(toolType: ToolType) {
        // Reset all button states
        btnPen.isSelected = false
        btnPencil.isSelected = false
        btnMarker.isSelected = false
        btnEraser.isSelected = false
        btnLasso.isSelected = false
        
        btnLassoToolbar.isSelected = false
        btnEraserToolbar.isSelected = false
        btnHighlighterToolbar.isSelected = false
        btnPenToolbar.isSelected = false
        btnTextToolbar.isSelected = false
        btnShapeToolbar.isSelected = false
        
        // Set active button
        when (toolType) {
            ToolType.PEN -> {
                btnPen.isSelected = true
                btnPenToolbar.isSelected = true
            }
            ToolType.PENCIL -> btnPencil.isSelected = true
            ToolType.MARKER -> btnMarker.isSelected = true
            ToolType.HIGHLIGHTER -> btnHighlighterToolbar.isSelected = true
            ToolType.ERASER -> {
                btnEraser.isSelected = true
                btnEraserToolbar.isSelected = true
            }
            ToolType.LASSO -> {
                btnLasso.isSelected = true
                btnLassoToolbar.isSelected = true
            }
            ToolType.TEXT -> btnTextToolbar.isSelected = true
            ToolType.SHAPE -> btnShapeToolbar.isSelected = true
        }
        
        // Show/hide settings panel for drawing tools
        val shouldShowSettings = toolType in listOf(
            ToolType.PEN, ToolType.PENCIL, ToolType.MARKER, ToolType.HIGHLIGHTER
        )
        settingsPanel.visibility = if (shouldShowSettings) View.VISIBLE else View.GONE
    }

    private fun showColorPicker() {
        val colorPickerDialog = ColorPickerDialog()
        colorPickerDialog.setInitialColor(currentColor)
        colorPickerDialog.setOnColorSelectedListener { color ->
            viewModel.setColor(color)
        }
        colorPickerDialog.show(supportFragmentManager, "ColorPickerDialog")
    }
    
    private fun showPenSizeDialog() {
        val sizes = arrayOf("Small (3px)", "Medium (5px)", "Large (8px)", "Extra Large (12px)")
        val sizeValues = floatArrayOf(3f, 5f, 8f, 12f)
        
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_pen_size_title)
            .setItems(sizes) { _, which ->
                viewModel.setBrushSize(sizeValues[which])
                sizeSlider.value = sizeValues[which]
            }
            .show()
    }
    
    private fun showEraserModeDialog() {
        val modes = arrayOf(getString(R.string.eraser_stroke), getString(R.string.eraser_part))
        
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_eraser_mode_title)
            .setItems(modes) { _, which ->
                val mode = if (which == 0) EraserMode.STROKE else EraserMode.PART
                drawingView.setEraserMode(mode)
                Toast.makeText(this, modes[which], Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    private fun showShapePickerDialog() {
        val shapes = arrayOf(
            getString(R.string.shape_circle),
            getString(R.string.shape_rectangle),
            getString(R.string.shape_triangle)
        )
        
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_shape_picker_title)
            .setItems(shapes) { _, which ->
                val shapeType = when (which) {
                    0 -> ShapeType.CIRCLE
                    1 -> ShapeType.RECTANGLE
                    else -> ShapeType.TRIANGLE
                }
                // Placeholder for shape drawing - would need interactive drawing
                Toast.makeText(this, "Draw shape: ${shapes[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    private fun showTextInputDialog() {
        val input = TextInputEditText(this)
        val inputLayout = TextInputLayout(this).apply {
            hint = getString(R.string.dialog_text_input_hint)
            addView(input)
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_text_input_title)
            .setView(inputLayout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val text = input.text.toString()
                if (text.isNotEmpty()) {
                    // Insert text at center of canvas
                    val x = drawingView.width / 2f
                    val y = drawingView.height / 2f
                    drawingView.insertText(text, x, y)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun showBackgroundDialog() {
        val backgrounds = arrayOf(
            getString(R.string.bg_plain),
            getString(R.string.bg_grid),
            getString(R.string.bg_dots),
            getString(R.string.bg_lines)
        )
        
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_background_title)
            .setItems(backgrounds) { _, which ->
                val bgType = when (which) {
                    0 -> BackgroundType.PLAIN
                    1 -> BackgroundType.GRID
                    2 -> BackgroundType.DOTS
                    else -> BackgroundType.LINES
                }
                viewModel.setBackgroundType(bgType)
            }
            .show()
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_open -> {
                openFile()
                true
            }
            R.id.action_new -> {
                createNewFile()
                true
            }
            R.id.action_save -> {
                saveCurrentFile()
                true
            }
            R.id.action_export_png -> {
                exportAsPNG()
                true
            }
            R.id.action_export_jpeg -> {
                exportAsJPEG()
                true
            }
            R.id.action_export_pdf -> {
                exportAsPDF()
                true
            }
            R.id.bg_plain -> {
                viewModel.setBackgroundType(BackgroundType.PLAIN)
                true
            }
            R.id.bg_grid -> {
                viewModel.setBackgroundType(BackgroundType.GRID)
                true
            }
            R.id.bg_dots -> {
                viewModel.setBackgroundType(BackgroundType.DOTS)
                true
            }
            R.id.bg_lines -> {
                viewModel.setBackgroundType(BackgroundType.LINES)
                true
            }
            R.id.action_convert_text -> {
                convertStrokesToText()
                true
            }
            R.id.action_clear -> {
                showClearConfirmationDialog()
                true
            }
            R.id.action_close -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun createNewFile() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_new)
            .setMessage(R.string.dialog_new_drawing_message)
            .setPositiveButton(R.string.button_yes) { _, _ ->
                drawingView.clearCanvas()
                viewModel.createNewFile()
                viewModel.updateUndoRedoState(false, false)
            }
            .setNegativeButton(R.string.button_no, null)
            .show()
    }
    
    private fun saveCurrentFile() {
        val bitmap = drawingView.getCanvasBitmap()
        if (bitmap == null) {
            Toast.makeText(this, "Nothing to save", Toast.LENGTH_SHORT).show()
            return
        }
        
        val fileName = "drawing_${System.currentTimeMillis()}"
        lifecycleScope.launch {
            val result = fileRepository.saveDrawing(bitmap, fileName)
            result.onSuccess {
                viewModel.setCurrentFile(it)
                Toast.makeText(this@MainActivity, "Drawing saved", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@MainActivity, "Failed to save: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun openFile() {
        lifecycleScope.launch {
            val result = fileRepository.listDrawings()
            result.onSuccess { files ->
                if (files.isEmpty()) {
                    Toast.makeText(this@MainActivity, "No saved drawings", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val fileNames = files.map { it.name }.toTypedArray()
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(R.string.menu_open)
                    .setItems(fileNames) { _, which ->
                        // Load file logic would go here
                        Toast.makeText(this@MainActivity, "Load: ${fileNames[which]}", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }.onFailure {
                Toast.makeText(this@MainActivity, "Failed to list files: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun exportAsPNG() {
        val bitmap = drawingView.getCanvasBitmap()
        if (bitmap == null) {
            Toast.makeText(this, "Nothing to export", Toast.LENGTH_SHORT).show()
            return
        }
        
        val fileName = "drawing_${System.currentTimeMillis()}"
        lifecycleScope.launch {
            val result = fileRepository.exportAsPNG(bitmap, fileName)
            result.onSuccess {
                Toast.makeText(this@MainActivity, "Exported as PNG", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@MainActivity, "Export failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun exportAsJPEG() {
        val bitmap = drawingView.getCanvasBitmap()
        if (bitmap == null) {
            Toast.makeText(this, "Nothing to export", Toast.LENGTH_SHORT).show()
            return
        }
        
        val fileName = "drawing_${System.currentTimeMillis()}"
        lifecycleScope.launch {
            val result = fileRepository.exportAsJPEG(bitmap, fileName)
            result.onSuccess {
                Toast.makeText(this@MainActivity, "Exported as JPEG", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@MainActivity, "Export failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun exportAsPDF() {
        val bitmap = drawingView.getCanvasBitmap()
        if (bitmap == null) {
            Toast.makeText(this, "Nothing to export", Toast.LENGTH_SHORT).show()
            return
        }
        
        val fileName = "drawing_${System.currentTimeMillis()}"
        lifecycleScope.launch {
            val result = fileRepository.exportAsPDF(bitmap, fileName)
            result.onSuccess {
                Toast.makeText(this@MainActivity, "Exported as PDF", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@MainActivity, "Export failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun convertStrokesToText() {
        drawingView.convertStrokesToText { recognizedText ->
            MaterialAlertDialogBuilder(this)
                .setTitle("Recognized Text")
                .setMessage(recognizedText)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }
    
    private fun showClearConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_clear_title)
            .setMessage(R.string.dialog_clear_message)
            .setPositiveButton(R.string.button_yes) { _, _ ->
                drawingView.clearCanvas()
                viewModel.updateUndoRedoState(false, false)
            }
            .setNegativeButton(R.string.button_no, null)
            .show()
    }
}
