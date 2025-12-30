package com.algoframe.pdfreader.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView

@Composable
fun VideoPlayer(
    videoPath: String,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Verify file exists
    val file = remember(videoPath) { java.io.File(videoPath) }
    val fileExists = remember(videoPath) { file.exists() && file.length() > 0 }
    
    val exoPlayer = remember(videoPath) {
        ExoPlayer.Builder(context).build().apply {
            try {
                if (!fileExists) {
                    errorMessage = "File not found: ${file.name}"
                    android.util.Log.e("VideoPlayer", "File does not exist: $videoPath")
                    return@apply
                }
                
                android.util.Log.d("VideoPlayer", "Loading video file: $videoPath (${file.length()} bytes)")
                
                // Handle both file:// and direct file paths
                val uri = if (videoPath.startsWith("file://")) {
                    android.net.Uri.parse(videoPath)
                } else {
                    android.net.Uri.fromFile(file)
                }
                
                android.util.Log.d("VideoPlayer", "URI: $uri")
                
                val mediaItem = MediaItem.fromUri(uri)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
                
                addListener(object : com.google.android.exoplayer2.Player.Listener {
                    override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
                        errorMessage = "Playback error: ${error.message}"
                        android.util.Log.e("VideoPlayer", "Player error", error)
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            com.google.android.exoplayer2.Player.STATE_READY -> {
                                android.util.Log.d("VideoPlayer", "Player ready")
                            }
                            com.google.android.exoplayer2.Player.STATE_BUFFERING -> {
                                android.util.Log.d("VideoPlayer", "Player buffering")
                            }
                            com.google.android.exoplayer2.Player.STATE_ENDED -> {
                                android.util.Log.d("VideoPlayer", "Player ended")
                                // Close the player after playback ends
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    onDismiss()
                                }, 500) // Small delay to allow UI to update
                            }
                            com.google.android.exoplayer2.Player.STATE_IDLE -> {
                                android.util.Log.d("VideoPlayer", "Player idle")
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                errorMessage = "Error: ${e.message}"
                android.util.Log.e("VideoPlayer", "Error setting up video player for $videoPath", e)
            }
        }
    }
    
    // Update player when path changes
    LaunchedEffect(videoPath) {
        android.util.Log.d("VideoPlayer", "LaunchedEffect triggered for: $videoPath")
        val currentFile = java.io.File(videoPath)
        val exists = currentFile.exists()
        val size = if (exists) currentFile.length() else 0L
        
        android.util.Log.d("VideoPlayer", "File check - exists: $exists, size: $size bytes")
        
        if (!exists || size == 0L) {
            errorMessage = "File not found or empty: ${currentFile.name}"
            android.util.Log.e("VideoPlayer", "Cannot play: file doesn't exist or is empty")
            return@LaunchedEffect
        }
        
        try {
            // Use FileProvider URI for better compatibility
            val uri = try {
                android.net.Uri.fromFile(currentFile)
            } catch (e: Exception) {
                android.net.Uri.parse("file://$videoPath")
            }
            
            android.util.Log.d("VideoPlayer", "Creating MediaItem with URI: $uri")
            val mediaItem = MediaItem.fromUri(uri)
            
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.playWhenReady = true
            exoPlayer.prepare()
            
            android.util.Log.d("VideoPlayer", "Media item prepared successfully")
        } catch (e: Exception) {
            errorMessage = "Error loading: ${e.message}"
            android.util.Log.e("VideoPlayer", "Error updating media item", e)
            e.printStackTrace()
        }
    }
    
    DisposableEffect(videoPath) {
        onDispose {
            exoPlayer.release()
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Video Player",
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
            
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            
            if (!fileExists) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "File not found: ${file.name}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        useController = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
        }
    }
}

