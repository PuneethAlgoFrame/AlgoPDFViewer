package `in`.algoframe.pdfviewer.ui.components

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import java.io.File

@Composable
fun VideoPlayer(
    videoPath: String,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Verify file exists
    val file = remember(videoPath) { File(videoPath) }
    val fileExists = remember(videoPath) { file.exists() && file.length() > 0 }
    
    val exoPlayer = remember(videoPath) {
        ExoPlayer.Builder(context).build().apply {
            try {
                if (!fileExists) {
                    errorMessage = "File not found: ${file.name}"
                    Log.e("VideoPlayer", "File does not exist: $videoPath")
                    return@apply
                }
                
                Log.d("VideoPlayer", "Loading video file: $videoPath (${file.length()} bytes)")
                
                // Handle both file:// and direct file paths
                val uri = if (videoPath.startsWith("file://")) {
                    Uri.parse(videoPath)
                } else {
                    Uri.fromFile(file)
                }
                
                Log.d("VideoPlayer", "URI: $uri")
                
                val mediaItem = MediaItem.fromUri(uri)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
                
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        errorMessage = "Playback error: ${error.message}"
                        Log.e("VideoPlayer", "Player error", error)
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                Log.d("VideoPlayer", "Player ready")
                            }
                            Player.STATE_BUFFERING -> {
                                Log.d("VideoPlayer", "Player buffering")
                            }
                            Player.STATE_ENDED -> {
                                Log.d("VideoPlayer", "Player ended")
                                // Close the player after playback ends
                                Handler(Looper.getMainLooper()).postDelayed({
                                    onDismiss()
                                }, 500) // Small delay to allow UI to update
                            }
                            Player.STATE_IDLE -> {
                                Log.d("VideoPlayer", "Player idle")
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                errorMessage = "Error: ${e.message}"
                Log.e("VideoPlayer", "Error setting up video player for $videoPath", e)
            }
        }
    }
    
    // Update player when path changes
    LaunchedEffect(videoPath) {
        Log.d("VideoPlayer", "LaunchedEffect triggered for: $videoPath")
        val currentFile = File(videoPath)
        val exists = currentFile.exists()
        val size = if (exists) currentFile.length() else 0L
        
        Log.d("VideoPlayer", "File check - exists: $exists, size: $size bytes")
        
        if (!exists || size == 0L) {
            errorMessage = "File not found or empty: ${currentFile.name}"
            Log.e("VideoPlayer", "Cannot play: file doesn't exist or is empty")
            return@LaunchedEffect
        }
        
        try {
            // Use FileProvider URI for better compatibility
            val uri = try {
                Uri.fromFile(currentFile)
            } catch (e: Exception) {
                Uri.parse("file://$videoPath")
            }
            
            Log.d("VideoPlayer", "Creating MediaItem with URI: $uri")
            val mediaItem = MediaItem.fromUri(uri)
            
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.playWhenReady = true
            exoPlayer.prepare()
            
            Log.d("VideoPlayer", "Media item prepared successfully")
        } catch (e: Exception) {
            errorMessage = "Error loading: ${e.message}"
            Log.e("VideoPlayer", "Error updating media item", e)
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

