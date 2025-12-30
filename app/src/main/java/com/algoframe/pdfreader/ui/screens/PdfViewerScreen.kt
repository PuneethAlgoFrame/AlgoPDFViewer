package com.algoframe.pdfreader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.algoframe.pdfreader.data.MediaFile
import com.algoframe.pdfreader.data.MediaType
import com.algoframe.pdfreader.ui.components.AudioPlayer
import com.algoframe.pdfreader.ui.components.PdfViewer
import com.algoframe.pdfreader.ui.components.VideoPlayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    pdfPath: String,
    onBack: () -> Unit
) {
    var mediaFiles by remember { mutableStateOf<List<MediaFile>>(emptyList()) }
    var selectedMedia by remember { mutableStateOf<MediaFile?>(null) }
    
    // Clear selected media when mediaFiles changes (page changed)
    LaunchedEffect(mediaFiles) {
        // Clear selected media if it's not in the current page's media files
        selectedMedia?.let { media ->
            if (!mediaFiles.contains(media)) {
                selectedMedia = null
            }
        }
    }
    
    // Log when media is selected
    LaunchedEffect(selectedMedia) {
        selectedMedia?.let { media ->
            val file = java.io.File(media.path)
            android.util.Log.d("PdfViewerScreen", "Media selected: ${media.name}, type: ${media.type}, path: ${media.path}")
            android.util.Log.d("PdfViewerScreen", "File exists: ${file.exists()}, size: ${file.length()} bytes")
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Viewer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Debug: Manual extraction test button
                    IconButton(
                        onClick = {
                            android.util.Log.d("PdfViewerScreen", "Manual extraction test triggered")
                            // This will trigger re-extraction when PDF path changes
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Media")
                    }
                    
                    if (mediaFiles.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                android.util.Log.d("PdfViewerScreen", "Media files count: ${mediaFiles.size}")
                            }
                        ) {
                            Icon(Icons.Default.PlayCircle, contentDescription = "Media Files")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PdfViewer(
                pdfPath = pdfPath,
                modifier = Modifier.fillMaxSize(),
                onMediaFound = { mediaFile ->
                    android.util.Log.d("PdfViewerScreen", "onMediaFound called: ${mediaFile.name}, type: ${mediaFile.type}, path: ${mediaFile.path}")
                    val file = java.io.File(mediaFile.path)
                    android.util.Log.d("PdfViewerScreen", "File exists: ${file.exists()}, size: ${file.length()}")
                    // Add media file if not already present (avoid duplicates)
                    if (!mediaFiles.any { it.path == mediaFile.path && 
                        it.annotationRect?.let { rect ->
                            mediaFile.annotationRect?.let { newRect ->
                                rect.left == newRect.left && 
                                rect.top == newRect.top && 
                                rect.right == newRect.right && 
                                rect.bottom == newRect.bottom
                            } ?: false
                        } ?: (it.annotationRect == null && mediaFile.annotationRect == null)
                    }) {
                        mediaFiles = mediaFiles + mediaFile
                    }
                    android.util.Log.d("PdfViewerScreen", "Total media files: ${mediaFiles.size}")
                },
                onPageChanged = { pageIndex ->
                    // Clear media files when page changes (new page will populate its own media)
                    android.util.Log.d("PdfViewerScreen", "Page changed to ${pageIndex + 1}, clearing media files")
                    mediaFiles = emptyList()
                    selectedMedia = null
                },
                onMediaClick = { mediaFile ->
                    android.util.Log.d("PdfViewerScreen", "Media clicked: ${mediaFile.name}, type: ${mediaFile.type}")
                    android.util.Log.d("PdfViewerScreen", "  Path: ${mediaFile.path}")
                    android.util.Log.d("PdfViewerScreen", "  startTime: ${mediaFile.startTime}, endTime: ${mediaFile.endTime}")
                    android.util.Log.d("PdfViewerScreen", "  annotationRect: ${mediaFile.annotationRect}")
                    // Only play audio/video, not images
                    if (mediaFile.type == MediaType.AUDIO || mediaFile.type == MediaType.VIDEO) {
                        selectedMedia = mediaFile
                    }
                }
            )
            
            // Show media players at the bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                selectedMedia?.let { media ->
                    when (media.type) {
                        MediaType.AUDIO -> {
                            android.util.Log.d("PdfViewerScreen", "Creating AudioPlayer for: ${media.name}")
                            android.util.Log.d("PdfViewerScreen", "  startTime: ${media.startTime}, endTime: ${media.endTime}")
                            AudioPlayer(
                                audioPath = media.path,
                                modifier = Modifier.padding(8.dp),
                                onDismiss = { selectedMedia = null },
                                startTime = media.startTime,
                                endTime = media.endTime
                            )
                        }
                        MediaType.VIDEO -> {
                            VideoPlayer(
                                videoPath = media.path,
                                modifier = Modifier.padding(8.dp),
                                onDismiss = { selectedMedia = null }
                            )
                        }
                        MediaType.IMAGE -> {
                            // Image can be shown in a dialog or overlay
                        }
                    }
                }
            }
            
        }
    }
}


