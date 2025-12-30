package com.algoframe.pdfreader.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.algoframe.pdfreader.data.AnnotationRect
import com.algoframe.pdfreader.data.MediaFile
import com.algoframe.pdfreader.data.MediaType
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Extracts embedded media files (audio, video, images) from PDF documents.
 */
class PdfMediaExtractor(private val context: Context) {
    
    /**
     * Extract media files for a specific page (0-indexed)
     */
    suspend fun extractMediaFilesForPage(pdfPath: String, pageIndex: Int): List<MediaFile> = withContext(Dispatchers.IO) {
        // Load external audio segment mapping if available
        val mappingLoader = AudioSegmentMappingLoader(context)
        val segmentMapping = mappingLoader.loadMapping(pdfPath)
        
        if (segmentMapping != null) {
            android.util.Log.d("PdfMediaExtractor", "Loaded external segment mapping for ${segmentMapping.pdfFileName}")
        }
        val mediaFiles = mutableListOf<MediaFile>()
        val extractedPaths = mutableSetOf<String>() // Track extracted file paths to avoid duplicates
        
        android.util.Log.d("PdfMediaExtractor", "Starting media extraction for page ${pageIndex + 1} from: $pdfPath")
        
        try {
            val pdfFile = File(pdfPath)
            if (!pdfFile.exists()) {
                android.util.Log.e("PdfMediaExtractor", "PDF file does not exist: $pdfPath")
                return@withContext mediaFiles
            }
            
            android.util.Log.d("PdfMediaExtractor", "Loading PDF document...")
            val document = PDDocument.load(pdfFile)
            val cacheDir = File(context.cacheDir, "pdf_media")
            cacheDir.mkdirs()
            android.util.Log.d("PdfMediaExtractor", "Cache directory: ${cacheDir.absolutePath}")
            
            try {
                val pageNumber = pageIndex + 1 // Convert to 1-indexed
                val pages = document.pages
                var pageCount = 0
                pages.forEach { _ -> pageCount++ }
                
                if (pageIndex !in 0..<pageCount) {
                    android.util.Log.e("PdfMediaExtractor", "Page index $pageIndex is out of range (0-${pageCount - 1})")
                    return@withContext mediaFiles
                }
                
                // Helper function to add media file with duplicate detection
                fun addMediaFileIfNotDuplicate(mediaFile: MediaFile): Boolean {
                    val file = File(mediaFile.path)
                    if (!file.exists() || file.length() == 0L) {
                        return false
                    }
                    
                    // If this media file has an annotation rectangle, always allow it
                    if (mediaFile.annotationRect != null) {
                        val existingIndex = mediaFiles.indexOfFirst { 
                            it.path == mediaFile.path && 
                            it.annotationRect?.let { rect ->
                                mediaFile.annotationRect?.let { newRect ->
                                    rect.left == newRect.left && 
                                    rect.top == newRect.top && 
                                    rect.right == newRect.right && 
                                    rect.bottom == newRect.bottom
                                } ?: false
                            } ?: (it.annotationRect == null && mediaFile.annotationRect == null)
                        }
                        
                        if (existingIndex >= 0) {
                            val existing = mediaFiles[existingIndex]
                            if (existing.startTime == null && mediaFile.startTime != null) {
                                android.util.Log.d("PdfMediaExtractor", "Updating existing media file with time range: ${mediaFile.path}")
                                mediaFiles[existingIndex] = existing.copy(
                                    startTime = mediaFile.startTime,
                                    endTime = mediaFile.endTime
                                )
                            }
                            return false
                        }
                        
                        extractedPaths.add(mediaFile.path)
                        mediaFiles.add(mediaFile)
                        android.util.Log.d("PdfMediaExtractor", "Added media file with annotation rect: ${mediaFile.name}, path: ${mediaFile.path}, startTime=${mediaFile.startTime}, endTime=${mediaFile.endTime}")
                        return true
                    }
                    
                    // For files without annotation rectangles, check for duplicates by path
                    if (extractedPaths.contains(mediaFile.path)) {
                        val existingIndex = mediaFiles.indexOfFirst { it.path == mediaFile.path }
                        if (existingIndex >= 0 && mediaFile.annotationRect != null && mediaFiles[existingIndex].annotationRect == null) {
                            android.util.Log.d("PdfMediaExtractor", "Updating existing media file with annotation rectangle: ${mediaFile.path}")
                            mediaFiles[existingIndex] = mediaFiles[existingIndex].copy(
                                annotationRect = mediaFile.annotationRect,
                                pageNumber = mediaFile.pageNumber ?: mediaFiles[existingIndex].pageNumber
                            )
                            return true
                        }
                        android.util.Log.d("PdfMediaExtractor", "Skipping duplicate path: ${mediaFile.path}")
                        return false
                    }
                    
                    val fileSize = file.length()
                    val existingIndex = mediaFiles.indexOfFirst { 
                        val existingSize = File(it.path).length()
                        existingSize == fileSize &&
                        it.type == mediaFile.type &&
                        File(it.path).exists()
                    }
                    if (existingIndex >= 0) {
                        try {
                            val bufferSize = 1024
                            val existingBytes = ByteArray(bufferSize)
                            val newBytes = ByteArray(bufferSize)
                            val existingRead = File(mediaFiles[existingIndex].path).inputStream().use { it.read(existingBytes) }
                            val newRead = file.inputStream().use { it.read(newBytes) }
                            if (existingRead == newRead && existingRead > 0) {
                                var matches = true
                                for (i in 0 until existingRead) {
                                    if (existingBytes[i] != newBytes[i]) {
                                        matches = false
                                        break
                                    }
                                }
                                if (matches) {
                                    if (mediaFile.annotationRect != null && mediaFiles[existingIndex].annotationRect == null) {
                                        android.util.Log.d("PdfMediaExtractor", "Updating duplicate media file with annotation rectangle: ${mediaFile.path}")
                                        mediaFiles[existingIndex] = mediaFiles[existingIndex].copy(
                                            annotationRect = mediaFile.annotationRect,
                                            pageNumber = mediaFile.pageNumber ?: mediaFiles[existingIndex].pageNumber
                                        )
                                        return true
                                    }
                                    android.util.Log.d("PdfMediaExtractor", "Skipping duplicate content: ${mediaFile.path}")
                                    return false
                                }
                            }
                        } catch (_: Exception) {
                            // If comparison fails, assume different files
                        }
                    }
                    
                    extractedPaths.add(mediaFile.path)
                    mediaFiles.add(mediaFile)
                    android.util.Log.d("PdfMediaExtractor", "Added media file: ${mediaFile.name}, type: ${mediaFile.type}, path: ${mediaFile.path}, size: $fileSize, hasRect: ${mediaFile.annotationRect != null}")
                    return true
                }
                
                // Get the specific page
                var currentPageIndex = 0
                var targetPage: PDPage? = null
                pages.forEach { page ->
                    if (currentPageIndex == pageIndex) {
                        targetPage = page
                    }
                    currentPageIndex++
                }
                
                if (targetPage == null) {
                    android.util.Log.e("PdfMediaExtractor", "Could not find page at index $pageIndex")
                    return@withContext mediaFiles
                }
                
                // Extract media from the specific page only
                android.util.Log.d("PdfMediaExtractor", "Extracting media from page ${pageNumber}...")
                extractMediaFromPage(document, targetPage!!, pageNumber, cacheDir, mediaFiles, ::addMediaFileIfNotDuplicate)
                android.util.Log.d("PdfMediaExtractor", "Page ${pageNumber}: Found ${mediaFiles.size} media files")
                
            } finally {
                document.close()
            }
        } catch (e: Exception) {
            android.util.Log.e("PdfMediaExtractor", "Error extracting media for page ${pageIndex + 1}", e)
            e.printStackTrace()
        }
        
        android.util.Log.d("PdfMediaExtractor", "Page ${pageIndex + 1} media extraction completed. Found ${mediaFiles.size} files.")
        mediaFiles
    }
    
    /**
     * Extract media from a PDF page
     */
    private fun extractMediaFromPage(
        document: PDDocument,
        page: PDPage,
        pageNumber: Int,
        cacheDir: File,
        mediaFiles: MutableList<MediaFile>,
        addMediaFile: (MediaFile) -> Boolean
    ) {
        try {
            android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Starting extraction")
            
            val resources = page.resources
            if (resources != null) {
                android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Has resources, extracting XObjects")
                // Extract XObjects (images, embedded media)
                extractXObjects(document, resources, pageNumber, cacheDir, mediaFiles, addMediaFile)
            } else {
                android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: No resources found")
            }
            
            // Extract from annotations (RichMedia, Screen annotations often contain audio/video)
            android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Extracting annotations")
            extractAnnotationMedia(document, page, pageNumber, cacheDir, mediaFiles, addMediaFile)
            
            // Also check page dictionary directly for any streams
            val pageDict = page.cosObject
            if (pageDict != null && pageDict is COSDictionary) {
                android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Checking page dictionary for streams")
                extractStreamsFromDict(pageDict, document, cacheDir, mediaFiles, "Page_$pageNumber", 0, addMediaFile)
                
                // Check page content streams (Contents can be a stream or array of streams)
                val contents = pageDict.getDictionaryObject(COSName.getPDFName("Contents"))
                if (contents != null) {
                    android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Found Contents, checking for media streams...")
                    when {
                        contents is PDStream -> {
                            android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Contents is a PDStream")
                            checkAndExtractStream(contents, "Contents", "Page_$pageNumber", cacheDir, mediaFiles, addMediaFile)
                        }
                        contents is com.tom_roush.pdfbox.cos.COSStream -> {
                            android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Contents is a COSStream, converting...")
                            try {
                                val stream = PDStream(contents)
                                checkAndExtractStream(stream, "Contents", "Page_$pageNumber", cacheDir, mediaFiles, addMediaFile)
                            } catch (e: Exception) {
                                android.util.Log.e("PdfMediaExtractor", "Failed to convert Contents COSStream", e)
                            }
                        }
                        contents is com.tom_roush.pdfbox.cos.COSArray -> {
                            android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Contents is an array, checking each stream...")
                            val array = contents as com.tom_roush.pdfbox.cos.COSArray
                            array.forEachIndexed { index, item ->
                                when {
                                    item is PDStream -> checkAndExtractStream(item, "Contents_$index", "Page_$pageNumber", cacheDir, mediaFiles, addMediaFile)
                                    item is com.tom_roush.pdfbox.cos.COSStream -> {
                                        try {
                                            val stream = PDStream(item)
                                            checkAndExtractStream(stream, "Contents_$index", "Page_$pageNumber", cacheDir, mediaFiles, addMediaFile)
                                        } catch (e: Exception) {
                                            android.util.Log.e("PdfMediaExtractor", "Failed to convert Contents[$index] COSStream", e)
                                        }
                                    }
                                    item is com.tom_roush.pdfbox.cos.COSObject -> {
                                        try {
                                            val cosObj = item as com.tom_roush.pdfbox.cos.COSObject
                                            val base = cosObj.getObject()
                                            when {
                                                base is PDStream -> checkAndExtractStream(base, "Contents_$index", "Page_$pageNumber", cacheDir, mediaFiles, addMediaFile)
                                                base is com.tom_roush.pdfbox.cos.COSStream -> {
                                                    val stream = PDStream(base)
                                                    checkAndExtractStream(stream, "Contents_$index", "Page_$pageNumber", cacheDir, mediaFiles, addMediaFile)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("PdfMediaExtractor", "Error unwrapping Contents[$index] COSObject", e)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("PdfMediaExtractor", "Error extracting media from page $pageNumber", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Extract XObjects from page resources
     */
    private fun extractXObjects(
        document: PDDocument,
        resources: com.tom_roush.pdfbox.pdmodel.PDResources,
        pageNumber: Int,
        cacheDir: File,
        mediaFiles: MutableList<MediaFile>,
        addMediaFile: (MediaFile) -> Boolean
    ) {
        try {
            val cosResources = resources.cosObject
            if (cosResources != null && cosResources is COSDictionary) {
                val xObjectBase = cosResources.getItem(COSName.getPDFName("XObject"))
                if (xObjectBase != null && xObjectBase is COSDictionary) {
                    val xObjectDict = xObjectBase as COSDictionary
                    val keys = xObjectDict.keySet()
                    android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Found ${keys.size} XObjects")
                    var index = 0
                    
                    keys.forEach { nameObj ->
                        try {
                            val name = nameObj as? COSName ?: return@forEach
                            val nameStr = name.name
                            val xObjectItem = xObjectDict.getItem(name)
                            
                            android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Processing XObject: $nameStr")
                            
                            if (xObjectItem != null) {
                                // Log the type of XObject
                                android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: XObject $nameStr type: ${xObjectItem.javaClass.simpleName}")
                                
                                // Unwrap COSObject if needed
                                val unwrappedItem = if (xObjectItem is com.tom_roush.pdfbox.cos.COSObject) {
                                    val cosObj = xObjectItem as com.tom_roush.pdfbox.cos.COSObject
                                    val base = cosObj.getObject()
                                    android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Unwrapped COSObject, base type: ${base?.javaClass?.simpleName}")
                                    base
                                } else {
                                    xObjectItem
                                }
                                
                                // First, try to create PDImageXObject if it's an image
                                val imageXObject = try {
                                    if (unwrappedItem is com.tom_roush.pdfbox.cos.COSStream || unwrappedItem is COSDictionary) {
                                        com.tom_roush.pdfbox.pdmodel.graphics.PDXObject.createXObject(unwrappedItem, resources) as? PDImageXObject
                                    } else {
                                        null
                                    }
                                } catch (e: Exception) {
                                    null
                                }
                                
                                if (imageXObject != null) {
                                    // Extract image
                                    android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: XObject $nameStr is an image")
                                    val imageFile = File(cacheDir, "image_${pageNumber}_${nameStr}_$index.png")
                                    extractImage(imageXObject, imageFile)
                                    if (imageFile.exists() && imageFile.length() > 0) {
                                        mediaFiles.add(
                                            MediaFile(
                                                name = "image_${pageNumber}_$nameStr",
                                                path = imageFile.absolutePath,
                                                type = MediaType.IMAGE,
                                                pageNumber = pageNumber
                                            )
                                        )
                                        android.util.Log.d("PdfMediaExtractor", "Successfully extracted image_${nameStr}_$index.png (${imageFile.length()} bytes)")
                                    }
                                } else {
                                    // Not an image, check if it's a stream (for audio/video)
                                    val stream = when {
                                        unwrappedItem is PDStream -> {
                                            android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: XObject $nameStr is already a PDStream")
                                            unwrappedItem as PDStream
                                        }
                                        unwrappedItem is com.tom_roush.pdfbox.cos.COSStream -> {
                                            android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: XObject $nameStr is a COSStream, converting to PDStream")
                                            try {
                                                val cosStream = unwrappedItem as com.tom_roush.pdfbox.cos.COSStream
                                                PDStream(cosStream)
                                            } catch (e: Exception) {
                                                android.util.Log.e("PdfMediaExtractor", "Page $pageNumber: Failed to convert COSStream to PDStream", e)
                                                null
                                            }
                                        }
                                        else -> null
                                    }
                                    
                                    if (stream != null) {
                                        val streamDict = stream.cosObject
                                        if (streamDict is COSDictionary) {
                                            val subtype = streamDict.getNameAsString(COSName.getPDFName("Subtype")) ?: ""
                                            val length = streamDict.getInt(COSName.getPDFName("Length"), 0)
                                            val filter = streamDict.getNameAsString(COSName.getPDFName("Filter")) ?: ""
                                            android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Stream $nameStr - Subtype: $subtype, Length: $length, Filter: $filter")
                                            
                                            // Check if it's a Form XObject that might contain media
                                            if (subtype == "Form" || nameStr.startsWith("Fm")) {
                                                android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Form XObject detected, checking for nested streams...")
                                                // Form XObjects can contain nested streams with media
                                                extractStreamsFromDict(streamDict, document, cacheDir, mediaFiles, "Form_$nameStr", 0, addMediaFile)
                                                
                                                // Also check the Form XObject stream itself for media content
                                                // Some PDFs embed media directly in Form XObject streams
                                                // Lower threshold to catch smaller media files
                                                if (length > 100) {
                                                    android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Checking Form XObject stream $nameStr ($length bytes) for media content...")
                                                    val formMediaType = determineMediaTypeFromStream(stream, nameStr)
                                                    if (formMediaType != null && formMediaType != MediaType.IMAGE) {
                                                        android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Form XObject $nameStr contains ${formMediaType.name} media!")
                                                        tryExtractStream(stream, "Form_$nameStr", cacheDir, mediaFiles, pageNumber, addMediaFile)
                                                    }
                                                }
                                            }
                                            
                                            // IMPORTANT: Check ALL streams for audio/video, even if marked as Image or Form
                                            // Some PDFs mislabel media streams or embed them in unexpected places
                                            // Check all non-image streams, or large image streams that might be mislabeled
                                            if (subtype != "Image" || length > 5000) {
                                                android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Checking stream $nameStr (subtype=$subtype, length=$length) for audio/video signatures...")
                                                
                                                // Always check stream content first (most reliable)
                                                var mediaType = determineMediaTypeFromStream(stream, nameStr)
                                                
                                                // If not detected by content, check metadata
                                                if (mediaType == null) {
                                                    mediaType = determineMediaType(subtype, nameStr)
                                                }
                                                
                                                if (mediaType != null && mediaType != MediaType.IMAGE) {
                                                    android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Stream $nameStr detected as ${mediaType.name} ($length bytes), attempting extraction...")
                                                    tryExtractStream(stream, nameStr, cacheDir, mediaFiles, pageNumber, addMediaFile)
                                                } else if (length > 5000 && subtype != "Image" && subtype != "Form") {
                                                    // For medium-to-large non-image, non-form streams, check content anyway
                                                    android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Checking unknown stream $nameStr ($length bytes) for media signatures...")
                                                    val unknownMediaType = determineMediaTypeFromStream(stream, nameStr)
                                                    if (unknownMediaType != null && unknownMediaType != MediaType.IMAGE) {
                                                        android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Unknown stream $nameStr detected as ${unknownMediaType.name}, extracting...")
                                                        tryExtractStream(stream, nameStr, cacheDir, mediaFiles, pageNumber, addMediaFile)
                                                    }
                                                }
                                            }
                                        } else {
                                            // Even if we can't get stream dict, try extraction for large streams
                                            android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Stream $nameStr has no dict, attempting extraction anyway...")
                                            tryExtractStream(stream, nameStr, cacheDir, mediaFiles, pageNumber, addMediaFile)
                                        }
                                    } else if (unwrappedItem is COSDictionary) {
                                        // XObject might be a dictionary containing a stream
                                        android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: XObject $nameStr is a dictionary, checking for nested streams...")
                                        val dict = unwrappedItem as COSDictionary
                                        // Recursively check dictionary for nested streams
                                        extractStreamsFromDict(dict, document, cacheDir, mediaFiles, "XObject_$nameStr", 0, addMediaFile)
                                    }
                                }
                            }
                            index++
                        } catch (e: Exception) {
                            android.util.Log.e("PdfMediaExtractor", "Error processing XObject on page $pageNumber", e)
                            index++
                        }
                    }
                } else {
                    android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: No XObject dictionary found")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PdfMediaExtractor", "Error extracting XObjects from page $pageNumber", e)
        }
    }
    
    /**
     * Comprehensive stream extraction - extract ALL streams and check if they're media
     */
    private fun extractAllStreams(
        document: PDDocument,
        cacheDir: File,
        mediaFiles: MutableList<MediaFile>,
        addMediaFile: (MediaFile) -> Boolean
    ) {
        try {
            android.util.Log.d("PdfMediaExtractor", "Starting comprehensive stream extraction...")
            val catalog = document.documentCatalog
            val cosDoc = catalog.cosObject
            
            if (cosDoc != null && cosDoc is COSDictionary) {
                // Recursively search for streams starting from document catalog
                extractStreamsFromDict(cosDoc, document, cacheDir, mediaFiles, "root", 0, addMediaFile)
                
                // Also check all pages again for any streams we might have missed
                val pages = document.pages
                pages.forEachIndexed { pageIndex, page ->
                    val pageDict = page.cosObject
                    if (pageDict != null && pageDict is COSDictionary) {
                        android.util.Log.d("PdfMediaExtractor", "Comprehensive: Checking page ${pageIndex + 1} dictionary...")
                        extractStreamsFromDict(pageDict, document, cacheDir, mediaFiles, "comprehensive_Page_${pageIndex + 1}", 0, addMediaFile)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PdfMediaExtractor", "Error in comprehensive stream extraction", e)
        }
    }
    
    /**
     * Check a stream for audio/video content and extract if found
     */
    private fun checkAndExtractStream(
        stream: PDStream,
        keyName: String,
        path: String,
        cacheDir: File,
        mediaFiles: MutableList<MediaFile>,
        addMediaFile: (MediaFile) -> Boolean
    ) {
        try {
            val streamDict = stream.cosObject
            if (streamDict is COSDictionary) {
                val subtype = streamDict.getNameAsString(COSName.getPDFName("Subtype")) ?: ""
                val filter = streamDict.getNameAsString(COSName.getPDFName("Filter")) ?: ""
                val length = streamDict.getInt(COSName.getPDFName("Length"), 0)
                
                android.util.Log.d("PdfMediaExtractor", "Stream $path/$keyName - Subtype: $subtype, Filter: $filter, Length: $length")
                
                // Check ALL streams for audio/video, regardless of size or subtype
                // Some PDFs embed small audio/video files or compress them
                // Lower threshold to catch even small media files (100 bytes minimum)
                if (length > 100) {
                    android.util.Log.d("PdfMediaExtractor", "Checking stream $keyName ($length bytes, subtype=$subtype) for audio/video signatures...")
                    
                    // First check stream content (magic bytes) - most reliable
                    var mediaType = determineMediaTypeFromStream(stream, keyName)
                    
                    // If not detected by content, check metadata
                    if (mediaType == null) {
                        mediaType = determineMediaType(subtype, keyName)
                    }
                    
                    android.util.Log.d("PdfMediaExtractor", "Stream $keyName media type determination: $mediaType")
                    
                    if (mediaType != null && mediaType != MediaType.IMAGE) {
                        val extension = getExtensionForMediaType(mediaType)
                        val mediaFile = File(cacheDir, "${mediaType.name.lowercase()}_stream_${keyName}_${mediaFiles.size}.$extension")
                        android.util.Log.d("PdfMediaExtractor", "Attempting to extract stream $keyName to ${mediaFile.name}")
                        extractStream(stream, mediaFile)
                        
                        if (mediaFile.exists() && mediaFile.length() > 0) {
                            android.util.Log.d("PdfMediaExtractor", "Successfully extracted ${mediaType.name} stream: ${mediaFile.name} (${mediaFile.length()} bytes)")
                            addMediaFile(
                                MediaFile(
                                    name = "${mediaType.name.lowercase()}_stream_$keyName",
                                    path = mediaFile.absolutePath,
                                    type = mediaType,
                                    pageNumber = null
                                )
                            )
                        } else {
                            android.util.Log.w("PdfMediaExtractor", "Failed to extract stream $keyName - file not created or empty")
                        }
                    } else if (length > 5000 && subtype != "Image" && subtype != "Form" && subtype.isNotEmpty()) {
                        // For medium-to-large non-image, non-form streams with unknown subtype, check content
                        android.util.Log.d("PdfMediaExtractor", "Checking unknown stream $keyName ($length bytes, subtype=$subtype) for media signatures...")
                        val unknownMediaType = determineMediaTypeFromStream(stream, keyName)
                        if (unknownMediaType != null && unknownMediaType != MediaType.IMAGE) {
                            val extension = getExtensionForMediaType(unknownMediaType)
                            val mediaFile = File(cacheDir, "${unknownMediaType.name.lowercase()}_stream_${keyName}_${mediaFiles.size}.$extension")
                            extractStream(stream, mediaFile)
                            if (mediaFile.exists() && mediaFile.length() > 0) {
                                android.util.Log.d("PdfMediaExtractor", "Extracted ${unknownMediaType.name} from unknown stream: ${mediaFile.name} (${mediaFile.length()} bytes)")
                                addMediaFile(
                                    MediaFile(
                                        name = "${unknownMediaType.name.lowercase()}_stream_$keyName",
                                        path = mediaFile.absolutePath,
                                        type = unknownMediaType,
                                        pageNumber = null
                                    )
                                )
                            }
                        }
                    }
                }
            } else {
                // Even if no dictionary, try to check the stream content
                android.util.Log.d("PdfMediaExtractor", "Stream $path/$keyName has no dictionary, checking content directly...")
                val mediaType = determineMediaTypeFromStream(stream, keyName)
                if (mediaType != null && mediaType != MediaType.IMAGE) {
                    val extension = getExtensionForMediaType(mediaType)
                    val mediaFile = File(cacheDir, "${mediaType.name.lowercase()}_stream_${keyName}_${mediaFiles.size}.$extension")
                    extractStream(stream, mediaFile)
                    if (mediaFile.exists() && mediaFile.length() > 0) {
                        android.util.Log.d("PdfMediaExtractor", "Successfully extracted ${mediaType.name} stream: ${mediaFile.name} (${mediaFile.length()} bytes)")
                        addMediaFile(
                            MediaFile(
                                name = "${mediaType.name.lowercase()}_stream_$keyName",
                                path = mediaFile.absolutePath,
                                type = mediaType,
                                pageNumber = null
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PdfMediaExtractor", "Error checking stream $path/$keyName", e)
        }
    }
    
    /**
     * Recursively extract streams from a dictionary
     */
    private fun extractStreamsFromDict(
        dict: COSDictionary,
        document: PDDocument,
        cacheDir: File,
        mediaFiles: MutableList<MediaFile>,
        path: String,
        depth: Int,
        addMediaFile: (MediaFile) -> Boolean
    ) {
        if (depth > 5) {
            android.util.Log.d("PdfMediaExtractor", "Reached max depth at $path")
            return // Limit recursion depth
        }
        
        try {
            val keys = dict.keySet()
            android.util.Log.d("PdfMediaExtractor", "Searching dict at $path (depth $depth), found ${keys.size} keys")
            keys.forEach { key ->
                try {
                    val value = dict.getItem(key)
                    val keyName = if (key is COSName) key.name else key.toString()
                    
                    when {
                        value is PDStream -> {
                            val stream = value as PDStream
                            android.util.Log.d("PdfMediaExtractor", "Found PDStream at $path/$keyName")
                            checkAndExtractStream(stream, keyName, path, cacheDir, mediaFiles, addMediaFile)
                        }
                        value is com.tom_roush.pdfbox.cos.COSStream -> {
                            // Convert COSStream to PDStream
                            android.util.Log.d("PdfMediaExtractor", "Found COSStream at $path/$keyName, converting to PDStream...")
                            try {
                                val stream = PDStream(value as com.tom_roush.pdfbox.cos.COSStream)
                                checkAndExtractStream(stream, keyName, path, cacheDir, mediaFiles, addMediaFile)
                            } catch (e: Exception) {
                                android.util.Log.e("PdfMediaExtractor", "Failed to convert COSStream to PDStream at $path/$keyName", e)
                            }
                        }
                        value is com.tom_roush.pdfbox.cos.COSObject -> {
                            // Unwrap COSObject to get the base object
                            android.util.Log.d("PdfMediaExtractor", "Found COSObject at $path/$keyName, unwrapping...")
                            try {
                                val cosObj = value as com.tom_roush.pdfbox.cos.COSObject
                                val base = cosObj.getObject()
                                when {
                                    base is PDStream -> {
                                        android.util.Log.d("PdfMediaExtractor", "COSObject contains PDStream at $path/$keyName")
                                        checkAndExtractStream(base, keyName, path, cacheDir, mediaFiles, addMediaFile)
                                    }
                                    base is com.tom_roush.pdfbox.cos.COSStream -> {
                                        android.util.Log.d("PdfMediaExtractor", "COSObject contains COSStream at $path/$keyName, converting...")
                                        val stream = PDStream(base)
                                        checkAndExtractStream(stream, keyName, path, cacheDir, mediaFiles, addMediaFile)
                                    }
                                    base is COSDictionary -> {
                                        // Recursively check the dictionary
                                        android.util.Log.d("PdfMediaExtractor", "COSObject contains dictionary at $path/$keyName, recursing...")
                                        extractStreamsFromDict(base, document, cacheDir, mediaFiles, "$path/$keyName", depth + 1, addMediaFile)
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("PdfMediaExtractor", "Error unwrapping COSObject at $path/$keyName", e)
                            }
                        }
                        value is COSDictionary -> {
                            val keyName = if (key is COSName) key.name else key.toString()
                            android.util.Log.d("PdfMediaExtractor", "Found nested dict at $path/$keyName, recursing...")
                            extractStreamsFromDict(value, document, cacheDir, mediaFiles, "$path/$keyName", depth + 1, addMediaFile)
                        }
                        value is com.tom_roush.pdfbox.cos.COSArray -> {
                            val array = value as com.tom_roush.pdfbox.cos.COSArray
                            array.forEachIndexed { index, item ->
                                when {
                                    item is COSDictionary -> {
                                        extractStreamsFromDict(item, document, cacheDir, mediaFiles, "$path[$index]", depth + 1, addMediaFile)
                                    }
                                    item is PDStream -> {
                                        android.util.Log.d("PdfMediaExtractor", "Found PDStream in array at $path[$index]")
                                        checkAndExtractStream(item, "array_$index", path, cacheDir, mediaFiles, addMediaFile)
                                    }
                                    item is com.tom_roush.pdfbox.cos.COSStream -> {
                                        android.util.Log.d("PdfMediaExtractor", "Found COSStream in array at $path[$index], converting...")
                                        try {
                                            val stream = PDStream(item)
                                            checkAndExtractStream(stream, "array_$index", path, cacheDir, mediaFiles, addMediaFile)
                                        } catch (e: Exception) {
                                            android.util.Log.e("PdfMediaExtractor", "Failed to convert COSStream in array", e)
                                        }
                                    }
                                    item is com.tom_roush.pdfbox.cos.COSObject -> {
                                        android.util.Log.d("PdfMediaExtractor", "Found COSObject in array at $path[$index], unwrapping...")
                                        try {
                                            val cosObj = item as com.tom_roush.pdfbox.cos.COSObject
                                            val base = cosObj.getObject()
                                            when {
                                                base is PDStream -> checkAndExtractStream(base, "array_$index", path, cacheDir, mediaFiles, addMediaFile)
                                                base is com.tom_roush.pdfbox.cos.COSStream -> {
                                                    val stream = PDStream(base)
                                                    checkAndExtractStream(stream, "array_$index", path, cacheDir, mediaFiles, addMediaFile)
                                                }
                                                base is COSDictionary -> extractStreamsFromDict(base, document, cacheDir, mediaFiles, "$path[$index]", depth + 1, addMediaFile)
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("PdfMediaExtractor", "Error unwrapping COSObject in array", e)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PdfMediaExtractor", "Error processing key in dict", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PdfMediaExtractor", "Error extracting streams from dict", e)
        }
    }
    
    /**
     * Try to extract a stream as media
     */
    private fun tryExtractStream(
        stream: PDStream,
        name: String,
        cacheDir: File,
        mediaFiles: MutableList<MediaFile>,
        pageNumber: Int?,
        addMediaFile: (MediaFile) -> Boolean
    ) {
        try {
            val streamDict = stream.cosObject
            if (streamDict is COSDictionary) {
                val subtype = streamDict.getNameAsString(COSName.getPDFName("Subtype")) ?: ""
                val contentType = streamDict.getNameAsString(COSName.getPDFName("ContentType")) ?: ""
                val length = streamDict.getInt(COSName.getPDFName("Length"), 0)
                
                // Check for media type from metadata first
                var mediaType = determineMediaTypeFromContentType(contentType)
                    ?: determineMediaType(subtype, name)
                
                // If not found in metadata, check stream content (magic bytes)
                if (mediaType == null || mediaType == MediaType.IMAGE) {
                    mediaType = determineMediaTypeFromStream(stream, name)
                }
                
                // Extract if it's audio/video (skip images as they're handled separately)
                // Lower threshold to 100 bytes to catch even very small audio/video clips
                if (mediaType != null && mediaType != MediaType.IMAGE && length > 100) {
                    android.util.Log.d("PdfMediaExtractor", "Extracting ${mediaType.name} stream: $name ($length bytes)")
                    val extension = getExtensionForMediaType(mediaType)
                    val mediaFile = File(cacheDir, "${mediaType.name.lowercase()}_${name}_${mediaFiles.size}.$extension")
                    extractStream(stream, mediaFile)
                    
                    if (mediaFile.exists() && mediaFile.length() > 0) {
                        android.util.Log.d("PdfMediaExtractor", "Successfully extracted ${mediaType.name}: ${mediaFile.name} (${mediaFile.length()} bytes)")
                        addMediaFile(
                            MediaFile(
                                name = "${mediaType.name.lowercase()}_$name",
                                path = mediaFile.absolutePath,
                                type = mediaType,
                                pageNumber = pageNumber
                            )
                        )
                    } else {
                        android.util.Log.w("PdfMediaExtractor", "Failed to extract ${mediaType.name} stream $name - file not created or empty")
                    }
                } else {
                    android.util.Log.d("PdfMediaExtractor", "Stream $name not extracted: type=$mediaType, length=$length")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PdfMediaExtractor", "Error trying to extract stream $name", e)
        }
    }
    
    /**
     * Extract media stream from a RichMedia asset
     */
    private fun extractRichMediaAssetStream(
        stream: PDStream,
        assetName: String,
        pageNumber: Int,
        annotationIndex: Int,
        cacheDir: File,
        mediaFiles: MutableList<MediaFile>,
        addMediaFile: (MediaFile) -> Boolean,
        assetDict: COSDictionary,
        annotationRect: AnnotationRect? = null,
        timeRange: Pair<Long, Long>? = null
    ) {
        try {
            // Check stream length first
            val streamLength = try {
                stream.cosObject.getInt(COSName.getPDFName("Length"), 0)
            } catch (e: Exception) { 0 }
            
            android.util.Log.d("PdfMediaExtractor", "RichMedia asset '$assetName': checking stream (length=$streamLength)")
            
            // Determine media type - check stream content FIRST (most reliable)
            var mediaType = determineMediaTypeFromStream(stream, assetName)
            
            // If not detected by content, check metadata and filename
            if (mediaType == null) {
                // Check FileSpec metadata
                val contentType = assetDict.getString(COSName.getPDFName("Type")) ?: ""
                val subtypeStr = assetDict.getNameAsString(COSName.getPDFName("Subtype")) ?: ""
                val fileName = assetDict.getString(COSName.getPDFName("F")) 
                    ?: assetDict.getString(COSName.getPDFName("UF"))
                    ?: assetName
                
                mediaType = determineMediaTypeFromContentType(contentType)
                    ?: determineMediaType(subtypeStr, fileName)
                    ?: determineMediaType("", fileName) // Try filename-based detection
            }
            
            android.util.Log.d("PdfMediaExtractor", "RichMedia asset '$assetName': detected type=$mediaType, length=$streamLength")
            
            // Extract if it's audio/video (skip images unless they're very large - might be mislabeled)
            if (mediaType != null && mediaType != MediaType.IMAGE && streamLength > 100) {
                val extension = getExtensionForMediaType(mediaType)
                val mediaFile = File(cacheDir, "${mediaType.name.lowercase()}_richmedia_${pageNumber}_${assetName.replace("/", "_").replace("\\", "_")}_$annotationIndex.$extension")
                extractStream(stream, mediaFile)
                
                // Verify file was created and has content
                if (mediaFile.exists() && mediaFile.length() > 0) {
                    android.util.Log.d("PdfMediaExtractor", "Extracted ${mediaType.name} from RichMedia asset '$assetName': ${mediaFile.name} (${mediaFile.length()} bytes)")
                    if (timeRange != null) {
                        android.util.Log.d("PdfMediaExtractor", "  Time range: ${timeRange.first}ms - ${timeRange.second}ms")
                    }
                    addMediaFile(
                        MediaFile(
                            name = "${mediaType.name.lowercase()}_richmedia_${pageNumber}_$assetName",
                            path = mediaFile.absolutePath,
                            type = mediaType,
                            pageNumber = pageNumber,
                            annotationRect = annotationRect,
                            startTime = timeRange?.first,
                            endTime = timeRange?.second
                        )
                    )
                } else {
                    android.util.Log.w("PdfMediaExtractor", "Failed to extract ${mediaType.name} from RichMedia asset '$assetName': file not created or empty")
                }
            } else if (streamLength > 1000) {
                // For large streams that weren't identified, check content anyway
                android.util.Log.d("PdfMediaExtractor", "RichMedia asset '$assetName': large stream ($streamLength bytes) not identified, checking content...")
                val unknownMediaType = determineMediaTypeFromStream(stream, assetName)
                if (unknownMediaType != null && unknownMediaType != MediaType.IMAGE) {
                    val extension = getExtensionForMediaType(unknownMediaType)
                    val mediaFile = File(cacheDir, "${unknownMediaType.name.lowercase()}_richmedia_${pageNumber}_${assetName.replace("/", "_").replace("\\", "_")}_$annotationIndex.$extension")
                    extractStream(stream, mediaFile)
                    if (mediaFile.exists() && mediaFile.length() > 0) {
                        android.util.Log.d("PdfMediaExtractor", "Extracted ${unknownMediaType.name} from RichMedia asset '$assetName' (recheck): ${mediaFile.name} (${mediaFile.length()} bytes)")
                        addMediaFile(
                            MediaFile(
                                name = "${unknownMediaType.name.lowercase()}_richmedia_${pageNumber}_$assetName",
                                path = mediaFile.absolutePath,
                                type = unknownMediaType,
                                pageNumber = pageNumber,
                                annotationRect = annotationRect
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PdfMediaExtractor", "Error extracting stream from RichMedia asset '$assetName'", e)
        }
    }
    
    /**
     * Extract time range from MediaClip dictionary (PDF spec)
     * MediaClip can have:
     * - "C" (clip time): array of [start, end] or dictionary with "S" (start) and "D" (duration) or "E" (end)
     * - "S" (start time): start time in seconds
     * - "D" (duration): duration in seconds
     * - "E" (end time): end time in seconds
     * Returns Pair<startTimeMs, endTimeMs> or null if no time range found
     */
    private fun extractTimeRangeFromMediaClip(mediaClipDict: COSDictionary?): Pair<Long, Long>? {
        if (mediaClipDict == null) return null
        
        try {
            android.util.Log.d("PdfMediaExtractor", "Extracting time range from MediaClip")
            
            // Check for "C" (clip time) - can be array [start, end] or dictionary
            val clipTimeObj = mediaClipDict.getDictionaryObject(COSName.getPDFName("C"))
            if (clipTimeObj != null) {
                when {
                    // Array format: [start, end]
                    clipTimeObj is com.tom_roush.pdfbox.cos.COSArray && clipTimeObj.size() >= 2 -> {
                        val startObj = clipTimeObj.get(0)
                        val endObj = clipTimeObj.get(1)
                        val startSeconds = when {
                            startObj is com.tom_roush.pdfbox.cos.COSNumber -> startObj.floatValue()
                            startObj is com.tom_roush.pdfbox.cos.COSInteger -> startObj.intValue().toFloat()
                            else -> return null
                        }
                        val endSeconds = when {
                            endObj is com.tom_roush.pdfbox.cos.COSNumber -> endObj.floatValue()
                            endObj is com.tom_roush.pdfbox.cos.COSInteger -> endObj.intValue().toFloat()
                            else -> return null
                        }
                        val startMs = (startSeconds * 1000).toLong()
                        val endMs = (endSeconds * 1000).toLong()
                        android.util.Log.d("PdfMediaExtractor", "Found time range from clip array: ${startMs}ms - ${endMs}ms")
                        return Pair(startMs, endMs)
                    }
                    // Dictionary format: { "S": start, "D": duration } or { "S": start, "E": end }
                    clipTimeObj is COSDictionary -> {
                        val clipDict = clipTimeObj as COSDictionary
                        val startObj = clipDict.getDictionaryObject(COSName.getPDFName("S"))
                        val durationObj = clipDict.getDictionaryObject(COSName.getPDFName("D"))
                        val endObj = clipDict.getDictionaryObject(COSName.getPDFName("E"))
                        
                        val startSeconds = when {
                            startObj is com.tom_roush.pdfbox.cos.COSNumber -> startObj.floatValue()
                            startObj is com.tom_roush.pdfbox.cos.COSInteger -> startObj.intValue().toFloat()
                            else -> 0f
                        }
                        
                        val endSeconds = when {
                            endObj != null -> {
                                when {
                                    endObj is com.tom_roush.pdfbox.cos.COSNumber -> endObj.floatValue()
                                    endObj is com.tom_roush.pdfbox.cos.COSInteger -> endObj.intValue().toFloat()
                                    else -> startSeconds
                                }
                            }
                            durationObj != null -> {
                                val duration = when {
                                    durationObj is com.tom_roush.pdfbox.cos.COSNumber -> durationObj.floatValue()
                                    durationObj is com.tom_roush.pdfbox.cos.COSInteger -> durationObj.intValue().toFloat()
                                    else -> 0f
                                }
                                startSeconds + duration
                            }
                            else -> null
                        }
                        
                        if (endSeconds != null) {
                            val startMs = (startSeconds * 1000).toLong()
                            val endMs = (endSeconds * 1000).toLong()
                            android.util.Log.d("PdfMediaExtractor", "Found time range from clip dict: ${startMs}ms - ${endMs}ms")
                            return Pair(startMs, endMs)
                        }
                    }
                }
            }
            
            // Check for direct "S" (start) and "D" (duration) or "E" (end) in MediaClip
            val startObj = mediaClipDict.getDictionaryObject(COSName.getPDFName("S"))
            val durationObj = mediaClipDict.getDictionaryObject(COSName.getPDFName("D"))
            val endObj = mediaClipDict.getDictionaryObject(COSName.getPDFName("E"))
            
            if (startObj != null) {
                val startSeconds = when {
                    startObj is com.tom_roush.pdfbox.cos.COSNumber -> startObj.floatValue()
                    startObj is com.tom_roush.pdfbox.cos.COSInteger -> startObj.intValue().toFloat()
                    else -> return null
                }
                
                val endSeconds = when {
                    endObj != null -> {
                        when {
                            endObj is com.tom_roush.pdfbox.cos.COSNumber -> endObj.floatValue()
                            endObj is com.tom_roush.pdfbox.cos.COSInteger -> endObj.intValue().toFloat()
                            else -> null
                        }
                    }
                    durationObj != null -> {
                        val duration = when {
                            durationObj is com.tom_roush.pdfbox.cos.COSNumber -> durationObj.floatValue()
                            durationObj is com.tom_roush.pdfbox.cos.COSInteger -> durationObj.intValue().toFloat()
                            else -> null
                        }
                        duration?.let { startSeconds + it }
                    }
                    else -> null
                }
                
                if (endSeconds != null) {
                    val startMs = (startSeconds * 1000).toLong()
                    val endMs = (endSeconds * 1000).toLong()
                    android.util.Log.d("PdfMediaExtractor", "Found time range from MediaClip: ${startMs}ms - ${endMs}ms")
                    return Pair(startMs, endMs)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("PdfMediaExtractor", "Error extracting time range from MediaClip", e)
        }
        
        return null
    }
    
    /**
     * Extract annotation rectangle from annotation dictionary
     * PDF annotations have a "Rect" key containing [left, bottom, right, top] array
     */
    private fun extractAnnotationRect(annotationDict: COSDictionary, page: PDPage): AnnotationRect? {
        try {
            val rectArray = annotationDict.getDictionaryObject(COSName.getPDFName("Rect"))
            android.util.Log.d("PdfMediaExtractor", "Extracting annotation rect: rectArray=$rectArray, type=${rectArray?.javaClass?.simpleName}")
            if (rectArray != null && rectArray is com.tom_roush.pdfbox.cos.COSArray && rectArray.size() >= 4) {
                val leftObj = rectArray.get(0)
                val bottomObj = rectArray.get(1)
                val rightObj = rectArray.get(2)
                val topObj = rectArray.get(3)
                
                val left = when (leftObj) {
                    is com.tom_roush.pdfbox.cos.COSNumber -> leftObj.floatValue()
                    is com.tom_roush.pdfbox.cos.COSInteger -> leftObj.intValue().toFloat()
                    else -> 0f
                }
                val bottom = when (bottomObj) {
                    is com.tom_roush.pdfbox.cos.COSNumber -> bottomObj.floatValue()
                    is com.tom_roush.pdfbox.cos.COSInteger -> bottomObj.intValue().toFloat()
                    else -> 0f
                }
                val right = when (rightObj) {
                    is com.tom_roush.pdfbox.cos.COSNumber -> rightObj.floatValue()
                    is com.tom_roush.pdfbox.cos.COSInteger -> rightObj.intValue().toFloat()
                    else -> 0f
                }
                val top = when (topObj) {
                    is com.tom_roush.pdfbox.cos.COSNumber -> topObj.floatValue()
                    is com.tom_roush.pdfbox.cos.COSInteger -> topObj.intValue().toFloat()
                    else -> 0f
                }
                
                // PDF coordinates: (0,0) is bottom-left, Y increases upward
                // Android PdfRenderer renders with (0,0) at top-left, Y increases downward
                // The bitmap is flipped vertically when rendered
                
                // Store in PDF coordinates - we'll convert during click detection
                // Note: In PDF coords, top > bottom
                
                val pageHeight = page.mediaBox.height
                
                android.util.Log.d("PdfMediaExtractor", "Extracted annotation rect:")
                android.util.Log.d("PdfMediaExtractor", "  PDF coords: [left=$left, bottom=$bottom, right=$right, top=$top]")
                android.util.Log.d("PdfMediaExtractor", "  Page height: $pageHeight")
                
                return AnnotationRect(
                    left = left,
                    top = top,      // PDF top (high Y)
                    right = right,
                    bottom = bottom // PDF bottom (low Y)
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("PdfMediaExtractor", "Error extracting annotation rectangle", e)
        }
        return null
    }
    
    /**
     * Extract RichMedia annotation following PDF specification structure:
     * RichMedia -> RichMediaContent -> Assets (NameTree) -> Names array -> FileSpec -> EF -> F (stream)
     */
    private fun extractRichMediaAnnotation(
        annotationDict: COSDictionary,
        pageNumber: Int,
        annotationIndex: Int,
        document: PDDocument,
        cacheDir: File,
        mediaFiles: MutableList<MediaFile>,
        addMediaFile: (MediaFile) -> Boolean,
        annotationRect: AnnotationRect? = null
    ) {
        try {
            // Step 1: Get RichMediaContent dictionary
            val richMediaContentObj = annotationDict.getDictionaryObject(COSName.getPDFName("RichMediaContent"))
            if (richMediaContentObj == null) {
                android.util.Log.w("PdfMediaExtractor", "Page $pageNumber: RichMediaContent not found")
                return
            }
            
            // Handle indirect reference
            val richMediaContent = when {
                richMediaContentObj is COSDictionary -> richMediaContentObj
                richMediaContentObj is com.tom_roush.pdfbox.cos.COSObject -> {
                    val base = richMediaContentObj.getObject()
                    if (base is COSDictionary) base else null
                }
                else -> null
            }
            
            if (richMediaContent == null) {
                android.util.Log.w("PdfMediaExtractor", "Page $pageNumber: RichMediaContent is not a dictionary")
                return
            }
            
            android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Processing RichMediaContent")
            
            // Log all keys in RichMediaContent for debugging
            val richMediaKeys = richMediaContent.keySet()
            android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: RichMediaContent keys: ${richMediaKeys.map { if (it is COSName) it.name else it.toString() }}")
            
            val mediaCountBefore = mediaFiles.size
            
            // Step 2: Get Assets dictionary (NameTree structure)
            val assetsObj = richMediaContent.getDictionaryObject(COSName.getPDFName("Assets"))
            var assetsDict: COSDictionary? = null
            
            if (assetsObj != null) {
                // Handle indirect reference to Assets
                assetsDict = when {
                    assetsObj is COSDictionary -> assetsObj
                    assetsObj is com.tom_roush.pdfbox.cos.COSObject -> {
                        val base = assetsObj.getObject()
                        if (base is COSDictionary) base else null
                    }
                    else -> null
                }
                
                if (assetsDict != null) {
                    android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Found Assets dictionary")
                    
                    // Log all keys in Assets
                    val assetKeys = assetsDict.keySet()
                    android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Assets keys: ${assetKeys.map { if (it is COSName) it.name else it.toString() }}")
                    
                    // Step 3: Get Names array from Assets NameTree
                    // Names array format: [name1, ref1, name2, ref2, ...]
                    val namesArrayObj = assetsDict.getDictionaryObject(COSName.getPDFName("Names"))
                    if (namesArrayObj != null && namesArrayObj is com.tom_roush.pdfbox.cos.COSArray) {
                        val namesArray = namesArrayObj as com.tom_roush.pdfbox.cos.COSArray
                        val assetCount = namesArray.size() / 2
                        android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Found Names array with $assetCount assets")
                        
                        // Step 4: Process each asset pair (name, reference)
                        for (i in 0 until namesArray.size() step 2) {
                            try {
                                val nameObj = namesArray.get(i)
                                val refObj = namesArray.get(i + 1)
                                
                                // Extract asset name
                                val assetName = when {
                                    nameObj is COSName -> nameObj.name
                                    nameObj is com.tom_roush.pdfbox.cos.COSString -> nameObj.string
                                    else -> nameObj.toString().trim('(', ')')
                                }
                                
                                android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Processing asset '$assetName' (ref type: ${refObj.javaClass.simpleName})")
                                
                                // Resolve FileSpec reference
                                val fileSpecDict = resolveObjectReference(refObj, document)
                                if (fileSpecDict == null) {
                                    android.util.Log.w("PdfMediaExtractor", "Page $pageNumber: Could not resolve FileSpec for asset '$assetName', trying direct extraction...")
                                    // Try to extract directly from the reference object
                                    if (refObj is com.tom_roush.pdfbox.cos.COSObject) {
                                        val base = refObj.getObject()
                                        if (base is COSDictionary) {
                                            extractMediaFromFileSpec(base, assetName, pageNumber, annotationIndex, cacheDir, mediaFiles, addMediaFile, document, annotationRect, null)
                                        }
                                    }
                                    continue
                                }
                                
                                // Step 5: Extract media from FileSpec
                                extractMediaFromFileSpec(fileSpecDict, assetName, pageNumber, annotationIndex, cacheDir, mediaFiles, addMediaFile, document, annotationRect, null)
                                
                            } catch (e: Exception) {
                                android.util.Log.e("PdfMediaExtractor", "Error processing asset at index $i", e)
                                e.printStackTrace()
                            }
                        }
                    } else {
                        android.util.Log.w("PdfMediaExtractor", "Page $pageNumber: Names array not found or invalid, trying direct keys...")
                        // Fallback: try to extract assets by direct keys
                        extractAssetsByKeys(assetsDict, pageNumber, annotationIndex, document, cacheDir, mediaFiles, addMediaFile, annotationRect)
                    }
                } else {
                    android.util.Log.w("PdfMediaExtractor", "Page $pageNumber: Assets is not a dictionary")
                }
            } else {
                android.util.Log.w("PdfMediaExtractor", "Page $pageNumber: Assets not found in RichMediaContent")
            }
            
            // Also check Instances and Configurations
            if (assetsDict != null) {
                extractRichMediaInstances(richMediaContent, assetsDict, pageNumber, annotationIndex, document, cacheDir, mediaFiles, addMediaFile, annotationRect)
            }
            extractRichMediaConfigurations(richMediaContent, pageNumber, annotationIndex, document, cacheDir, mediaFiles, addMediaFile)
            
            // Comprehensive fallback: recursively search entire RichMediaContent for any streams
            val mediaCountAfter = mediaFiles.size
            val extractedCount = mediaCountAfter - mediaCountBefore
            android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Extracted $extractedCount media files from RichMedia")
            
            if (extractedCount == 0) {
                android.util.Log.w("PdfMediaExtractor", "Page $pageNumber: No media found through standard extraction, performing comprehensive recursive search...")
                extractStreamsFromDict(richMediaContent, document, cacheDir, mediaFiles, "RichMedia_Comprehensive_${pageNumber}_$annotationIndex", 0, addMediaFile)
                val finalCount = mediaFiles.size - mediaCountAfter
                if (finalCount > 0) {
                    android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Found $finalCount additional media files through recursive search")
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("PdfMediaExtractor", "Error extracting RichMedia annotation", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Resolve an object reference (handles COSObject, COSDictionary, indirect references)
     */
    private fun resolveObjectReference(obj: COSBase?, document: PDDocument? = null): COSDictionary? {
        if (obj == null) return null
        return when {
            obj is COSDictionary -> obj
            obj is com.tom_roush.pdfbox.cos.COSObject -> {
                val base = obj.getObject()
                when {
                    base is COSDictionary -> base
                    base is com.tom_roush.pdfbox.cos.COSObject -> resolveObjectReference(base, document)
                    else -> null
                }
            }
            else -> null
        }
    }
    
    /**
     * Extract media from a FileSpec dictionary
     * FileSpec structure: EF -> F (stream containing the actual media file)
     */
    private fun extractMediaFromFileSpec(
        fileSpecDict: COSDictionary,
        assetName: String,
        pageNumber: Int,
        annotationIndex: Int,
        cacheDir: File,
        mediaFiles: MutableList<MediaFile>,
        addMediaFile: (MediaFile) -> Boolean,
        document: PDDocument,
        annotationRect: AnnotationRect? = null,
        timeRange: Pair<Long, Long>? = null
    ) {
        try {
            android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Extracting from FileSpec '$assetName'")
            
            // Get EF (Embedded File) dictionary
            val efObj = fileSpecDict.getDictionaryObject(COSName.getPDFName("EF"))
            if (efObj == null) {
                android.util.Log.w("PdfMediaExtractor", "Page $pageNumber: EF dict not found for asset '$assetName', checking for direct stream...")
                // Fallback: check if FileSpec itself contains a stream
                val directStream = fileSpecDict.getDictionaryObject(COSName.getPDFName("F"))
                    ?: fileSpecDict.getDictionaryObject(COSName.getPDFName("Contents"))
                if (directStream != null) {
                    val stream = when {
                        directStream is PDStream -> directStream
                        directStream is com.tom_roush.pdfbox.cos.COSStream -> PDStream(directStream)
                        directStream is com.tom_roush.pdfbox.cos.COSObject -> {
                            val base = directStream.getObject()
                            when {
                                base is PDStream -> base
                                base is com.tom_roush.pdfbox.cos.COSStream -> PDStream(base)
                                else -> null
                            }
                        }
                        else -> null
                    }
                    if (stream != null) {
                        android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Found direct stream in FileSpec '$assetName'")
                        extractRichMediaAssetStream(stream, assetName, pageNumber, annotationIndex, cacheDir, mediaFiles, addMediaFile, fileSpecDict, annotationRect, timeRange)
                    }
                }
                // Also recursively search FileSpec for any streams
                extractStreamsFromDict(fileSpecDict, document, cacheDir, mediaFiles, "FileSpec_${pageNumber}_$assetName", 0, addMediaFile)
                return
            }
            
            val efDict = resolveObjectReference(efObj, document)
            if (efDict == null || efDict !is COSDictionary) {
                android.util.Log.w("PdfMediaExtractor", "Page $pageNumber: EF dict is invalid for asset '$assetName'")
                return
            }
            
            android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Found EF dict for asset '$assetName'")
            
            // Get F (File) stream - this contains the actual media data
            // Also check for UF (Unicode File) stream
            val fStreamObj = efDict.getDictionaryObject(COSName.getPDFName("F"))
                ?: efDict.getDictionaryObject(COSName.getPDFName("UF"))
            
            if (fStreamObj == null) {
                android.util.Log.w("PdfMediaExtractor", "Page $pageNumber: F/UF stream not found for asset '$assetName', checking EF dict for other streams...")
                // Fallback: recursively search EF dict
                extractStreamsFromDict(efDict, document, cacheDir, mediaFiles, "EF_${pageNumber}_$assetName", 0, addMediaFile)
                return
            }
            
            // Convert to PDStream
            val stream = when {
                fStreamObj is PDStream -> fStreamObj
                fStreamObj is com.tom_roush.pdfbox.cos.COSStream -> PDStream(fStreamObj)
                fStreamObj is com.tom_roush.pdfbox.cos.COSObject -> {
                    val base = fStreamObj.getObject()
                    when {
                        base is PDStream -> base
                        base is com.tom_roush.pdfbox.cos.COSStream -> PDStream(base)
                        else -> null
                    }
                }
                else -> null
            }
            
            if (stream == null) {
                android.util.Log.w("PdfMediaExtractor", "Page $pageNumber: Could not convert F stream for asset '$assetName'")
                return
            }
            
            android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Successfully resolved stream for asset '$assetName'")
            
            // Extract the stream
            extractRichMediaAssetStream(stream, assetName, pageNumber, annotationIndex, cacheDir, mediaFiles, addMediaFile, fileSpecDict, annotationRect, timeRange)
            
        } catch (e: Exception) {
            android.util.Log.e("PdfMediaExtractor", "Error extracting media from FileSpec '$assetName'", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Extract assets by iterating through Assets dictionary keys (fallback method)
     */
    private fun extractAssetsByKeys(
        assetsDict: COSDictionary,
        pageNumber: Int,
        annotationIndex: Int,
        document: PDDocument,
        cacheDir: File,
        mediaFiles: MutableList<MediaFile>,
        addMediaFile: (MediaFile) -> Boolean,
        annotationRect: AnnotationRect? = null
    ) {
        assetsDict.keySet().forEach { key ->
            if (key is COSName && key.name != "Names" && key.name != "Kids" && key.name != "Limits") {
                try {
                    val assetObj = assetsDict.getDictionaryObject(key)
                    val assetDict = resolveObjectReference(assetObj, document)
                    if (assetDict != null) {
                        extractMediaFromFileSpec(assetDict, key.name, pageNumber, annotationIndex, cacheDir, mediaFiles, addMediaFile, document, annotationRect, null)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PdfMediaExtractor", "Error extracting asset by key '${key.name}'", e)
                }
            }
        }
    }
    
    /**
     * Extract media from RichMedia Instances
     * Instances can have Params -> MediaClip with time ranges
     */
    private fun extractRichMediaInstances(
        richMediaContent: COSDictionary,
        assetsDict: COSDictionary,
        pageNumber: Int,
        annotationIndex: Int,
        document: PDDocument,
        cacheDir: File,
        mediaFiles: MutableList<MediaFile>,
        addMediaFile: (MediaFile) -> Boolean,
        annotationRect: AnnotationRect? = null
    ) {
        val instancesObj = richMediaContent.getDictionaryObject(COSName.getPDFName("Instances"))
        if (instancesObj == null) return
        
        val instances = when {
            instancesObj is com.tom_roush.pdfbox.cos.COSArray -> instancesObj
            instancesObj is COSDictionary -> {
                val array = com.tom_roush.pdfbox.cos.COSArray()
                array.add(instancesObj)
                array
            }
            else -> null
        }
        
        instances?.forEachIndexed { idx, instItem ->
            if (instItem is COSDictionary) {
                // Extract time range from Params -> MediaClip if present
                var timeRange: Pair<Long, Long>? = null
                val paramsObj = instItem.getDictionaryObject(COSName.getPDFName("Params"))
                if (paramsObj != null && paramsObj is COSDictionary) {
                    android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Instance $idx has Params dictionary")
                    val paramsKeys = (paramsObj as COSDictionary).keySet()
                    android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Instance $idx Params keys: ${paramsKeys.map { if (it is COSName) it.name else it.toString() }}")
                    
                    val mediaClipObj = paramsObj.getDictionaryObject(COSName.getPDFName("MediaClip"))
                    if (mediaClipObj != null) {
                        android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Instance $idx has MediaClip")
                        if (mediaClipObj is COSDictionary) {
                            val mediaClipKeys = (mediaClipObj as COSDictionary).keySet()
                            android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Instance $idx MediaClip keys: ${mediaClipKeys.map { if (it is COSName) it.name else it.toString() }}")
                            timeRange = extractTimeRangeFromMediaClip(mediaClipObj as COSDictionary)
                            android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Instance $idx extracted time range: $timeRange")
                        } else {
                            android.util.Log.w("PdfMediaExtractor", "Page $pageNumber: Instance $idx MediaClip is not a dictionary: ${mediaClipObj.javaClass.simpleName}")
                        }
                    } else {
                        android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Instance $idx Params does not contain MediaClip")
                    }
                } else {
                    android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Instance $idx does not have Params dictionary")
                }
                
                val assetRef = instItem.getDictionaryObject(COSName.getPDFName("Asset"))
                if (assetRef != null) {
                    val assetName = when {
                        assetRef is COSName -> assetRef.name
                        assetRef is com.tom_roush.pdfbox.cos.COSString -> assetRef.string
                        else -> "instance_$idx"
                    }
                    
                    // Try to find asset in Assets dictionary
                    val assetDict = assetsDict.getDictionaryObject(COSName.getPDFName(assetName))
                        ?: resolveObjectReference(assetRef, document)
                    
                    if (assetDict != null && assetDict is COSDictionary) {
                        extractMediaFromFileSpec(assetDict, assetName, pageNumber, annotationIndex, cacheDir, mediaFiles, addMediaFile, document, annotationRect, timeRange)
                    }
                }
            }
        }
    }
    
    /**
     * Extract media from RichMedia Configurations
     */
    private fun extractRichMediaConfigurations(
        richMediaContent: COSDictionary,
        pageNumber: Int,
        annotationIndex: Int,
        document: PDDocument,
        cacheDir: File,
        mediaFiles: MutableList<MediaFile>,
        addMediaFile: (MediaFile) -> Boolean
    ) {
        val configsObj = richMediaContent.getDictionaryObject(COSName.getPDFName("Configurations"))
        if (configsObj == null) return
        
        val configs = when {
            configsObj is com.tom_roush.pdfbox.cos.COSArray -> configsObj
            configsObj is COSDictionary -> {
                val array = com.tom_roush.pdfbox.cos.COSArray()
                array.add(configsObj)
                array
            }
            else -> null
        }
        
        configs?.forEach { configItem ->
            if (configItem is COSDictionary) {
                extractStreamsFromDict(configItem, document, cacheDir, mediaFiles, "RichMedia_Config_${pageNumber}_$annotationIndex", 0, addMediaFile)
            }
        }
    }
    
    /**
     * Extract media from annotations (RichMedia, Screen annotations)
     * Note: PdfBox-Android doesn't fully support RichMedia, so we access COS dictionary directly
     */
    private fun extractAnnotationMedia(
        document: PDDocument,
        page: PDPage,
        pageNumber: Int,
        cacheDir: File,
        mediaFiles: MutableList<MediaFile>,
        addMediaFile: (MediaFile) -> Boolean
    ) {
        try {
            // Access annotations directly through COS dictionary since PdfBox-Android
            // doesn't fully support RichMedia annotations
            val pageDict = page.cosObject
            if (pageDict != null && pageDict is COSDictionary) {
                val annotsArray = pageDict.getDictionaryObject(COSName.getPDFName("Annots"))
                if (annotsArray != null) {
                    // Handle both array and single dictionary
                    val annotsList = mutableListOf<COSDictionary>()
                    when (annotsArray) {
                        is com.tom_roush.pdfbox.cos.COSArray -> {
                            annotsArray.forEach { item ->
                                if (item is COSDictionary) {
                                    annotsList.add(item)
                                }
                            }
                        }
                        is COSDictionary -> {
                            annotsList.add(annotsArray)
                        }
                    }
                    
                    android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Found ${annotsList.size} annotations")
                    
                    // Log annotation subtypes for debugging
                    annotsList.forEachIndexed { idx, annot ->
                        try {
                            val actualDict = when {
                                annot is COSDictionary -> annot
                                annot is com.tom_roush.pdfbox.cos.COSObject -> {
                                    val base = annot.getObject()
                                    if (base is COSDictionary) base else null
                                }
                                else -> null
                            }
                            if (actualDict != null) {
                                val subtype = actualDict.getNameAsString(COSName.getPDFName("Subtype")) ?: "Unknown"
                                val hasRect = actualDict.getDictionaryObject(COSName.getPDFName("Rect")) != null
                                android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Annotation $idx subtype='$subtype', hasRect=$hasRect")
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("PdfMediaExtractor", "Error checking annotation $idx", e)
                        }
                    }
                    
                    // Also try accessing annotations through PDPage API as fallback
                    try {
                        val pageAnnots = page.annotations
                        android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Found ${pageAnnots.size} annotations via PDPage API")
                        pageAnnots.forEachIndexed { idx, annot ->
                            try {
                                val annotSubtype = annot.subtype
                                android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: PDPage annotation $idx subtype: '$annotSubtype'")
                                if (annotSubtype == "RichMedia" || annotSubtype == "Screen") {
                                    val annotDict = annot.cosObject
                                    if (annotDict is COSDictionary) {
                                        android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Found RichMedia via PDPage API")
                                        val rect = extractAnnotationRect(annotDict, page)
                                        extractRichMediaAnnotation(annotDict, pageNumber, idx, document, cacheDir, mediaFiles, addMediaFile, rect)
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("PdfMediaExtractor", "Error processing PDPage annotation", e)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("PdfMediaExtractor", "Could not access annotations via PDPage API", e)
                    }
                    
                    annotsList.forEachIndexed { index, annotationDict ->
                        try {
                            // Handle indirect reference
                            val actualDict = when {
                                annotationDict is COSDictionary -> annotationDict
                                annotationDict is com.tom_roush.pdfbox.cos.COSObject -> {
                                    val base = annotationDict.getObject()
                                    if (base is COSDictionary) base else null
                                }
                                else -> null
                            }
                            
                            if (actualDict == null) {
                                android.util.Log.w("PdfMediaExtractor", "Page $pageNumber: Annotation $index is not a dictionary")
                                return@forEachIndexed
                            }
                            
                            val subtype = actualDict.getNameAsString(COSName.getPDFName("Subtype")) ?: ""
                            android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Processing annotation $index, subtype: '$subtype'")
                            
                            // Log all keys in annotation to see what's available
                            val annotKeys = actualDict.keySet()
                            android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Annotation $index keys: ${annotKeys.map { if (it is COSName) it.name else it.toString() }}")
                            
                            // Extract annotation rectangle FIRST so we can use it for Actions/RichMediaSettings
                            val annotationRect = extractAnnotationRect(actualDict, page)
                            if (annotationRect != null) {
                                android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Annotation $index has rect: [${annotationRect.left}, ${annotationRect.top}, ${annotationRect.right}, ${annotationRect.bottom}]")
                            } else {
                                android.util.Log.w("PdfMediaExtractor", "Page $pageNumber: Annotation $index has NO rect")
                            }
                            
                            // Create a wrapper that adds annotation rectangle to media files extracted from this annotation
                            val addMediaFileWithRect: (MediaFile) -> Boolean = { mediaFile ->
                                val updatedMediaFile = if (mediaFile.annotationRect == null && annotationRect != null) {
                                    android.util.Log.d("PdfMediaExtractor", "Adding annotation rect to media file: ${mediaFile.name}")
                                    mediaFile.copy(
                                        annotationRect = annotationRect,
                                        pageNumber = mediaFile.pageNumber ?: pageNumber
                                    )
                                } else {
                                    mediaFile
                                }
                                addMediaFile(updatedMediaFile)
                            }
                            
                            // Check for Actions - annotations can have actions that trigger media playback
                            val action = actualDict.getDictionaryObject(COSName.getPDFName("A"))
                            if (action != null && action is COSDictionary) {
                                android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Found Action in annotation $index")
                                val actionType = (action as COSDictionary).getNameAsString(COSName.getPDFName("S")) ?: ""
                                android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Action type: $actionType")
                                
                                // Recursively search action dictionary for any streams
                                // Use wrapper to add annotation rectangle
                                extractStreamsFromDict(action as COSDictionary, document, cacheDir, mediaFiles, "Annotation_${pageNumber}_${index}_Action", 0, addMediaFileWithRect)
                            }
                            
                            // Check for RichMediaSettings - might contain media references
                            val richMediaSettings = actualDict.getDictionaryObject(COSName.getPDFName("RichMediaSettings"))
                            if (richMediaSettings != null && richMediaSettings is COSDictionary) {
                                android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Found RichMediaSettings in annotation $index")
                                extractStreamsFromDict(richMediaSettings as COSDictionary, document, cacheDir, mediaFiles, "Annotation_${pageNumber}_${index}_RichMediaSettings", 0, addMediaFileWithRect)
                            }
                            
                            // Check for Movie annotations
                            if (subtype == "Movie") {
                                android.util.Log.d("PdfMediaExtractor", "Found Movie annotation on page $pageNumber")
                                val movieDict = actualDict.getDictionaryObject(COSName.getPDFName("Movie"))
                                if (movieDict != null && movieDict is COSDictionary) {
                                    val movieFile = movieDict.getDictionaryObject(COSName.getPDFName("F"))
                                    if (movieFile != null && movieFile is PDStream) {
                                        val extension = "mp4" // Default for movies
                                        val mediaFile = File(cacheDir, "video_movie_${pageNumber}_$index.$extension")
                                        extractStream(movieFile as PDStream, mediaFile)
                                        if (mediaFile.exists() && mediaFile.length() > 0) {
                                            android.util.Log.d("PdfMediaExtractor", "Extracted VIDEO from Movie annotation: ${mediaFile.name} (${mediaFile.length()} bytes)")
                                            addMediaFile(
                                                MediaFile(
                                                    name = "video_movie_${pageNumber}_$index",
                                                    path = mediaFile.absolutePath,
                                                    type = MediaType.VIDEO,
                                                    pageNumber = pageNumber,
                                                    annotationRect = annotationRect
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            // Check for Sound annotations
                            else if (subtype == "Sound") {
                                android.util.Log.d("PdfMediaExtractor", "Found Sound annotation on page $pageNumber")
                                val soundDict = actualDict.getDictionaryObject(COSName.getPDFName("Sound"))
                                if (soundDict != null && soundDict is COSDictionary) {
                                    val soundStream = soundDict.getDictionaryObject(COSName.getPDFName("Contents"))
                                        ?: soundDict.getDictionaryObject(COSName.getPDFName("F"))
                                    if (soundStream != null && soundStream is PDStream) {
                                        val extension = "mp3" // Default for sound
                                        val mediaFile = File(cacheDir, "audio_sound_${pageNumber}_$index.$extension")
                                        extractStream(soundStream as PDStream, mediaFile)
                                        if (mediaFile.exists() && mediaFile.length() > 0) {
                                            android.util.Log.d("PdfMediaExtractor", "Extracted AUDIO from Sound annotation: ${mediaFile.name} (${mediaFile.length()} bytes)")
                                            addMediaFile(
                                                MediaFile(
                                                    name = "audio_sound_${pageNumber}_$index",
                                                    path = mediaFile.absolutePath,
                                                    type = MediaType.AUDIO,
                                                    pageNumber = pageNumber,
                                                    annotationRect = annotationRect
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            // Check for RichMedia annotations (common for embedded video/audio)
                            else if (subtype == "RichMedia" || subtype == "Screen") {
                                android.util.Log.d("PdfMediaExtractor", "=== Found RichMedia annotation on page $pageNumber ===")
                                extractRichMediaAnnotation(actualDict, pageNumber, index, document, cacheDir, mediaFiles, addMediaFile, annotationRect)
                            }
                            
                            // Check for any other annotation types that might contain media streams
                            // Look for common media-related dictionary keys
                            val annotationKeys = actualDict.keySet()
                            annotationKeys.forEach { key ->
                                if (key is COSName) {
                                    val value = actualDict.getDictionaryObject(key)
                                    if (value is PDStream) {
                                        val stream = value as PDStream
                                        val streamLength = try {
                                            stream.cosObject.getInt(COSName.getPDFName("Length"), 0)
                                        } catch (e: Exception) {
                                            0
                                        }
                                        if (streamLength > 1000) {
                                            android.util.Log.d("PdfMediaExtractor", "Found stream in annotation key ${key.name} on page $pageNumber ($streamLength bytes)")
                                            val mediaType = determineMediaTypeFromStream(stream, key.name)
                                            if (mediaType != null && mediaType != MediaType.IMAGE) {
                                                val extension = getExtensionForMediaType(mediaType)
                                                val mediaFile = File(cacheDir, "${mediaType.name.lowercase()}_annotation_${pageNumber}_${key.name}_$index.$extension")
                                                extractStream(stream, mediaFile)
                                                if (mediaFile.exists() && mediaFile.length() > 0) {
                                                    android.util.Log.d("PdfMediaExtractor", "Extracted ${mediaType.name} from annotation stream: ${mediaFile.name} (${mediaFile.length()} bytes)")
                                                    addMediaFile(
                                                        MediaFile(
                                                            name = "${mediaType.name.lowercase()}_annotation_${pageNumber}_${key.name}",
                                                            path = mediaFile.absolutePath,
                                                            type = mediaType,
                                                            pageNumber = pageNumber,
                                                            annotationRect = annotationRect
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // Also recursively search the entire annotation dictionary for any streams
                            // This catches media embedded in nested structures that weren't caught above
                            if (annotationRect != null) {
                                android.util.Log.d("PdfMediaExtractor", "Page $pageNumber: Recursively searching annotation $index for any remaining streams with rect")
                                extractStreamsFromDict(actualDict, document, cacheDir, mediaFiles, "Annotation_${pageNumber}_${index}_Full", 0, addMediaFileWithRect)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("PdfMediaExtractor", "Error processing annotation", e)
                            e.printStackTrace()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PdfMediaExtractor", "Error extracting annotation media", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Try to determine media type by examining stream content
     */
    private fun determineMediaTypeFromStream(stream: PDStream, name: String): MediaType? {
        try {
            // Read more bytes to detect file type (some formats have signatures further in)
            stream.createInputStream().use { input ->
                val header = ByteArray(64) // Read more bytes for better detection
                val bytesRead = input.read(header)
                
                if (bytesRead >= 4) {
                    // Check for common file signatures
                    when {
                        // MP3: ID3 tag (ID3v2) or MPEG header
                        (bytesRead >= 3 && header[0] == 0x49.toByte() && header[1] == 0x44.toByte() && header[2] == 0x33.toByte()) ||
                        (bytesRead >= 2 && header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0) -> {
                            android.util.Log.d("PdfMediaExtractor", "Detected MP3 audio signature in stream: $name")
                            return MediaType.AUDIO
                        }
                        
                        // MP4/MOV: ftyp box (can be at offset 4 or 8)
                        (bytesRead >= 8 && header[4] == 0x66.toByte() && header[5] == 0x74.toByte() && 
                         header[6] == 0x79.toByte() && header[7] == 0x70.toByte()) ||
                        (bytesRead >= 12 && header[8] == 0x66.toByte() && header[9] == 0x74.toByte() && 
                         header[10] == 0x79.toByte() && header[11] == 0x70.toByte()) -> {
                            android.util.Log.d("PdfMediaExtractor", "Detected MP4/MOV video signature in stream: $name")
                            return MediaType.VIDEO
                        }
                        
                        // AVI: RIFF header
                        bytesRead >= 4 && header[0] == 0x52.toByte() && header[1] == 0x49.toByte() && 
                        header[2] == 0x46.toByte() && header[3] == 0x46.toByte() -> {
                            // Check if it's AVI or WAV
                            if (bytesRead >= 12 && header[8] == 0x41.toByte() && header[9] == 0x56.toByte() && 
                                header[10] == 0x49.toByte() && header[11] == 0x20.toByte()) {
                                android.util.Log.d("PdfMediaExtractor", "Detected AVI video signature in stream: $name")
                                return MediaType.VIDEO
                            } else if (bytesRead >= 12 && header[8] == 0x57.toByte() && header[9] == 0x41.toByte() &&
                                      header[10] == 0x56.toByte() && header[11] == 0x45.toByte()) {
                                android.util.Log.d("PdfMediaExtractor", "Detected WAV audio signature in stream: $name")
                                return MediaType.AUDIO
                            }
                        }
                        
                        // OGG (Ogg Vorbis/Theora)
                        bytesRead >= 4 && header[0] == 0x4F.toByte() && header[1] == 0x67.toByte() && 
                        header[2] == 0x67.toByte() && header[3] == 0x53.toByte() -> {
                            // Check further to determine if audio or video
                            if (bytesRead >= 30) {
                                val oggType = String(header, 28, minOf(2, bytesRead - 28))
                                android.util.Log.d("PdfMediaExtractor", "Detected OGG signature in stream: $name")
                                // OGG can be audio or video, default to audio
                                return MediaType.AUDIO
                            }
                        }
                        
                        // FLAC
                        bytesRead >= 4 && header[0] == 0x66.toByte() && header[1] == 0x4C.toByte() && 
                        header[2] == 0x61.toByte() && header[3] == 0x43.toByte() -> {
                            android.util.Log.d("PdfMediaExtractor", "Detected FLAC audio signature in stream: $name")
                            return MediaType.AUDIO
                        }
                        
                        // WebM
                        bytesRead >= 4 && header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() && 
                        header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte() -> {
                            android.util.Log.d("PdfMediaExtractor", "Detected WebM video signature in stream: $name")
                            return MediaType.VIDEO
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("PdfMediaExtractor", "Error reading stream $name for type detection: ${e.message}")
        }
        return null
    }
    
    /**
     * Determine media type from content type
     */
    private fun determineMediaTypeFromContentType(contentType: String): MediaType? {
        val lowerType = contentType.lowercase()
        return when {
            lowerType.contains("audio") -> MediaType.AUDIO
            lowerType.contains("video") -> MediaType.VIDEO
            lowerType.contains("image") -> MediaType.IMAGE
            else -> null
        }
    }
    
    /**
     * Determine media type from subtype and name
     */
    private fun determineMediaType(subtype: String, name: String): MediaType? {
        val lowerSubtype = subtype.lowercase()
        val lowerName = name.lowercase()
        
        // Check for audio indicators
        val isAudio = lowerSubtype.contains("audio") || lowerSubtype.contains("sound") ||
                lowerName.contains("audio") || lowerName.contains("sound") ||
                lowerName.contains(".mp3") || lowerName.contains(".wav") ||
                lowerName.contains(".m4a") || lowerName.contains(".aac") ||
                lowerName.contains(".ogg") || lowerName.contains(".flac") ||
                lowerSubtype == "sound" || lowerSubtype == "audio"
        
        // Check for video indicators
        val isVideo = lowerSubtype.contains("video") || lowerSubtype.contains("movie") ||
                lowerName.contains("video") || lowerName.contains("movie") ||
                lowerName.contains(".mp4") || lowerName.contains(".mov") ||
                lowerName.contains(".avi") || lowerName.contains(".mkv") ||
                lowerName.contains(".webm") || lowerName.contains(".flv") ||
                lowerSubtype == "movie" || lowerSubtype == "video"
        
        // Check for image indicators
        val isImage = lowerSubtype.contains("image") ||
                lowerName.contains(".png") || lowerName.contains(".jpg") ||
                lowerName.contains(".jpeg") || lowerName.contains(".gif") ||
                lowerName.contains(".bmp") || lowerName.contains(".webp")
        
        return when {
            isAudio -> MediaType.AUDIO
            isVideo -> MediaType.VIDEO
            isImage -> MediaType.IMAGE
            else -> null
        }
    }
    
    /**
     * Get file extension for media type
     */
    private fun getExtensionForMediaType(mediaType: MediaType): String {
        return when (mediaType) {
            MediaType.AUDIO -> "mp3"
            MediaType.VIDEO -> "mp4"
            MediaType.IMAGE -> "png"
        }
    }
    
    /**
     * Extract image from PDImageXObject
     */
    private fun extractImage(imageXObject: PDImageXObject, outputFile: File) {
        try {
            val image = imageXObject.image
            FileOutputStream(outputFile).use { out ->
                image.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Extract stream to file
     */
    private fun extractStream(stream: PDStream, outputFile: File) {
        try {
            // Ensure parent directory exists
            outputFile.parentFile?.mkdirs()
            
            FileOutputStream(outputFile).use { out ->
                stream.createInputStream().use { input ->
                    input.copyTo(out)
                }
            }
            
            // Verify extraction was successful
            if (!outputFile.exists() || outputFile.length() == 0L) {
                android.util.Log.w("PdfMediaExtractor", "Failed to extract stream to ${outputFile.name}")
            } else {
                android.util.Log.d("PdfMediaExtractor", "Successfully extracted ${outputFile.name} (${outputFile.length()} bytes)")
            }
        } catch (e: Exception) {
            android.util.Log.e("PdfMediaExtractor", "Error extracting stream to ${outputFile.name}", e)
            e.printStackTrace()
        }
    }
}
