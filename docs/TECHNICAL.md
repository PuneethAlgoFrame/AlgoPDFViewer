# Technical Documentation - PDF Reader

Comprehensive technical documentation for the PDF Reader Android application.

> **📚 Documentation Index**: See [INDEX.md](./INDEX.md) for complete documentation navigation.

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Technology Stack](#technology-stack)
- [Component Details](#component-details)
- [Data Models](#data-models)
- [Media Extraction System](#media-extraction-system)
- [PDF Rendering](#pdf-rendering)
- [Media Playback](#media-playback)
- [State Management](#state-management)
- [File Management](#file-management)
- [Permission Handling](#permission-handling)
- [Performance Considerations](#performance-considerations)
- [API Reference](#api-reference)
- [Implementation Details](#implementation-details)
- [Testing Strategy](#testing-strategy)

## Architecture Overview

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    MainActivity                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │            PDFReaderApp (Composable)              │  │
│  │  ┌──────────────────┐  ┌──────────────────────┐ │  │
│  │  │ FilePickerScreen │  │  PdfViewerScreen     │ │  │
│  │  └──────────────────┘  └──────────────────────┘ │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        │               │               │
┌───────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐
│ PdfViewer    │ │ PdfMedia    │ │ RecentPdf   │
│ Component    │ │ Extractor   │ │ Manager     │
└──────────────┘ └─────────────┘ └─────────────┘
        │               │               │
        └───────────────┼───────────────┘
                        │
        ┌───────────────┼───────────────┐
        │               │               │
┌───────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐
│ AudioPlayer  │ │ VideoPlayer │ │ MediaFile   │
│ Component    │ │ Component   │ │ Data Model  │
└──────────────┘ └─────────────┘ └─────────────┘
```

### Architecture Patterns

1. **Compose UI Pattern**: All UI built with declarative Compose functions
2. **State Hoisting**: State managed at screen level, passed down to components
3. **Separation of Concerns**: Clear boundaries between UI, data, and business logic
4. **Coroutine-based Async**: All async operations use Kotlin Coroutines

## Technology Stack

### Core Technologies

| Technology | Version | Purpose |
|------------|---------|---------|
| Kotlin | 2.2.21 | Programming language |
| Android Gradle Plugin | 8.13.2 | Build system |
| Jetpack Compose | 2025.12.00 | UI framework |
| Material Design 3 | Latest | Design system |

### Key Libraries

| Library | Version | Purpose |
|---------|---------|---------|
| PDFBox Android | 2.0.27.0 | PDF parsing and media extraction |
| ExoPlayer | 2.19.1 | Audio/video playback |
| Coil | 2.5.0 | Image loading |
| Accompanist Permissions | 0.34.0 | Permission handling |
| Activity Result API | 1.3.1 | File picking |
| Material Icons Extended | 1.7.8 | UI icons |

**Note**: PDF rendering uses Android's native `PdfRenderer` API - no external PDF viewer library is required.

### Android APIs

- **PdfRenderer**: Native Android API for PDF rendering (no external library needed)
- **MediaStore**: File access on Android 10+
- **SharedPreferences**: Recent PDFs storage
- **ContentResolver**: File URI handling
- **Activity Result API**: File picker integration

## Component Details

### MainActivity

**Location**: `in.algoframe.pdfreader.MainActivity`

**Purpose**: Application entry point and root composable setup.

**Key Responsibilities**:
- Initialize Compose UI
- Set up edge-to-edge display
- Manage app-level navigation state

**Code Structure**:
```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PDFReaderTheme {
                PDFReaderApp()
            }
        }
    }
}
```

**State Management**:
- Uses `remember { mutableStateOf<String?>(null) }` for selected PDF path
- Conditional rendering based on PDF selection state

### PDFReaderApp

**Location**: `in.algoframe.pdfreader.MainActivity.PDFReaderApp`

**Purpose**: Root composable that manages screen navigation.

**Navigation Logic**:
- Shows `FilePickerScreen` when no PDF is selected
- Shows `PdfViewerScreen` when PDF is selected
- Handles back navigation

### FilePickerScreen

**Location**: `in.algoframe.pdfreader.ui.screens.FilePickerScreen`

**Purpose**: Screen for PDF file selection and recent PDFs display.

**Key Features**:
- Permission handling with Accompanist Permissions
- File picker integration using Activity Result API
- Recent PDFs list display
- Storage access management

**Permission Handling**:
```kotlin
val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    listOf(
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO
    )
} else {
    listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}
```

**File Picker Integration**:
- Uses `ActivityResultContracts.GetContent()` for file selection
- Handles URI to file path conversion
- Saves selected PDF to recent list

### PdfViewerScreen

**Location**: `in.algoframe.pdfreader.ui.screens.PdfViewerScreen`

**Purpose**: Main PDF viewing screen with media playback.

**State Management**:
- `mediaFiles`: List of extracted media files for current page
- `selectedMedia`: Currently playing media file
- Page-based media extraction

**Media Playback Logic**:
- Extracts media files per page
- Handles media click events
- Shows appropriate player (Audio/Video) based on media type
- Clears media when page changes

### PdfViewer Component

**Location**: `in.algoframe.pdfreader.ui.components.PdfViewer`

**Purpose**: Core PDF rendering component.

**Rendering Strategy**:
- Uses Android's `PdfRenderer` API
- Renders pages as bitmaps (2x scale for quality)
- Caches rendered pages in memory
- Handles page navigation

**Media Detection**:
- Extracts media files when page is displayed
- Calls `onMediaFound` callback for each media file
- Handles tap gestures on media annotations
- Maps PDF coordinates to Android view coordinates

**Key Implementation Details**:
```kotlin
// PDF Rendering
val pdfRenderer = PdfRenderer(fileDescriptor)
val page = pdfRenderer.openPage(pageIndex)
val bitmap = Bitmap.createBitmap(
    pageWidth * 2,
    pageHeight * 2,
    Bitmap.Config.ARGB_8888
)
page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
```

## Data Models

### MediaFile

**Location**: `in.algoframe.pdfreader.data.MediaFile`

**Purpose**: Represents an extracted media file from a PDF.

**Properties**:
```kotlin
data class MediaFile(
    val name: String,                    // File name
    val path: String,                    // Full file path
    val type: MediaType,                 // AUDIO, VIDEO, or IMAGE
    val pageNumber: Int? = null,         // Page where media was found
    val annotationRect: AnnotationRect? = null,  // PDF coordinates
    val startTime: Long? = null,         // Start time in milliseconds
    val endTime: Long? = null           // End time in milliseconds
)
```

**Usage**:
- Created by `PdfMediaExtractor` during extraction
- Used by UI components to display and play media
- Contains coordinate information for click detection

### AnnotationRect

**Location**: `in.algoframe.pdfreader.data.AnnotationRect`

**Purpose**: Represents a rectangle in PDF coordinates.

**Properties**:
```kotlin
data class AnnotationRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)
```

**Coordinate Conversion**:
- PDF coordinates: Origin at bottom-left, Y increases upward
- Android coordinates: Origin at top-left, Y increases downward
- `contains()` method handles coordinate conversion

### AudioSegmentMapping

**Location**: `in.algoframe.pdfreader.data.AudioSegmentMapping`

**Purpose**: External mapping for audio segments with time ranges.

**Structure**:
```kotlin
data class AudioSegmentMapping(
    val pdfFileName: String,
    val pages: List<PageSegments>
)

data class PageSegments(
    val pageNumber: Int,
    val segments: List<SegmentMapping>
)

data class SegmentMapping(
    val rect: FloatArray,    // [left, top, right, bottom]
    val startTime: Long,     // milliseconds
    val endTime: Long        // milliseconds
)
```

**Matching Logic**:
- Uses tolerance-based matching for rectangle coordinates
- Accounts for floating-point precision issues
- Matches segments to annotations by coordinate proximity

## Media Extraction System

### PdfMediaExtractor

**Location**: `in.algoframe.pdfreader.utils.PdfMediaExtractor`

**Purpose**: Extracts embedded media files from PDF documents.

### Extraction Process

1. **PDF Loading**:
   ```kotlin
   val document = PDDocument.load(pdfFile)
   val page = document.getPage(pageIndex)
   ```

2. **Annotation Processing**:
   - Iterates through page annotations
   - Identifies media annotations (RichMedia, Screen, etc.)
   - Extracts embedded file streams

3. **Media File Extraction**:
   - Extracts file content from PDF streams
   - Saves to cache directory
   - Creates `MediaFile` objects with metadata

4. **Audio Segment Mapping**:
   - Loads external JSON mapping if available
   - Matches annotations to segments by coordinates
   - Applies time ranges to audio files

### Supported Media Types

- **Audio**: MP3, WAV, AAC, OGG
- **Video**: MP4, AVI, MOV
- **Images**: JPEG, PNG, GIF, BMP

### Extraction Flow

```
PDF File
    │
    ├─► Load PDF Document (PDFBox)
    │
    ├─► Get Page Annotations
    │
    ├─► Identify Media Annotations
    │
    ├─► Extract File Streams
    │
    ├─► Save to Cache Directory
    │
    ├─► Load Audio Segment Mapping (if available)
    │
    ├─► Match Annotations to Segments
    │
    └─► Return List<MediaFile>
```

### Cache Management

- Media files extracted to: `{cacheDir}/pdf_media/`
- Files named with hash to avoid conflicts
- Old files cleaned up on app restart (optional)

## PDF Rendering

### Rendering Pipeline

1. **File Access**:
   ```kotlin
   val fileDescriptor = ParcelFileDescriptor.open(
       file, 
       ParcelFileDescriptor.MODE_READ_ONLY
   )
   ```

2. **Renderer Creation**:
   ```kotlin
   val pdfRenderer = PdfRenderer(fileDescriptor)
   ```

3. **Page Rendering**:
   ```kotlin
   val page = pdfRenderer.openPage(pageIndex)
   val bitmap = Bitmap.createBitmap(
       pageWidth * 2,
       pageHeight * 2,
       Bitmap.Config.ARGB_8888
   )
   page.render(bitmap, null, null, RENDER_MODE_FOR_DISPLAY)
   ```

4. **Display**:
   - Convert bitmap to Compose `ImageBitmap`
   - Display in `Image` composable
   - Handle scaling and scrolling

### Coordinate Mapping

**PDF Coordinates → Android Coordinates**:

```kotlin
// PDF: (0,0) at bottom-left, Y increases upward
// Android: (0,0) at top-left, Y increases downward

val androidY = pageHeight - pdfY
```

**Annotation Click Detection**:

```kotlin
fun contains(x: Float, y: Float, pageHeight: Float): Boolean {
    val androidY = pageHeight - maxOf(top, bottom)
    val androidBottom = pageHeight - minOf(top, bottom)
    // Check if click is within bounds
}
```

### Performance Optimizations

- **Bitmap Scaling**: 2x scale for quality vs. memory trade-off
- **Page Caching**: Cache rendered pages in memory
- **Lazy Loading**: Render pages on-demand
- **Memory Management**: Recycle bitmaps when not needed

## Media Playback

### AudioPlayer

**Location**: `in.algoframe.pdfreader.ui.components.AudioPlayer`

**Technology**: ExoPlayer 2.19.1

**Features**:
- Play/pause controls
- 10-second rewind/forward
- Progress indicator
- Time range support (start/end times)
- Segment playback

**Configuration**:
```kotlin
val audioAttributes = AudioAttributes.Builder()
    .setContentType(C.CONTENT_TYPE_UNKNOWN)
    .setUsage(C.USAGE_MEDIA)
    .build()
exoPlayer.setAudioAttributes(audioAttributes, true)
exoPlayer.playbackParameters = PlaybackParameters(1.0f, 1.0f)
```

**Segment Playback**:
- Seeks to `startTime` when media loads
- Stops at `endTime` if specified
- Adjusts progress bar for segment duration

### VideoPlayer

**Location**: `in.algoframe.pdfreader.ui.components.VideoPlayer`

**Technology**: ExoPlayer with PlayerView

**Features**:
- Full video playback
- Standard ExoPlayer controls
- Error handling
- File validation

**Implementation**:
```kotlin
AndroidView(
    factory = { ctx ->
        PlayerView(ctx).apply {
            player = exoPlayer
            useController = true
        }
    }
)
```

### Playback State Management

- Uses ExoPlayer's `Player.Listener` for state updates
- Tracks playing state with `onIsPlayingChanged`
- Handles errors with `onPlayerError`
- Manages lifecycle with `DisposableEffect`

## State Management

### Compose State

**State Hoisting Pattern**:
- State managed at screen level
- Passed down to child components
- Callbacks for state updates

**Example**:
```kotlin
@Composable
fun PdfViewerScreen(pdfPath: String) {
    var mediaFiles by remember { mutableStateOf<List<MediaFile>>(emptyList()) }
    var selectedMedia by remember { mutableStateOf<MediaFile?>(null) }
    
    PdfViewer(
        pdfPath = pdfPath,
        onMediaFound = { mediaFile ->
            mediaFiles = mediaFiles + mediaFile
        },
        onMediaClick = { mediaFile ->
            selectedMedia = mediaFile
        }
    )
}
```

### State Persistence

**Recent PDFs**:
- Stored in `SharedPreferences`
- Persisted across app restarts
- Limited to 20 most recent files

**No State Persistence**:
- Media files: Extracted on-demand, cached temporarily
- Current page: Not persisted (starts at page 1)
- Playback state: Not persisted

## File Management

### File Access Strategies

**Android 9 and Below**:
- Direct file path access
- `READ_EXTERNAL_STORAGE` permission
- File system traversal

**Android 10+**:
- Scoped storage
- MediaStore API for file discovery
- ContentResolver for file access
- `READ_MEDIA_VIDEO` and `READ_MEDIA_AUDIO` permissions

### URI Handling

**Content URI to File Path**:
```kotlin
fun getPathFromUri(context: Context, uri: Uri): String? {
    if (uri.scheme == "content") {
        // Copy to cache directory
        val cursor = context.contentResolver.query(uri, ...)
        // Extract file name and copy stream
    } else if (uri.scheme == "file") {
        return uri.path
    }
}
```

### Cache Management

**Media Cache**:
- Location: `{cacheDir}/pdf_media/`
- Naming: Hash-based to avoid conflicts
- Cleanup: Manual (not automatic)

**Audio Segment Mapping Cache**:
- Location: `{cacheDir}/audio_segments/`
- Format: JSON files
- Naming: `{pdfFileName}.json`

## Permission Handling

### Permission Strategy

**Runtime Permissions**:
- Requested on app launch
- Uses Accompanist Permissions library
- Handles permission denial gracefully

**Permission Types**:
```kotlin
// Android 13+
READ_MEDIA_VIDEO
READ_MEDIA_AUDIO

// Android 9 and below
READ_EXTERNAL_STORAGE
```

**Permission Flow**:
1. Check if permissions granted
2. If not, show permission request UI
3. Request permissions on launch
4. Handle denial with user-friendly message

## Performance Considerations

### Memory Management

**Bitmap Handling**:
- Use appropriate bitmap config (`ARGB_8888`)
- Recycle bitmaps when done
- Limit cached pages
- Consider using `BitmapRegionDecoder` for large PDFs

**Media Files**:
- Extract on-demand (per page)
- Cache extracted files temporarily
- Clean up old cache files

### Rendering Performance

**Optimizations**:
- Render at 2x scale (balance quality/performance)
- Cache rendered pages
- Lazy load pages
- Use `RENDER_MODE_FOR_DISPLAY` for faster rendering

**Potential Improvements**:
- Implement page preloading
- Use lower resolution for thumbnails
- Implement virtual scrolling for large PDFs

### Async Operations

**Coroutine Usage**:
- All file I/O on `Dispatchers.IO`
- UI updates on `Dispatchers.Main`
- Proper cancellation handling

**Example**:
```kotlin
suspend fun extractMediaFiles(): List<MediaFile> = withContext(Dispatchers.IO) {
    // File I/O operations
}
```

## API Reference

### PdfMediaExtractor

#### `extractMediaFilesForPage(pdfPath: String, pageIndex: Int): List<MediaFile>`

Extracts media files from a specific PDF page.

**Parameters**:
- `pdfPath`: Full path to PDF file
- `pageIndex`: Zero-indexed page number

**Returns**: List of extracted media files

**Throws**: None (returns empty list on error)

**Example**:
```kotlin
val extractor = PdfMediaExtractor(context)
val mediaFiles = extractor.extractMediaFilesForPage("/path/to/file.pdf", 0)
```

### RecentPdfManager

#### `addRecentPdf(pdfPath: String)`

Adds a PDF to the recent list.

**Parameters**:
- `pdfPath`: Full path to PDF file

**Example**:
```kotlin
val manager = RecentPdfManager(context)
manager.addRecentPdf("/path/to/file.pdf")
```

#### `getRecentPdfs(): List<RecentPdf>`

Retrieves list of recent PDFs.

**Returns**: List of recent PDFs, sorted by last opened time

### AudioSegmentMappingLoader

#### `loadMapping(pdfPath: String): AudioSegmentMapping?`

Loads audio segment mapping for a PDF.

**Parameters**:
- `pdfPath`: Full path to PDF file

**Returns**: AudioSegmentMapping if found, null otherwise

**Search Order**:
1. Assets folder: `assets/audio_segments/{pdfFileName}.json`
2. Cache directory: `{cacheDir}/audio_segments/{pdfFileName}.json`

**JSON Key Matching**:
- First tries to match the full PDF filename (e.g., `"document.pdf"`)
- Falls back to filename without extension (e.g., `"document"`) if full name not found

## Implementation Details

### PDF Coordinate System

**PDF Coordinates**:
- Origin: Bottom-left corner
- X-axis: Increases rightward
- Y-axis: Increases upward
- Units: Points (1/72 inch)

**Android Coordinates**:
- Origin: Top-left corner
- X-axis: Increases rightward
- Y-axis: Increases downward
- Units: Pixels (device-dependent)

**Conversion Formula**:
```kotlin
androidY = pageHeight - pdfY
```

### Media Annotation Detection

**Annotation Types**:
- RichMedia annotations
- Screen annotations
- Link annotations with media actions

**Detection Process**:
1. Iterate through page annotations
2. Check annotation type
3. Extract embedded file streams
4. Identify media type from file extension or MIME type
5. Extract file content to cache

### Error Handling

**PDF Loading Errors**:
- File not found: Returns empty media list
- Corrupted PDF: Logs error, returns empty list
- Permission denied: Shows user-friendly message

**Media Extraction Errors**:
- Invalid file format: Skips file, continues extraction
- Extraction failure: Logs error, continues with other files
- Cache write failure: Logs error, skips file

**Playback Errors**:
- File not found: Shows error message
- Unsupported format: Shows error message
- Playback error: Logs error, shows user message

## Testing Strategy

### Unit Tests

**Testable Components**:
- `RecentPdfManager`: Test SharedPreferences operations
- `AudioSegmentMappingLoader`: Test JSON parsing
- `PdfMediaExtractor`: Test media extraction logic (mocked PDF)

**Test Location**: `app/src/test/java/`

### Instrumented Tests

**UI Tests**:
- Screen navigation
- File picker interaction
- PDF rendering
- Media playback

**Test Location**: `app/src/androidTest/java/`

### Manual Testing Checklist

- [ ] PDF file selection
- [ ] PDF page navigation
- [ ] Media extraction (audio, video, images)
- [ ] Audio playback with segments
- [ ] Video playback
- [ ] Recent PDFs functionality
- [ ] Permission handling
- [ ] Error scenarios (corrupted PDF, missing files)

---

**Document Version**: 1.0  
**Last Updated**: 2024  
**Maintained by**: AlgoFrame

