# PDF Reader - Android Application

A modern Android PDF reader application built with Jetpack Compose that supports viewing PDFs and playing embedded media files (audio, video, images) extracted from PDF documents.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Usage](#usage)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Key Components](#key-components)
- [Configuration](#configuration)
- [Audio Segment Mapping](#audio-segment-mapping)
- [Development](#development)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

## Overview

PDF Reader is an Android application that provides a comprehensive PDF viewing experience with advanced media playback capabilities. The app can extract and play audio, video, and image files embedded within PDF documents, making it ideal for interactive PDFs, educational materials, and multimedia documents.

### Key Highlights

- **Modern UI**: Built with Jetpack Compose and Material Design 3
- **Media Extraction**: Automatically extracts embedded audio, video, and images from PDFs
- **Interactive Playback**: Click on media annotations in PDFs to play audio/video segments
- **Recent Files**: Tracks and displays recently opened PDF files
- **Permission Handling**: Smart permission management for Android 10+ devices

## Features

### Core Features

- ✅ **PDF Viewing**: View PDF documents with smooth scrolling and page navigation
- ✅ **Media Extraction**: Extract embedded audio, video, and image files from PDFs
- ✅ **Audio Playback**: Play audio files with segment support (start/end time ranges)
- ✅ **Video Playback**: Play embedded video files using ExoPlayer
- ✅ **Image Display**: View embedded images from PDFs
- ✅ **Recent PDFs**: Track and quickly access recently opened PDF files
- ✅ **File Picker**: Easy PDF file selection using Android's file picker
- ✅ **Permission Management**: Automatic handling of storage permissions

### Advanced Features

- **Audio Segment Mapping**: Support for external JSON mapping files to define audio segments with time ranges
- **Clickable Media**: Click on media annotations in PDFs to trigger playback
- **Page-based Media**: Media files are extracted per-page for optimal performance
- **Coordinate Mapping**: Accurate mapping between PDF coordinates and Android view coordinates

## Requirements

### Minimum Requirements

- **Android Version**: Android 7.0 (API level 24) or higher
- **Target SDK**: Android 14 (API level 36)
- **Java Version**: Java 11
- **Kotlin Version**: 2.2.21

### Permissions

The app requires the following permissions:

- `READ_EXTERNAL_STORAGE` (Android 9 and below)
- `READ_MEDIA_VIDEO` (Android 13+)
- `READ_MEDIA_AUDIO` (Android 13+)
- `INTERNET` (for potential future features)

## Installation

### Prerequisites

1. **Android Studio**: Hedgehog (2023.1.1) or later
2. **JDK**: Java Development Kit 11 or later
3. **Android SDK**: API level 24-36

### Build Steps

1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd PDFReader
   ```

2. **Open in Android Studio**:
   - Open Android Studio
   - Select "Open an Existing Project"
   - Navigate to the project directory

3. **Sync Gradle**:
   - Android Studio will automatically sync Gradle dependencies
   - If not, go to: `File > Sync Project with Gradle Files`

4. **Build the project**:
   - `Build > Make Project` or press `Ctrl+F9` (Windows/Linux) / `Cmd+F9` (Mac)

5. **Run on device/emulator**:
   - Connect an Android device or start an emulator
   - Click `Run > Run 'app'` or press `Shift+F10` (Windows/Linux) / `Ctrl+R` (Mac)

### APK Installation

Pre-built APK files are available in:
```
app/build/outputs/apk/debug/app-debug.apk
```

Install via ADB:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Usage

### Basic Usage

1. **Launch the app**: Open PDF Reader from your app drawer
2. **Grant permissions**: Allow storage permissions when prompted
3. **Select a PDF**: Tap "Select PDF File" to choose a PDF from your device
4. **View PDF**: Scroll through pages using swipe gestures
5. **Navigate pages**: Use arrow buttons or swipe to navigate between pages
6. **Play media**: Tap on audio/video annotations in the PDF to play them

### Recent PDFs

- Recently opened PDFs appear in the "Recent PDFs" section
- Tap any recent PDF to open it directly
- The app tracks up to 20 recent PDFs

### Audio Segment Playback

For PDFs with audio segments:

1. The app automatically extracts audio files from the PDF
2. Click on audio annotations in the PDF
3. The audio player appears at the bottom
4. Audio plays for the specified time range (if configured)
5. Use controls to play/pause, rewind 10s, or forward 10s

### Video Playback

1. Click on video annotations in the PDF
2. The video player appears with ExoPlayer controls
3. Use standard video controls to play/pause/seek

## Architecture

### Architecture Pattern

The app follows a **Compose-based MVVM-like architecture**:

- **UI Layer**: Jetpack Compose screens and components
- **Data Layer**: Data classes and models
- **Utils Layer**: Business logic and utilities
- **State Management**: Compose state management with `remember` and `mutableStateOf`

### Key Architectural Decisions

1. **Compose-First**: Entire UI built with Jetpack Compose
2. **Coroutines**: Async operations handled with Kotlin Coroutines
3. **State Hoisting**: State managed at screen level and passed down
4. **Separation of Concerns**: Clear separation between UI, data, and utilities

## Project Structure

```
PDFReader/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/algoframe/pdfreader/
│   │   │   │   ├── MainActivity.kt              # Main entry point
│   │   │   │   ├── data/                        # Data models
│   │   │   │   │   ├── AudioSegmentMapping.kt
│   │   │   │   │   └── MediaFile.kt
│   │   │   │   ├── ui/                          # UI components
│   │   │   │   │   ├── components/              # Reusable components
│   │   │   │   │   │   ├── AudioPlayer.kt
│   │   │   │   │   │   ├── PdfViewer.kt
│   │   │   │   │   │   └── VideoPlayer.kt
│   │   │   │   │   ├── screens/                 # Screen composables
│   │   │   │   │   │   ├── FilePickerScreen.kt
│   │   │   │   │   │   └── PdfViewerScreen.kt
│   │   │   │   │   └── theme/                   # Theme configuration
│   │   │   │   │       ├── Color.kt
│   │   │   │   │       ├── Theme.kt
│   │   │   │   │       └── Type.kt
│   │   │   │   └── utils/                       # Utility classes
│   │   │   │       ├── AudioSegmentMappingLoader.kt
│   │   │   │       ├── PdfMediaExtractor.kt
│   │   │   │       └── RecentPdfManager.kt
│   │   │   ├── res/                             # Resources
│   │   │   └── AndroidManifest.xml
│   │   └── test/                                # Unit tests
│   └── build.gradle.kts                         # App-level build config
├── gradle/
│   └── libs.versions.toml                       # Dependency versions
├── build.gradle.kts                             # Project-level build config
└── settings.gradle.kts                          # Project settings
```

## Key Components

### MainActivity

Entry point of the application. Sets up the Compose theme and manages navigation between screens.

**Key Responsibilities**:
- Initialize Compose UI
- Manage app-level state (selected PDF path)
- Handle screen navigation

### FilePickerScreen

Screen for selecting PDF files and displaying recent PDFs.

**Features**:
- File picker integration
- Permission handling
- Recent PDFs list
- Storage access management

### PdfViewerScreen

Main PDF viewing screen with media playback support.

**Features**:
- PDF page rendering
- Media extraction per page
- Media playback controls
- Page navigation

### PdfViewer Component

Core component for rendering PDF pages and handling user interactions.

**Key Features**:
- PDF rendering using Android's native `PdfRenderer` API (no external library)
- Page navigation with swipe gestures
- Media annotation detection
- Click handling for media playback
- Coordinate mapping between PDF and Android view coordinates

### PdfMediaExtractor

Utility class for extracting embedded media from PDF files.

**Capabilities**:
- Extract audio files
- Extract video files
- Extract images
- Support for external audio segment mappings
- Coordinate mapping between PDF and Android views

### AudioPlayer

Composable component for audio playback with segment support.

**Features**:
- Play/pause controls
- 10-second rewind/forward
- Progress indicator
- Time range support (start/end times)
- ExoPlayer integration

### VideoPlayer

Composable component for video playback.

**Features**:
- Full video playback
- ExoPlayer integration
- Standard video controls

### RecentPdfManager

Manages recently opened PDF files using SharedPreferences.

**Features**:
- Store recent PDFs (up to 20)
- Retrieve recent PDFs
- System PDF discovery (Android 10+)
- File existence validation

## Configuration

### Build Configuration

**Gradle Version**: Check `gradle/wrapper/gradle-wrapper.properties`

**Dependencies**: All dependencies are managed in `gradle/libs.versions.toml`

**Key Dependencies**:
- Jetpack Compose BOM: `2025.12.00`
- Kotlin: `2.2.21`
- Android Gradle Plugin: `8.13.2`
- PDFBox Android: `2.0.27.0` (for media extraction)
- ExoPlayer: `2.19.1` (for audio/video playback)
- Coil: `2.5.0` (for image loading)
- Material Icons Extended: `1.7.8` (for UI icons)
- Accompanist Permissions: `0.34.0` (for permission handling)
- Activity Result API: `1.3.1` (for file picking)

**Note**: PDF rendering uses Android's native `PdfRenderer` API (no external PDF viewer library required).

### App Configuration

**Package Name**: `com.algoframe.pdfreader`

**Min SDK**: 24 (Android 7.0)

**Target SDK**: 36 (Android 14)

**Version**: 1.0 (versionCode: 1)

## Audio Segment Mapping

### Overview

For PDFs that don't have embedded MediaClip time ranges, you can provide external JSON mapping files to define audio segments with specific time ranges.

### Mapping File Location

Place mapping files in one of these locations:

1. **Assets folder**: `app/src/main/assets/audio_segments/{pdfFileName}.json`
2. **Cache directory**: `{app_cache_dir}/audio_segments/{pdfFileName}.json`

### Mapping File Format

```json
{
  "your_pdf_file.pdf": {
    "pages": [
      {
        "pageNumber": 2,
        "segments": [
          {
            "rect": [15.991, 594.98, 52.5049, 566.25],
            "startTime": 0,
            "endTime": 10000
          },
          {
            "rect": [60.0, 500.0, 100.0, 450.0],
            "startTime": 10000,
            "endTime": 20000
          }
        ]
      }
    ]
  }
}
```

### Mapping Fields

- **pdfFileName**: Name of the PDF file as the top-level key. Can be:
  - Full filename with extension: `"document.pdf"` (preferred)
  - Filename without extension: `"document"` (fallback, if full name not found)
- **pageNumber**: Page number (1-indexed)
- **rect**: Array of 4 floats `[left, top, right, bottom]` in PDF coordinates
- **startTime**: Start time in milliseconds
- **endTime**: End time in milliseconds

### Coordinate System

PDF coordinates use a coordinate system where:
- Origin (0,0) is at the bottom-left corner
- Y-axis increases upward
- Coordinates are in points (1/72 inch)

The app automatically converts PDF coordinates to Android view coordinates.

### Example Use Case

If you have a PDF with audio annotations but the PDF doesn't specify time ranges, create a mapping file:

1. Create `app/src/main/assets/audio_segments/mybook.pdf.json`
2. Define segments for each page with audio
3. Specify the rectangle coordinates and time ranges
4. The app will automatically load and apply the mapping

See `app/src/main/assets/audio_segments/README.md` for more details.

## Development

### Setting Up Development Environment

1. **Install Android Studio**: Download from [developer.android.com](https://developer.android.com/studio)

2. **Configure SDK**: Install Android SDK API levels 24-36

3. **Clone Repository**: 
   ```bash
   git clone <repository-url>
   cd PDFReader
   ```

4. **Open Project**: Open in Android Studio

5. **Sync Gradle**: Let Gradle sync dependencies

### Code Style

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add KDoc comments for public APIs
- Keep functions focused and single-purpose

### Testing

Run unit tests:
```bash
./gradlew test
```

Run instrumented tests:
```bash
./gradlew connectedAndroidTest
```

### Building Release APK

1. **Generate Signed Bundle/APK**:
   - `Build > Generate Signed Bundle / APK`
   - Select APK
   - Create or select keystore
   - Build release APK

2. **Or use Gradle**:
   ```bash
   ./gradlew assembleRelease
   ```

### Debugging

- Use Android Studio's debugger
- Check Logcat for debug logs (tag: "PdfViewer", "PdfMediaExtractor", etc.)
- Enable verbose logging in components for detailed debugging

## Troubleshooting

### Common Issues

#### PDF Not Loading

**Problem**: PDF file doesn't open or shows error.

**Solutions**:
1. Verify file exists and is accessible
2. Check file permissions
3. Ensure PDF is not corrupted
4. Check Logcat for specific error messages

#### Media Not Playing

**Problem**: Audio/video files don't play when clicked.

**Solutions**:
1. Check if media files were extracted (check cache directory)
2. Verify file format is supported (MP3, MP4, etc.)
3. Check ExoPlayer logs in Logcat
4. Ensure file path is correct

#### Permission Denied

**Problem**: App can't access PDF files.

**Solutions**:
1. Grant storage permissions in app settings
2. For Android 10+, ensure using scoped storage APIs
3. Check AndroidManifest.xml for permission declarations

#### Audio Segment Mapping Not Working

**Problem**: External audio segment mappings aren't applied.

**Solutions**:
1. Verify JSON file name matches PDF file name exactly
2. Check JSON syntax is valid
3. Verify coordinates are in PDF coordinate system
4. Check Logcat for mapping load errors
5. Ensure file is in correct location (assets or cache)

#### Build Errors

**Problem**: Gradle sync fails or build errors occur.

**Solutions**:
1. Check `gradle/libs.versions.toml` for correct versions
2. Verify internet connection for dependency downloads
3. Clean and rebuild: `Build > Clean Project` then `Build > Rebuild Project`
4. Invalidate caches: `File > Invalidate Caches / Restart`
5. Ensure all repositories are accessible (Google, Maven Central, JitPack)
6. If PDFBox version issues occur, check available versions at: https://search.maven.org/artifact/com.tom-roush/pdfbox-android

### Debug Logging

Enable verbose logging by checking Logcat with these tags:
- `PdfViewer`: PDF rendering and navigation
- `PdfMediaExtractor`: Media extraction process
- `AudioPlayer`: Audio playback issues
- `VideoPlayer`: Video playback issues
- `AudioSegmentMappingLoader`: Mapping file loading

### Performance Issues

**Slow PDF Rendering**:
- Reduce bitmap scaling factor in `PdfViewer.kt`
- Implement page caching
- Use lower resolution for preview

**Memory Issues**:
- Ensure bitmaps are recycled properly
- Limit number of cached pages
- Check for memory leaks in media players

## Contributing

Contributions are welcome! Please follow these guidelines:

1. **Fork the repository**
2. **Create a feature branch**: `git checkout -b feature/amazing-feature`
3. **Commit changes**: `git commit -m 'Add amazing feature'`
4. **Push to branch**: `git push origin feature/amazing-feature`
5. **Open a Pull Request**

### Contribution Guidelines

- Follow Kotlin coding conventions
- Add tests for new features
- Update documentation as needed
- Ensure all tests pass
- Keep commits focused and atomic

## License

This project is licensed under the GNU General Public License v3.0 (GPL-3.0).

See the [LICENSE](LICENSE) file for details.

**Summary**: This is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

---

## Additional Resources

- **[Documentation Index](./docs/INDEX.md)** - Complete index of all documentation
- **[Technical Documentation](./docs/TECHNICAL.md)** - Detailed technical documentation
- **[Quick Reference Guide](./docs/QUICK_REFERENCE.md)** - Quick reference for developers
- **[Dependency Fix Guide](./docs/FIX_DEPENDENCIES.md)** - Troubleshooting dependency issues
- **[Audio Segment Mapping Guide](./app/src/main/assets/audio_segments/README.md)** - Audio mapping file format

### Quick Navigation

- 🚀 **Getting Started**: [Installation](#installation) | [Usage](#usage)
- 🏗️ **Architecture**: [Architecture](#architecture) | [Project Structure](#project-structure)
- 🔧 **Development**: [Development](#development) | [Technical Docs](./docs/TECHNICAL.md) | [Quick Reference](./docs/QUICK_REFERENCE.md)
- 🐛 **Troubleshooting**: [Troubleshooting](#troubleshooting) | [Dependency Issues](./docs/FIX_DEPENDENCIES.md)
- 📚 **All Documentation**: [Documentation Index](./docs/INDEX.md)

## Support

For issues, questions, or contributions:
- Open an issue on GitHub
- Check existing documentation
- Review Logcat for error messages

---

**Version**: 1.0  
**Last Updated**: 2024  
**Maintained by**: AlgoFrame

