package com.algoframe.pdfreader.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.File

data class RecentPdf(
    val path: String,
    val name: String,
    val lastOpened: Long
)

class RecentPdfManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("recent_pdfs", Context.MODE_PRIVATE)
    private val maxRecentPdfs = 20
    
    /**
     * Add a PDF to the recent list
     */
    fun addRecentPdf(pdfPath: String) {
        try {
            val file = File(pdfPath)
            if (!file.exists()) {
                Log.w("RecentPdfManager", "PDF file does not exist: $pdfPath")
                return
            }
            
            val name = file.name
            val currentTime = System.currentTimeMillis()
            
            // Get existing recent PDFs
            val recentPdfs = getRecentPdfs().toMutableList()
            
            // Remove if already exists
            recentPdfs.removeAll { it.path == pdfPath }
            
            // Add to the beginning
            recentPdfs.add(0, RecentPdf(pdfPath, name, currentTime))
            
            // Keep only the most recent ones
            val trimmed = recentPdfs.take(maxRecentPdfs)
            
            // Save to SharedPreferences
            val editor = prefs.edit()
            editor.clear()
            trimmed.forEachIndexed { index, pdf ->
                editor.putString("path_$index", pdf.path)
                editor.putString("name_$index", pdf.name)
                editor.putLong("time_$index", pdf.lastOpened)
            }
            editor.putInt("count", trimmed.size)
            editor.apply()
            
            Log.d("RecentPdfManager", "Added PDF to recent list: $name")
        } catch (e: Exception) {
            Log.e("RecentPdfManager", "Error adding recent PDF", e)
        }
    }
    
    /**
     * Get list of recent PDFs
     */
    fun getRecentPdfs(): List<RecentPdf> {
        val recentPdfs = mutableListOf<RecentPdf>()
        try {
            val count = prefs.getInt("count", 0)
            for (i in 0 until count) {
                val path = prefs.getString("path_$i", null)
                val name = prefs.getString("name_$i", null)
                val time = prefs.getLong("time_$i", 0L)
                
                if (path != null && name != null) {
                    // Verify file still exists
                    val file = File(path)
                    if (file.exists()) {
                        recentPdfs.add(RecentPdf(path, name, time))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RecentPdfManager", "Error getting recent PDFs", e)
        }
        return recentPdfs.sortedByDescending { it.lastOpened }
    }
    
    /**
     * Get PDF files from system using MediaStore (for Android 10+)
     */
    fun getSystemPdfFiles(): List<File> {
        val pdfFiles = mutableListOf<File>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use MediaStore for Android 10+
            // Note: On Android 10+, direct file path access is restricted
            // This query may return limited results, but recent PDFs will still work
            try {
                val projection = arrayOf(
                    MediaStore.Files.FileColumns.DATA,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.DATE_MODIFIED
                )
                
                val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
                val selectionArgs = arrayOf("application/pdf")
                val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                
                val cursor = context.contentResolver.query(
                    MediaStore.Files.getContentUri("external"),
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )
                
                cursor?.use {
                    val dataColumnIndex = it.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                    val nameColumnIndex = it.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    
                    while (it.moveToNext()) {
                        // Try to get file path
                        val path = if (dataColumnIndex >= 0) {
                            it.getString(dataColumnIndex)
                        } else {
                            null
                        }
                        
                        // On Android 10+, DATA might be null, so we skip those entries
                        // The recent PDFs list (from SharedPreferences) will be the primary source
                        if (path != null) {
                            val file = File(path)
                            if (file.exists() && file.isFile && file.name.endsWith(".pdf", ignoreCase = true)) {
                                pdfFiles.add(file)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RecentPdfManager", "Error querying MediaStore", e)
                // This is expected on some devices - recent PDFs will still work
            }
        } else {
            // Fallback to direct file access for older Android versions
            try {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                if (downloadsDir.exists() && downloadsDir.isDirectory) {
                    downloadsDir.listFiles()?.forEach { file ->
                        if (file.isFile && file.name.endsWith(".pdf", ignoreCase = true)) {
                            pdfFiles.add(file)
                        }
                    }
                }
                
                val documentsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOCUMENTS
                )
                if (documentsDir.exists() && documentsDir.isDirectory) {
                    documentsDir.listFiles()?.forEach { file ->
                        if (file.isFile && file.name.endsWith(".pdf", ignoreCase = true)) {
                            pdfFiles.add(file)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RecentPdfManager", "Error accessing directories", e)
            }
        }
        
        return pdfFiles.sortedByDescending { it.lastModified() }
    }
    
    /**
     * Clear all recent PDFs
     */
    fun clearRecentPdfs() {
        prefs.edit().clear().apply()
    }
}

