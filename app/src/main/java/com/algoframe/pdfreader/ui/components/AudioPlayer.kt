package com.algoframe.pdfreader.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.PlaybackParameters
import androidx.core.net.toUri

@Composable
fun AudioPlayer(
    audioPath: String,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    startTime: Long? = null,  // Start time in milliseconds (for audio segments)
    endTime: Long? = null     // End time in milliseconds (for audio segments)
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Calculate effective start/end times
    val effectiveStartTime = startTime ?: 0L
    val effectiveEndTime = endTime
    
    // Verify file exists
    val file = remember(audioPath) { java.io.File(audioPath) }
    val fileExists = remember(audioPath) { file.exists() && file.length() > 0 }
    
    val exoPlayer = remember(audioPath) {
        ExoPlayer.Builder(context).build().apply {
            // Configure audio attributes to minimize processing and avoid distortion
            // Use CONTENT_TYPE_UNKNOWN to bypass audio processing effects that might cause distortion
            // Use USAGE_MEDIA for standard media playback
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_UNKNOWN)
                .setUsage(C.USAGE_MEDIA)
                .build()
            setAudioAttributes(audioAttributes, true)
            android.util.Log.d("AudioPlayer", "AudioAttributes configured: contentType=UNKNOWN, usage=MEDIA")
            
            // Ensure playback speed is normal (1.0x) with pitch correction disabled
            // Explicitly set both speed and pitch to 1.0f to avoid any speed-related distortion
            playbackParameters = PlaybackParameters(1.0f, 1.0f)
            android.util.Log.d("AudioPlayer", "Set playback speed to 1.0x, pitch to 1.0f")
            
            // Set volume to ensure it's at normal level
            volume = 1.0f
            android.util.Log.d("AudioPlayer", "Volume set to 1.0f")
            
            try {
                if (!fileExists) {
                    errorMessage = "File not found: ${file.name}"
                    android.util.Log.e("AudioPlayer", "File does not exist: $audioPath")
                    return@apply
                }
                
                android.util.Log.d("AudioPlayer", "Loading audio file: $audioPath (${file.length()} bytes)")
                
                // Handle both file:// and direct file paths
                val uri = if (audioPath.startsWith("file://")) {
                    audioPath.toUri()
                } else {
                    android.net.Uri.fromFile(file)
                }
                
                android.util.Log.d("AudioPlayer", "URI: $uri")
                if (startTime != null || endTime != null) {
                    android.util.Log.d("AudioPlayer", "=== AUDIO SEGMENT PLAYBACK ===")
                    android.util.Log.d("AudioPlayer", "Time range: ${startTime ?: 0}ms - ${endTime ?: "end"}ms")
                    android.util.Log.d("AudioPlayer", "Will seek to start time: ${startTime ?: 0}ms")
                } else {
                    android.util.Log.d("AudioPlayer", "=== FULL AUDIO PLAYBACK ===")
                    android.util.Log.d("AudioPlayer", "No time range specified, playing full file")
                }
                
                val mediaItem = MediaItem.fromUri(uri)
                setMediaItem(mediaItem)
                // Don't play immediately - wait for STATE_READY
                playWhenReady = false
                prepare()
                
                // Don't seek here - wait for STATE_READY callback
                
                val player = this
                addListener(object : com.google.android.exoplayer2.Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                        android.util.Log.d("AudioPlayer", "Playing state changed: $playing")
                    }
                    override fun onTracksChanged(tracks: com.google.android.exoplayer2.Tracks) {
                        // Log audio track information to diagnose format issues
                        android.util.Log.d("AudioPlayer", "=== AUDIO TRACK INFO ===")
                        var audioTrackCount = 0
                        for (trackGroup in tracks.groups) {
                            if (trackGroup.type == C.TRACK_TYPE_AUDIO) {
                                for (i in 0 until trackGroup.length) {
                                    val format = trackGroup.getTrackFormat(i)
                                    val isSelected = trackGroup.isTrackSelected(i)
                                    android.util.Log.d("AudioPlayer", "Audio Track $audioTrackCount:")
                                    android.util.Log.d("AudioPlayer", "  - Sample rate: ${format.sampleRate} Hz")
                                    android.util.Log.d("AudioPlayer", "  - Channels: ${format.channelCount}")
                                    android.util.Log.d("AudioPlayer", "  - Encoding: ${format.sampleMimeType}")
                                    android.util.Log.d("AudioPlayer", "  - Bitrate: ${format.bitrate} bps")
                                    android.util.Log.d("AudioPlayer", "  - Codec: ${format.codecs}")
                                    android.util.Log.d("AudioPlayer", "  - Selected: $isSelected")
                                    audioTrackCount++
                                }
                            }
                        }
                        android.util.Log.d("AudioPlayer", "Total audio tracks: $audioTrackCount")
                        android.util.Log.d("AudioPlayer", "========================")
                    }
                    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                        android.util.Log.d("AudioPlayer", "Playback parameters changed: speed=${playbackParameters.speed}x, pitch=${playbackParameters.pitch}")
                        // If speed is not 1.0x, reset it
                        if (playbackParameters.speed != 1.0f || playbackParameters.pitch != 1.0f) {
                            android.util.Log.w("AudioPlayer", "Detected speed/pitch change, resetting to 1.0x/1.0f")
                            player.playbackParameters = PlaybackParameters(1.0f, 1.0f)
                        }
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            com.google.android.exoplayer2.Player.STATE_READY -> {
                                android.util.Log.d("AudioPlayer", "Player ready, duration: ${player.duration}ms")
                                // Log current audio format if available (ExoPlayer 2.19+)
                                try {
                                    val currentTracks = player.currentTracks
                                    for (trackGroup in currentTracks.groups) {
                                        if (trackGroup.type == C.TRACK_TYPE_AUDIO) {
                                            for (i in 0 until trackGroup.length) {
                                                if (trackGroup.isTrackSelected(i)) {
                                                    val format = trackGroup.getTrackFormat(i)
                                                    android.util.Log.d("AudioPlayer", "Current audio format: sampleRate=${format.sampleRate}Hz, channels=${format.channelCount}, encoding=${format.sampleMimeType}")
                                                    break
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.d("AudioPlayer", "Could not get audio format info: ${e.message}")
                                }
                                // Ensure playback speed is correct (force reset to 1.0x)
                                val currentParams = player.playbackParameters
                                if (currentParams.speed != 1.0f || currentParams.pitch != 1.0f) {
                                    android.util.Log.w("AudioPlayer", "Playback speed was ${currentParams.speed}x, pitch was ${currentParams.pitch}, resetting to 1.0x/1.0f")
                                    player.playbackParameters = PlaybackParameters(1.0f, 1.0f)
                                } else {
                                    android.util.Log.d("AudioPlayer", "Playback speed verified: ${currentParams.speed}x, pitch: ${currentParams.pitch}")
                                }
                                // Seek to start time first if specified, then start playing
                                if (startTime != null && startTime > 0) {
                                    android.util.Log.d("AudioPlayer", "Seeking to start time: ${startTime}ms")
                                    // Ensure playback is paused during seek to avoid buffer issues
                                    player.playWhenReady = false
                                    player.pause()
                                    // Use seekTo with exact positioning
                                    player.seekTo(startTime)
                                    // Wait for seek to complete and buffer to be ready before starting playback
                                    // This prevents distortion from playing before audio is buffered
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        // Check if player is still ready and at correct position
                                        if (player.playbackState == com.google.android.exoplayer2.Player.STATE_READY) {
                                            val currentPos = player.currentPosition
                                            val positionDiff = kotlin.math.abs(currentPos - startTime)
                                            android.util.Log.d("AudioPlayer", "After seek delay: position=${currentPos}ms, diff=${positionDiff}ms")
                                            
                                            if (positionDiff < 200) {
                                                // Position is close enough, start playback
                                                android.util.Log.d("AudioPlayer", "Starting playback after successful seek")
                                                // Ensure speed is still 1.0x before playing
                                                player.playbackParameters = PlaybackParameters(1.0f, 1.0f)
                                                player.playWhenReady = true
                                                player.play()
                                            } else {
                                                // Position not close enough, seek again with longer delay
                                                android.util.Log.w("AudioPlayer", "Seek position not accurate (diff=$positionDiff), re-seeking")
                                                player.seekTo(startTime)
                                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                                    if (player.playbackState == com.google.android.exoplayer2.Player.STATE_READY) {
                                                        player.playbackParameters = PlaybackParameters(1.0f, 1.0f)
                                                        player.playWhenReady = true
                                                        player.play()
                                                    }
                                                }, 300)
                                            }
                                        }
                                    }, 300) // Increased delay to allow buffer to fill
                                } else {
                                    // No start time, just start playing
                                    android.util.Log.d("AudioPlayer", "No start time, starting playback immediately")
                                    // Ensure speed is 1.0x before playing
                                    player.playbackParameters = PlaybackParameters(1.0f, 1.0f)
                                    player.playWhenReady = true
                                    player.play()
                                }
                                // Sync playing state
                                isPlaying = player.isPlaying
                            }
                            com.google.android.exoplayer2.Player.STATE_BUFFERING -> {
                                android.util.Log.d("AudioPlayer", "Player buffering")
                            }
                            com.google.android.exoplayer2.Player.STATE_ENDED -> {
                                android.util.Log.d("AudioPlayer", "Player ended")
                                isPlaying = false
                                // Close the player after playback ends
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    onDismiss()
                                }, 500) // Small delay to allow UI to update
                            }
                            com.google.android.exoplayer2.Player.STATE_IDLE -> {
                                android.util.Log.d("AudioPlayer", "Player idle")
                            }
                        }
                    }
                    override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
                        errorMessage = "Playback error: ${error.message}"
                        android.util.Log.e("AudioPlayer", "Player error", error)
                    }
                })
            } catch (e: Exception) {
                errorMessage = "Error: ${e.message}"
                android.util.Log.e("AudioPlayer", "Error setting up audio player for $audioPath", e)
            }
        }
    }
    
    // Update player when path changes
    LaunchedEffect(audioPath) {
        android.util.Log.d("AudioPlayer", "LaunchedEffect triggered for: $audioPath")
        val currentFile = java.io.File(audioPath)
        val exists = currentFile.exists()
        val size = if (exists) currentFile.length() else 0L
        
        android.util.Log.d("AudioPlayer", "File check - exists: $exists, size: $size bytes")
        
        if (!exists || size == 0L) {
            errorMessage = "File not found or empty: ${currentFile.name}"
            android.util.Log.e("AudioPlayer", "Cannot play: file doesn't exist or is empty")
            return@LaunchedEffect
        }
        
        try {
            // Use FileProvider URI for better compatibility
            val uri = try {
                android.net.Uri.fromFile(currentFile)
            } catch (_: Exception) {
                "file://$audioPath".toUri()
            }
            
            android.util.Log.d("AudioPlayer", "Creating MediaItem with URI: $uri")
            if (startTime != null || endTime != null) {
                android.util.Log.d("AudioPlayer", "Time range: ${startTime ?: 0}ms - ${endTime ?: "end"}ms")
            }
            val mediaItem = MediaItem.fromUri(uri)
            
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.setMediaItem(mediaItem)
            // Ensure audio attributes are still set correctly
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_UNKNOWN)
                .setUsage(C.USAGE_MEDIA)
                .build()
            exoPlayer.setAudioAttributes(audioAttributes, true)
            android.util.Log.d("AudioPlayer", "Reset AudioAttributes in LaunchedEffect: contentType=UNKNOWN, usage=MEDIA")
            // Ensure playback speed is normal (explicitly set speed and pitch)
            exoPlayer.playbackParameters = PlaybackParameters(1.0f, 1.0f)
            android.util.Log.d("AudioPlayer", "Reset playback speed to 1.0x, pitch to 1.0f in LaunchedEffect")
            // Ensure volume is normal
            exoPlayer.volume = 1.0f
            android.util.Log.d("AudioPlayer", "Reset volume to 1.0f in LaunchedEffect")
            // Don't play immediately - wait for STATE_READY
            exoPlayer.playWhenReady = false
            exoPlayer.prepare()
            
            // Reset playing state when media changes
            isPlaying = false
            
            // Don't seek here - wait for STATE_READY callback
            
            android.util.Log.d("AudioPlayer", "Media item prepared successfully")
        } catch (e: Exception) {
            errorMessage = "Error loading: ${e.message}"
            android.util.Log.e("AudioPlayer", "Error updating media item", e)
            e.printStackTrace()
        }
    }
    
    DisposableEffect(audioPath, startTime, endTime) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val updateRunnable = object : Runnable {
            override fun run() {
                val pos = exoPlayer.currentPosition
                val dur = exoPlayer.duration
                
                // Check if we've reached the end time
                if (effectiveEndTime != null && pos >= effectiveEndTime && isPlaying) {
                    exoPlayer.pause()
                    isPlaying = false
                    android.util.Log.d("AudioPlayer", "Reached end time: ${effectiveEndTime}ms")
                    // Close the player after reaching end time
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        onDismiss()
                    }, 500) // Small delay to allow UI to update
                }
                
                // Check if playback has ended naturally (reached end of file)
                if (exoPlayer.playbackState == com.google.android.exoplayer2.Player.STATE_ENDED && isPlaying) {
                    isPlaying = false
                    android.util.Log.d("AudioPlayer", "Playback ended naturally")
                    // Close the player after playback ends
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        onDismiss()
                    }, 500) // Small delay to allow UI to update
                }
                
                // Adjust position/duration display for segments
                currentPosition = if (effectiveStartTime > 0) {
                    maxOf(0, pos - effectiveStartTime)
                } else {
                    pos
                }
                
                duration = when {
                    effectiveEndTime != null && effectiveStartTime != null -> effectiveEndTime - effectiveStartTime
                    effectiveEndTime != null -> effectiveEndTime
                    effectiveStartTime > 0 && dur > 0 -> dur - effectiveStartTime
                    else -> dur
                }
                
                handler.postDelayed(this, 100)
            }
        }
        handler.post(updateRunnable)
        
        onDispose {
            handler.removeCallbacks(updateRunnable)
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
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Audio Player",
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
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            if (!fileExists) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "File not found: ${file.name}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val newPos = maxOf(effectiveStartTime, exoPlayer.currentPosition - 10000)
                        exoPlayer.seekTo(newPos)
                    }
                ) {
                    Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s")
                }
                
                IconButton(
                    onClick = {
                        if (isPlaying) {
                            exoPlayer.pause()
                        } else {
                            // Ensure we're at or after start time
                            if (effectiveStartTime > 0 && exoPlayer.currentPosition < effectiveStartTime) {
                                exoPlayer.seekTo(effectiveStartTime)
                            }
                            exoPlayer.play()
                        }
                        // Don't manually toggle - let onIsPlayingChanged listener handle it
                    },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(48.dp)
                    )
                }
                
                IconButton(
                    onClick = {
                        val newPos = exoPlayer.currentPosition + 10000
                        val maxPos = effectiveEndTime ?: exoPlayer.duration
                        exoPlayer.seekTo(minOf(newPos, maxPos))
                    }
                ) {
                    Icon(Icons.Default.Forward10, contentDescription = "Forward 10s")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = {
                    if (duration > 0) {
                        (currentPosition.toFloat() / duration.toFloat())
                    } else {
                        0f
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(currentPosition),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = formatTime(duration),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

