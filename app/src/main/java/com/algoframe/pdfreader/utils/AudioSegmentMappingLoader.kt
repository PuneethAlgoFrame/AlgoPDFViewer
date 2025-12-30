package com.algoframe.pdfreader.utils

import android.content.Context
import android.util.Log
import com.algoframe.pdfreader.data.AudioSegmentMapping
import com.algoframe.pdfreader.data.PageSegments
import com.algoframe.pdfreader.data.SegmentMapping
import org.json.JSONObject
import java.io.File
import java.io.InputStream

/**
 * Loads audio segment mappings from JSON files
 * 
 * Looks for mapping files in:
 * 1. Assets folder: assets/audio_segments/{pdfFileName}.json
 * 2. Cache directory: cache/audio_segments/{pdfFileName}.json
 */
class AudioSegmentMappingLoader(private val context: Context) {
    
    /**
     * Load mapping for a specific PDF file
     * @param pdfPath Full path to the PDF file
     * @return AudioSegmentMapping if found, null otherwise
     */
    fun loadMapping(pdfPath: String): AudioSegmentMapping? {
        val pdfFile = File(pdfPath)
        val pdfFileName = pdfFile.name
        
        Log.d("AudioSegmentMappingLoader", "Loading mapping for PDF: $pdfFileName")
        
        // Try assets first
        try {
            val assetsPath = "audio_segments/$pdfFileName.json"
            context.assets.open(assetsPath).use { inputStream ->
                val mapping = parseMapping(inputStream, pdfFileName)
                if (mapping != null) {
                    Log.d("AudioSegmentMappingLoader", "Found mapping in assets: $assetsPath")
                    return mapping
                }
            }
        } catch (_: Exception) {
            // File not found in assets, try cache
            Log.d("AudioSegmentMappingLoader", "Mapping not found in assets, trying cache...")
        }
        
        // Try cache directory
        try {
            val cacheDir = File(context.cacheDir, "audio_segments")
            cacheDir.mkdirs()
            val mappingFile = File(cacheDir, "$pdfFileName.json")
            if (mappingFile.exists()) {
                mappingFile.inputStream().use { inputStream ->
                    val mapping = parseMapping(inputStream, pdfFileName)
                    if (mapping != null) {
                        Log.d("AudioSegmentMappingLoader", "Found mapping in cache: ${mappingFile.absolutePath}")
                        return mapping
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudioSegmentMappingLoader", "Error loading mapping from cache", e)
        }
        
        Log.d("AudioSegmentMappingLoader", "No mapping found for $pdfFileName")
        return null
    }
    
    /**
     * Parse JSON mapping from input stream
     */
    private fun parseMapping(inputStream: InputStream, pdfFileName: String): AudioSegmentMapping? {
        try {
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            
            // Get mapping for this PDF file
            val pdfMapping = jsonObject.optJSONObject(pdfFileName)
                ?: jsonObject.optJSONObject(File(pdfFileName).nameWithoutExtension)
            
            if (pdfMapping == null) {
                Log.w("AudioSegmentMappingLoader", "No mapping found for $pdfFileName in JSON")
                return null
            }
            
            val pagesArray = pdfMapping.getJSONArray("pages")
            val pages = mutableListOf<PageSegments>()
            
            for (i in 0 until pagesArray.length()) {
                val pageObj = pagesArray.getJSONObject(i)
                val pageNumber = pageObj.getInt("pageNumber")
                val segmentsArray = pageObj.getJSONArray("segments")
                val segments = mutableListOf<SegmentMapping>()
                
                for (j in 0 until segmentsArray.length()) {
                    val segmentObj = segmentsArray.getJSONObject(j)
                    val rectArray = segmentObj.getJSONArray("rect")
                    val rect = FloatArray(4) {
                        rectArray.getDouble(it).toFloat()
                    }
                    val startTime = segmentObj.getLong("startTime")
                    val endTime = segmentObj.getLong("endTime")
                    
                    segments.add(SegmentMapping(rect, startTime, endTime))
                }
                
                pages.add(PageSegments(pageNumber, segments))
            }
            
            Log.d("AudioSegmentMappingLoader", "Parsed mapping: ${pages.size} pages, ${pages.sumOf { it.segments.size }} total segments")
            return AudioSegmentMapping(pdfFileName, pages)
            
        } catch (e: Exception) {
            Log.e("AudioSegmentMappingLoader", "Error parsing mapping JSON", e)
            return null
        }
    }
}

