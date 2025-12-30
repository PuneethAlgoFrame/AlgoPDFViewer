package `in`.algoframe.pdfviewer.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.algoframe.pdfviewer.data.MediaFile
import `in`.algoframe.pdfviewer.data.MediaType
import `in`.algoframe.pdfviewer.utils.PdfMediaExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PdfViewer(
    pdfPath: String,
    modifier: Modifier = Modifier,
    onMediaFound: (MediaFile) -> Unit = {},
    onMediaClick: (MediaFile) -> Unit = {},
    onPageChanged: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var isPageLoading by remember { mutableStateOf(false) }
    var mediaFiles by remember { mutableStateOf<List<MediaFile>>(emptyList()) }
    var currentPage by remember { mutableStateOf(0) }
    var totalPages by remember { mutableStateOf(0) }
    var pageBitmaps by remember { mutableStateOf<Map<Int, Bitmap>>(emptyMap()) }
    var pageDimensions by remember { mutableStateOf<Map<Int, Pair<Int, Int>>>(emptyMap()) } // width, height
    var displayedImageSize by remember { mutableStateOf<IntSize?>(null) }
    
    LaunchedEffect(pdfPath) {
        isLoading = true
        scope.launch {
            try {
                android.util.Log.d("PdfViewer", "Starting PDF viewer for: $pdfPath")
                
                // Load PDF pages to get total page count
                withContext(Dispatchers.IO) {
                    val file = File(pdfPath)
                    val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val pdfRenderer = PdfRenderer(fileDescriptor)
                    
                    totalPages = pdfRenderer.pageCount
                    val bitmaps = mutableMapOf<Int, Bitmap>()
                    
                    // Render first page immediately
                    val dimensions = mutableMapOf<Int, Pair<Int, Int>>()
                    if (totalPages > 0) {
                        val page = pdfRenderer.openPage(0)
                        val pageWidth = page.width
                        val pageHeight = page.height
                        val bitmap = Bitmap.createBitmap(
                            pageWidth * 2, // Scale for better quality
                            pageHeight * 2,
                            Bitmap.Config.ARGB_8888
                        )
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmaps[0] = bitmap
                        dimensions[0] = Pair(pageWidth, pageHeight)
                        page.close()
                    }
                    pageDimensions = dimensions
                    
                    pdfRenderer.close()
                    fileDescriptor.close()
                    
                    pageBitmaps = bitmaps
                }
                
                // Extract media for first page
                if (totalPages > 0) {
                    android.util.Log.d("PdfViewer", "Extracting media files for page 1...")
                    onPageChanged?.invoke(0)
                    val extractor = PdfMediaExtractor(context)
                    val pageMediaFiles = extractor.extractMediaFilesForPage(pdfPath, 0)
                    mediaFiles = pageMediaFiles
                    android.util.Log.d("PdfViewer", "Page 1 extraction complete. Found ${pageMediaFiles.size} media files")
                    
                    pageMediaFiles.forEachIndexed { index, mediaFile ->
                        android.util.Log.d("PdfViewer", "Calling onMediaFound for media $index: ${mediaFile.name}, page: ${mediaFile.pageNumber}, type: ${mediaFile.type}, hasRect: ${mediaFile.annotationRect != null}")
                        if (mediaFile.annotationRect != null) {
                            android.util.Log.d("PdfViewer", "  Rect: [${mediaFile.annotationRect.left}, ${mediaFile.annotationRect.top}, ${mediaFile.annotationRect.right}, ${mediaFile.annotationRect.bottom}]")
                        }
                        onMediaFound(mediaFile)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }
    
    // Extract media when page changes
    LaunchedEffect(currentPage) {
        // Set loading state if not initial load (initial load is handled by LaunchedEffect(pdfPath))
        if (!isLoading) {
            isPageLoading = true
        }
        scope.launch {
            try {
                android.util.Log.d("PdfViewer", "Page changed to ${currentPage + 1}, extracting media...")
                onPageChanged?.invoke(currentPage)
                val extractor = PdfMediaExtractor(context)
                val pageMediaFiles = extractor.extractMediaFilesForPage(pdfPath, currentPage)
                mediaFiles = pageMediaFiles
                android.util.Log.d("PdfViewer", "Page ${currentPage + 1} extraction complete. Found ${pageMediaFiles.size} media files")
                
                pageMediaFiles.forEachIndexed { index, mediaFile ->
                    android.util.Log.d("PdfViewer", "Calling onMediaFound for media $index: ${mediaFile.name}, page: ${mediaFile.pageNumber}, type: ${mediaFile.type}, hasRect: ${mediaFile.annotationRect != null}")
                    if (mediaFile.annotationRect != null) {
                        android.util.Log.d("PdfViewer", "  Rect: [${mediaFile.annotationRect.left}, ${mediaFile.annotationRect.top}, ${mediaFile.annotationRect.right}, ${mediaFile.annotationRect.bottom}]")
                    }
                    onMediaFound(mediaFile)
                }
            } catch (e: Exception) {
                android.util.Log.e("PdfViewer", "Error extracting media for page ${currentPage + 1}", e)
                e.printStackTrace()
            } finally {
                // Only clear isPageLoading if we set it (i.e., not during initial load)
                if (!isLoading) {
                    isPageLoading = false
                }
            }
        }
    }
    
    Column(modifier = modifier.fillMaxSize()) {
        // Page indicator
        if (totalPages > 0) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            if (currentPage > 0 && !isLoading && !isPageLoading) {
                                isPageLoading = true
                                currentPage--
                                scope.launch {
                                    try {
                                        loadPage(context, pdfPath, currentPage, pageBitmaps, 
                                            onUpdate = { updated -> pageBitmaps = updated },
                                            onDimensionsUpdate = { dims -> 
                                                pageDimensions = pageDimensions.toMutableMap().apply { putAll(dims) }
                                            }
                                        )
                                    } finally {
                                        // isPageLoading will be set to false in LaunchedEffect when media extraction completes
                                    }
                                }
                            }
                        },
                        enabled = currentPage > 0 && !isLoading && !isPageLoading,
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Previous Page",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Prev")
                    }
                    
                    Text(
                        text = "Page ${currentPage + 1} of $totalPages",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    
                    FilledTonalButton(
                        onClick = {
                            if (currentPage < totalPages - 1 && !isLoading && !isPageLoading) {
                                isPageLoading = true
                                currentPage++
                                scope.launch {
                                    try {
                                        loadPage(context, pdfPath, currentPage, pageBitmaps, 
                                            onUpdate = { updated -> pageBitmaps = updated },
                                            onDimensionsUpdate = { dims -> 
                                                pageDimensions = pageDimensions.toMutableMap().apply { putAll(dims) }
                                            }
                                        )
                                    } finally {
                                        // isPageLoading will be set to false in LaunchedEffect when media extraction completes
                                    }
                                }
                            }
                        },
                        enabled = currentPage < totalPages - 1 && !isLoading && !isPageLoading,
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text("Next")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Next Page",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    pageBitmaps[currentPage]?.let { bitmap ->
                        val pageDim = pageDimensions[currentPage]
                        // Get media files for current page with annotation rectangles
                        val pageMedia = mediaFiles.filter { 
                            (it.pageNumber == currentPage + 1 || it.pageNumber == currentPage) && it.annotationRect != null 
                        }
                        
                        android.util.Log.d("PdfViewer", "Page ${currentPage + 1}: Found ${pageMedia.size} media files with annotation rectangles")
                        pageMedia.forEach { media ->
                            android.util.Log.d("PdfViewer", "  Media: ${media.name}, type: ${media.type}, rect: ${media.annotationRect}")
                        }
                        
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "PDF Page ${currentPage + 1}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .onSizeChanged { size ->
                                    displayedImageSize = size
                                    android.util.Log.d("PdfViewer", "Image size changed: ${size.width}x${size.height}")
                                }
                                .pointerInput(currentPage, mediaFiles, displayedImageSize, pageDim) {
                                    detectTapGestures { tapOffset ->
                                        android.util.Log.d("PdfViewer", "=== TAP DETECTED ===")
                                        android.util.Log.d("PdfViewer", "Tap offset: (${tapOffset.x}, ${tapOffset.y})")
                                        android.util.Log.d("PdfViewer", "Current page: $currentPage")
                                        android.util.Log.d("PdfViewer", "Page media count: ${pageMedia.size}")
                                        
                                        if (pageMedia.isNotEmpty() && pageDim != null && displayedImageSize != null) {
                                            // Get displayed image size on screen
                                            // Note: displayedImageSize includes padding, but tapOffset is relative to the Image composable
                                            // which also includes padding, so they should match
                                            val displayedWidth = displayedImageSize!!.width.toFloat()
                                            val displayedHeight = displayedImageSize!!.height.toFloat()
                                            
                                            // PDF page dimensions (original, not scaled)
                                            val pdfWidth = pageDim.first.toFloat()
                                            val pdfHeight = pageDim.second.toFloat()
                                            
                                            android.util.Log.d("PdfViewer", "Displayed size: ${displayedWidth}x${displayedHeight}")
                                            android.util.Log.d("PdfViewer", "PDF size: ${pdfWidth}x${pdfHeight}")
                                            
                                            // Calculate scale factors to convert tap coordinates to PDF page coordinates
                                            // Annotation rectangles are stored in Android coordinates (top-left origin)
                                            // after conversion from PDF coordinates (bottom-left origin)
                                            // They are relative to the PDF page dimensions
                                            
                                            // The bitmap is rendered at 2x scale, but the displayed image is scaled to fit screen
                                            // We need to convert from displayed image coordinates to PDF page coordinates
                                            
                                            // Scale from displayed image size to PDF page size
                                            // The displayed image maintains aspect ratio and fits the width
                                            val scaleX = pdfWidth / displayedWidth
                                            val scaleY = pdfHeight / displayedHeight
                                            
                                            // Convert tap coordinates to PDF page coordinates (Android coordinate system)
                                            // tapOffset is relative to displayed image (top-left origin)
                                            // Both tap coordinates and annotation rectangles use Android coordinate system (top-left origin)
                                            val pdfX = tapOffset.x * scaleX
                                            val pdfY = tapOffset.y * scaleY
                                            
                                            android.util.Log.d("PdfViewer", "Coordinate conversion: tap(${tapOffset.x}, ${tapOffset.y}) -> pdf($pdfX, $pdfY) with scale($scaleX, $scaleY)")
                                            
                                            android.util.Log.d("PdfViewer", "Scale factors: ${scaleX}x${scaleY}")
                                            android.util.Log.d("PdfViewer", "Converted coordinates: ($pdfX, $pdfY) [Android coordinate system]")
                                            android.util.Log.d("PdfViewer", "PDF bounds: (0, 0) to ($pdfWidth, $pdfHeight)")
                                            
                                            // Check if tap is within any annotation rectangle
                                            var foundMatch = false
                                            for (media in pageMedia) {
                                                media.annotationRect?.let { rect ->
                                                    // Annotation rectangles are stored in PDF coordinates (bottom-left origin)
                                                    // Tap coordinates are in Android coordinates (top-left origin)
                                                    // We need to convert PDF rectangle coordinates to Android coordinates for comparison
                                                    
                                                    // X coordinates don't need conversion (same in both systems)
                                                    val minX = minOf(rect.left, rect.right)
                                                    val maxX = maxOf(rect.left, rect.right)
                                                    
                                                    // Convert Y coordinates from PDF (bottom-left origin) to Android (top-left origin)
                                                    // PDF: (0,0) at bottom-left, Y increases upward, top > bottom
                                                    // Android: (0,0) at top-left, Y increases downward, top < bottom
                                                    val pdfRectTop = maxOf(rect.top, rect.bottom)  // PDF top (high Y)
                                                    val pdfRectBottom = minOf(rect.top, rect.bottom) // PDF bottom (low Y)
                                                    
                                                    val androidRectTop = pdfHeight - pdfRectTop      // Android top (low Y)
                                                    val androidRectBottom = pdfHeight - pdfRectBottom // Android bottom (high Y)
                                                    
                                                    // Ensure top < bottom for Android coordinates
                                                    val minY = minOf(androidRectTop, androidRectBottom)
                                                    val maxY = maxOf(androidRectTop, androidRectBottom)
                                                    
                                                    // Use tolerance for easier clicking
                                                    val tolerance = 20f
                                                    val expandedMinX = minX - tolerance
                                                    val expandedMaxX = maxX + tolerance
                                                    val expandedMinY = minY - tolerance
                                                    val expandedMaxY = maxY + tolerance
                                                    
                                                    val inX = pdfX >= expandedMinX && pdfX <= expandedMaxX
                                                    val inY = pdfY >= expandedMinY && pdfY <= expandedMaxY
                                                    val contains = inX && inY
                                                    
                                                    android.util.Log.d("PdfViewer", "Checking media '${media.name}' (${media.type}):")
                                                    android.util.Log.d("PdfViewer", "  PDF rect: X=[${rect.left}, ${rect.right}], Y=[${rect.bottom}, ${rect.top}]")
                                                    android.util.Log.d("PdfViewer", "  Android rect: X=[$minX, $maxX], Y=[$minY, $maxY]")
                                                    android.util.Log.d("PdfViewer", "  Expanded bounds: X=[$expandedMinX, $expandedMaxX], Y=[$expandedMinY, $expandedMaxY]")
                                                    android.util.Log.d("PdfViewer", "  Tap point: ($pdfX, $pdfY)")
                                                    android.util.Log.d("PdfViewer", "  In bounds: X=$inX (${if (inX) "✓" else "✗"}), Y=$inY (${if (inY) "✓" else "✗"}), contains=$contains")
                                                    
                                                    if (contains && !foundMatch) {
                                                        android.util.Log.d("PdfViewer", "✓✓✓ TAP DETECTED ON ANNOTATION for media: ${media.name}, type: ${media.type}")
                                                        // Only play audio/video, not images
                                                        if (media.type == MediaType.AUDIO || media.type == MediaType.VIDEO) {
                                                            android.util.Log.d("PdfViewer", "Calling onMediaClick for ${media.name}")
                                                            onMediaClick(media)
                                                            foundMatch = true
                                                        } else {
                                                            android.util.Log.d("PdfViewer", "Skipping image media type")
                                                        }
                                                    }
                                                }
                                            }
                                            if (!foundMatch) {
                                                android.util.Log.d("PdfViewer", "No annotation matched the tap or media type is not playable")
                                            }
                                        } else {
                                            android.util.Log.d("PdfViewer", "Cannot process tap: pageMedia=${pageMedia.size}, pageDim=$pageDim, displayedSize=$displayedImageSize")
                                        }
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}

private suspend fun loadPage(
    context: android.content.Context,
    pdfPath: String,
    pageIndex: Int,
    currentBitmaps: Map<Int, Bitmap>,
    onUpdate: (Map<Int, Bitmap>) -> Unit,
    onDimensionsUpdate: ((Map<Int, Pair<Int, Int>>) -> Unit)? = null
) = withContext(Dispatchers.IO) {
    if (currentBitmaps.containsKey(pageIndex)) return@withContext
    
    try {
        val file = File(pdfPath)
        val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val pdfRenderer = PdfRenderer(fileDescriptor)
        
        if (pageIndex < pdfRenderer.pageCount) {
            val page = pdfRenderer.openPage(pageIndex)
            val pageWidth = page.width
            val pageHeight = page.height
            val bitmap = Bitmap.createBitmap(
                pageWidth * 2,
                pageHeight * 2,
                Bitmap.Config.ARGB_8888
            )
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            
            val updated = currentBitmaps.toMutableMap()
            updated[pageIndex] = bitmap
            onUpdate(updated)
            
            // Update dimensions
            onDimensionsUpdate?.let { update ->
                val dims = mutableMapOf<Int, Pair<Int, Int>>()
                dims[pageIndex] = Pair(pageWidth, pageHeight)
                update(dims)
            }
            
            page.close()
        }
        
        pdfRenderer.close()
        fileDescriptor.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

