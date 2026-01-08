package com.sharks.androidinktablet.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.sharks.androidinktablet.model.DrawingFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Repository for handling file operations (save, load, export)
 */
class FileRepository(private val context: Context) {
    
    private val drawingsDir: File by lazy {
        File(context.filesDir, "drawings").apply {
            if (!exists()) mkdirs()
        }
    }
    
    /**
     * Save a bitmap as a drawing file
     */
    suspend fun saveDrawing(bitmap: Bitmap, fileName: String): Result<DrawingFile> = withContext(Dispatchers.IO) {
        try {
            val file = File(drawingsDir, "$fileName.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Result.success(DrawingFile(file, fileName, file.lastModified()))
        } catch (e: IOException) {
            Result.failure(e)
        }
    }
    
    /**
     * Load a drawing file
     */
    suspend fun loadDrawing(fileName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val file = File(drawingsDir, "$fileName.png")
            if (file.exists()) {
                Result.success(file)
            } else {
                Result.failure(IOException("File not found"))
            }
        } catch (e: IOException) {
            Result.failure(e)
        }
    }
    
    /**
     * List all saved drawings
     */
    suspend fun listDrawings(): Result<List<DrawingFile>> = withContext(Dispatchers.IO) {
        try {
            val files = drawingsDir.listFiles()
                ?.filter { it.extension == "png" }
                ?.map { DrawingFile(it, it.nameWithoutExtension, it.lastModified()) }
                ?.sortedByDescending { it.lastModified }
                ?: emptyList()
            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Export bitmap as PNG to downloads
     */
    suspend fun exportAsPNG(bitmap: Bitmap, fileName: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, "$fileName.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Export bitmap as JPEG to downloads
     */
    suspend fun exportAsJPEG(bitmap: Bitmap, fileName: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, "$fileName.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Export bitmap as PDF to downloads
     */
    suspend fun exportAsPDF(bitmap: Bitmap, fileName: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, "$fileName.pdf")
            
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
            val page = document.startPage(pageInfo)
            
            val canvas = page.canvas
            val paint = Paint().apply {
                color = Color.WHITE
            }
            canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            
            document.finishPage(page)
            
            FileOutputStream(file).use { out ->
                document.writeTo(out)
            }
            document.close()
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete a drawing file
     */
    suspend fun deleteDrawing(fileName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val file = File(drawingsDir, "$fileName.png")
            val deleted = file.delete()
            Result.success(deleted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
