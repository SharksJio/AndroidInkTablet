package com.sharks.androidinktablet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.Slider
import com.sharks.androidinktablet.drawing.DrawingView
import com.sharks.androidinktablet.drawing.Stroke
import com.sharks.androidinktablet.drawing.Tool
import com.sharks.androidinktablet.drawing.ToolType
import com.sharks.androidinktablet.ui.ColorPickerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

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

    // PDF Controls
    private lateinit var pdfControls: MaterialCardView
    private lateinit var btnPrevPage: ImageButton
    private lateinit var btnNextPage: ImageButton
    private lateinit var tvPageNumber: TextView
    
    private var currentTool = ToolType.PEN
    private var currentColor = Color.BLACK
    private var currentSize = 5f
    private var pressureSensitivity = 1.0f

    // PDF State
    private var pdfRenderer: PdfRenderer? = null
    private var currentPdfPage: PdfRenderer.Page? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var loadPageJob: kotlinx.coroutines.Job? = null
    private var currentPageIndex = 0
    private var totalPages = 0
    private val pageStrokes = mutableMapOf<Int, List<Stroke>>()

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                drawingView.insertImage(uri)
            }
        }
    }

    private val pdfPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                openPdf(uri)
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
        setupPdfControls()
        
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

        pdfControls = findViewById(R.id.pdfControls)
        btnPrevPage = findViewById(R.id.btnPrevPage)
        btnNextPage = findViewById(R.id.btnNextPage)
        tvPageNumber = findViewById(R.id.tvPageNumber)
    }

    private fun setupDrawingView() {
        drawingView = DrawingView(this)
        drawingView.setOnStrokeChangedListener {
            updateUndoRedoButtons()
        }
        
        val canvasContainer = findViewById<android.widget.FrameLayout>(R.id.canvasContainer)
        canvasContainer.addView(drawingView)

        drawingView.onRequestPdfRefresh = {
            if (pdfRenderer != null) {
                showPage(currentPageIndex)
            }
        }
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

    private fun setupPdfControls() {
        btnPrevPage.setOnClickListener {
            if (currentPageIndex > 0) {
                showPage(currentPageIndex - 1)
            }
        }

        btnNextPage.setOnClickListener {
            if (currentPageIndex < totalPages - 1) {
                showPage(currentPageIndex + 1)
            }
        }
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
        val colorPickerDialog = ColorPickerDialog()
        colorPickerDialog.setInitialColor(currentColor)
        colorPickerDialog.setOnColorSelectedListener { color ->
            currentColor = color
            updateCurrentTool()
            // Update color picker button background to show current color
            btnColorPicker.setColorFilter(currentColor)
        }
        colorPickerDialog.show(supportFragmentManager, "ColorPickerDialog")
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

    private fun handlePdfSelection() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        pdfPickerLauncher.launch(intent)
    }

    private fun openPdf(uri: Uri) {
        try {
            contentResolver.openFileDescriptor(uri, "r")?.let { descriptor ->
                fileDescriptor = descriptor
                pdfRenderer = PdfRenderer(descriptor)
                totalPages = pdfRenderer?.pageCount ?: 0

                if (totalPages > 0) {
                    pageStrokes.clear()
                    // If there are existing strokes on canvas, clear them or save them as non-pdf page?
                    // For now, let's just clear.
                    drawingView.clearCanvas()

                    pdfControls.visibility = View.VISIBLE
                    showPage(0)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Error opening PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPage(index: Int) {
        if (pdfRenderer == null || index < 0 || index >= totalPages) return

        // Save strokes of current page
        pageStrokes[currentPageIndex] = drawingView.getStrokes()

        // Disable buttons to prevent race conditions
        btnPrevPage.isEnabled = false
        btnNextPage.isEnabled = false

        loadPageJob?.cancel()
        loadPageJob = lifecycleScope.launch(Dispatchers.Main) {
            // Perform rendering with synchronized access
            val bitmap = withContext(Dispatchers.IO) {
                synchronized(this@MainActivity) {
                    // Close current page first if open
                    currentPdfPage?.close()
                    currentPdfPage = null

                    try {
                        currentPdfPage = pdfRenderer?.openPage(index)

                        currentPdfPage?.let { page ->
                            val viewWidth = drawingView.width
                            val viewHeight = drawingView.height

                            if (viewWidth > 0 && viewHeight > 0) {
                                val bmp = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888)

                                val scale = kotlin.math.min(
                                    viewWidth.toFloat() / page.width,
                                    viewHeight.toFloat() / page.height
                                )

                                val matrix = android.graphics.Matrix()
                                matrix.setScale(scale, scale)
                                val offsetX = (viewWidth - page.width * scale) / 2
                                val offsetY = (viewHeight - page.height * scale) / 2
                                matrix.postTranslate(offsetX, offsetY)

                                page.render(bmp, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                return@let bmp
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    null
                }
            }

            bitmap?.let {
                drawingView.setPdfBitmap(it)
            }

            currentPageIndex = index
            tvPageNumber.text = "${currentPageIndex + 1} / $totalPages"

            // Load strokes for new page
            drawingView.setStrokes(pageStrokes[index] ?: emptyList())

            // Update buttons
            btnPrevPage.isEnabled = currentPageIndex > 0
            btnNextPage.isEnabled = currentPageIndex < totalPages - 1

            // Reset undo/redo buttons
            updateUndoRedoButtons()
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
            R.id.action_open_pdf -> {
                handlePdfSelection()
                true
            }
            R.id.action_new -> {
                drawingView.clearCanvas()
                updateUndoRedoButtons()
                // Close PDF if open
                closePdf()
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

    private fun closePdf() {
        currentPdfPage?.close()
        currentPdfPage = null
        pdfRenderer?.close()
        pdfRenderer = null
        fileDescriptor?.close()
        fileDescriptor = null
        pdfControls.visibility = View.GONE
        drawingView.setPdfBitmap(null)
        pageStrokes.clear()
        currentPageIndex = 0
    }

    override fun onDestroy() {
        super.onDestroy()
        closePdf()
    }
}