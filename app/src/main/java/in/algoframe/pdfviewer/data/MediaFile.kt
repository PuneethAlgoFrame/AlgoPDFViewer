package `in`.algoframe.pdfviewer.data

import android.util.Log

data class MediaFile(
    val name: String,
    val path: String,
    val type: MediaType,
    val pageNumber: Int? = null,
    val annotationRect: AnnotationRect? = null,
    val startTime: Long? = null,  // Start time in milliseconds (for audio/video segments)
    val endTime: Long? = null      // End time in milliseconds (for audio/video segments)
)

data class AnnotationRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun contains(x: Float, y: Float, pageHeight: Float? = null, tolerance: Float = 10f): Boolean {
        val minX = minOf(left, right)
        val maxX = maxOf(left, right)
        
        // Handle both PDF coordinates (top > bottom) and Android coordinates (top < bottom)
        val minY: Float
        val maxY: Float
        if (pageHeight != null) {
            // PDF coordinates: convert to Android coordinates
            // PDF top (high Y) -> Android top (low Y)
            // PDF bottom (low Y) -> Android bottom (high Y)
            val androidTop = pageHeight - maxOf(top, bottom)
            val androidBottom = pageHeight - minOf(top, bottom)
            minY = androidTop
            maxY = androidBottom
        } else {
            // Assume Android coordinates already (top < bottom)
            minY = minOf(top, bottom)
            maxY = maxOf(top, bottom)
        }
        
        // Add tolerance to make clicking easier
        val expandedMinX = minX - tolerance
        val expandedMaxX = maxX + tolerance
        val expandedMinY = minY - tolerance
        val expandedMaxY = maxY + tolerance
        
        val result = x >= expandedMinX && x <= expandedMaxX && y >= expandedMinY && y <= expandedMaxY
        if (!result && tolerance == 0f) {
            Log.v("AnnotationRect", "Point ($x, $y) not in rect [$minX, $minY, $maxX, $maxY]")
        }
        return result
    }
}

enum class MediaType {
    AUDIO,
    VIDEO,
    IMAGE
}

