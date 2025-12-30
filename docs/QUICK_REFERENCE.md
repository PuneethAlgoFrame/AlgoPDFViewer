# Quick Reference Guide - PDF Reader

Quick reference for common tasks and information.

## 🚀 Quick Start

### Build & Run
```bash
# Sync Gradle
./gradlew build

# Install on device
./gradlew installDebug

# Run tests
./gradlew test
```

### Key Files
- **Main Activity**: `app/src/main/java/com/algoframe/pdfreader/MainActivity.kt`
- **PDF Viewer**: `app/src/main/java/com/algoframe/pdfreader/ui/components/PdfViewer.kt`
- **Media Extractor**: `app/src/main/java/com/algoframe/pdfreader/utils/PdfMediaExtractor.kt`
- **Build Config**: `app/build.gradle.kts`
- **Dependencies**: `gradle/libs.versions.toml`

---

## 📦 Key Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Kotlin | 2.2.21 | Language |
| Compose BOM | 2025.12.00 | UI Framework |
| PDFBox Android | 2.0.27.0 | PDF Parsing & Media Extraction |
| ExoPlayer | 2.19.1 | Media Playback |
| Coil | 2.5.0 | Image Loading |
| Material Icons Extended | 1.7.8 | UI Icons |
| Accompanist Permissions | 0.34.0 | Permission Handling |

**Note**: PDF rendering uses Android's native `PdfRenderer` API (no external PDF viewer library).

---

## 🏗️ Architecture Quick Reference

### Component Hierarchy
```
MainActivity
└── PDFReaderApp
    ├── FilePickerScreen (no PDF selected)
    └── PdfViewerScreen (PDF selected)
        ├── PdfViewer (renders PDF)
        ├── AudioPlayer (plays audio)
        └── VideoPlayer (plays video)
```

### Key Classes
- **MainActivity**: App entry point
- **FilePickerScreen**: PDF selection UI
- **PdfViewerScreen**: PDF viewing UI
- **PdfViewer**: PDF rendering component
- **PdfMediaExtractor**: Media extraction utility
- **RecentPdfManager**: Recent files management
- **AudioPlayer**: Audio playback component
- **VideoPlayer**: Video playback component

---

## 📝 Common Code Patterns

### Extract Media from PDF
```kotlin
val extractor = PdfMediaExtractor(context)
val mediaFiles = extractor.extractMediaFilesForPage(pdfPath, pageIndex)
```

### Render PDF Page
```kotlin
val fileDescriptor = ParcelFileDescriptor.open(file, MODE_READ_ONLY)
val pdfRenderer = PdfRenderer(fileDescriptor)
val page = pdfRenderer.openPage(pageIndex)
val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
page.render(bitmap, null, null, RENDER_MODE_FOR_DISPLAY)
```

### Play Audio Segment
```kotlin
AudioPlayer(
    audioPath = mediaFile.path,
    startTime = mediaFile.startTime,
    endTime = mediaFile.endTime
)
```

### Add Recent PDF
```kotlin
val manager = RecentPdfManager(context)
manager.addRecentPdf(pdfPath)
```

---

## 🔧 Configuration

### App Config
- **Package**: `com.algoframe.pdfreader`
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 36 (Android 14)
- **Version**: 1.0

### Permissions
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
```

---

## 🐛 Common Issues & Solutions

### PDF Not Loading
- ✅ Check file exists and is accessible
- ✅ Verify permissions granted
- ✅ Check Logcat for errors

### Media Not Playing
- ✅ Verify media files extracted (check cache)
- ✅ Check file format supported
- ✅ Verify ExoPlayer logs

### Build Errors
- ✅ Sync Gradle: `File > Sync Project with Gradle Files`
- ✅ Clean build: `Build > Clean Project`
- ✅ Check dependency versions in `libs.versions.toml`

---

## 📍 File Locations

### Source Code
- **UI Screens**: `app/src/main/java/com/algoframe/pdfreader/ui/screens/`
- **Components**: `app/src/main/java/com/algoframe/pdfreader/ui/components/`
- **Utils**: `app/src/main/java/com/algoframe/pdfreader/utils/`
- **Data Models**: `app/src/main/java/com/algoframe/pdfreader/data/`

### Resources
- **Assets**: `app/src/main/assets/`
- **Audio Mappings**: `app/src/main/assets/audio_segments/`
- **Layouts**: `app/src/main/res/layout/`
- **Values**: `app/src/main/res/values/`

### Build Outputs
- **APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Cache**: `app/build/`

---

## 🎯 Debugging Tips

### Logcat Tags
- `PdfViewer`: PDF rendering
- `PdfMediaExtractor`: Media extraction
- `AudioPlayer`: Audio playback
- `VideoPlayer`: Video playback
- `AudioSegmentMappingLoader`: Mapping file loading

### Common Debug Commands
```bash
# View logs
adb logcat | grep -E "PdfViewer|PdfMediaExtractor|AudioPlayer"

# Install APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Clear app data
adb shell pm clear com.algoframe.pdfreader
```

---

## 📚 Documentation Links

- **[README.md](../README.md)** - Main documentation
- **[TECHNICAL.md](./TECHNICAL.md)** - Technical details
- **[INDEX.md](./INDEX.md)** - All docs index
- **[FIX_DEPENDENCIES.md](./FIX_DEPENDENCIES.md)** - Dependency help

---

## 🔄 Common Workflows

### Adding New Feature
1. Create feature branch
2. Implement in appropriate module
3. Add tests
4. Update documentation
5. Create PR

### Debugging Media Issue
1. Check Logcat for extraction logs
2. Verify file exists in cache
3. Check media file format
4. Test with different PDF
5. Review ExoPlayer logs

### Updating Dependencies
1. Update version in `libs.versions.toml`
2. Sync Gradle
3. Test build
4. Check for breaking changes
5. Update code if needed

---

## 💡 Tips & Best Practices

### Performance
- Extract media per-page (not all at once)
- Cache rendered PDF pages
- Recycle bitmaps when done
- Use appropriate bitmap config

### Code Quality
- Follow Kotlin conventions
- Use meaningful names
- Add KDoc comments
- Keep functions focused

### Testing
- Write unit tests for utilities
- Test UI components
- Test error scenarios
- Verify on multiple devices

---

## 📞 Getting Help

1. **Check Documentation**: [INDEX.md](./INDEX.md)
2. **Review Logs**: Check Logcat for errors
3. **Search Issues**: Check existing GitHub issues
4. **Ask Question**: Open new issue with details

---

**Last Updated**: 2024  
**Version**: 1.0

