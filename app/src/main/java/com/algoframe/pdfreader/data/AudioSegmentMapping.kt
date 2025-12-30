package com.algoframe.pdfreader.data

import com.algoframe.pdfreader.data.AnnotationRect

/**
 * External mapping for audio segments when PDF doesn't have MediaClip time ranges
 * 
 * JSON format:
 * {
 *   "pdfFileName.pdf": {
 *     "pages": [
 *       {
 *         "pageNumber": 2,
 *         "segments": [
 *           {
 *             "rect": [15.991, 594.98, 52.5049, 566.25],
 *             "startTime": 0,
 *             "endTime": 10000
 *           }
 *         ]
 *       }
 *     ]
 *   }
 * }
 */
data class AudioSegmentMapping(
    val pdfFileName: String,
    val pages: List<PageSegments>
)

data class PageSegments(
    val pageNumber: Int,
    val segments: List<SegmentMapping>
)

data class SegmentMapping(
    val rect: FloatArray, // [left, top, right, bottom] in PDF coordinates
    val startTime: Long,  // Start time in milliseconds
    val endTime: Long     // End time in milliseconds
) {
    /**
     * Check if this mapping matches the given annotation rectangle
     * Uses tolerance to account for floating point precision
     */
    fun matches(annotationRect: AnnotationRect, tolerance: Float = 5f): Boolean {
        val rectLeft = rect[0]
        val rectTop = rect[1]
        val rectRight = rect[2]
        val rectBottom = rect[3]
        
        // Check if rectangles match within tolerance
        val leftMatch = kotlin.math.abs(annotationRect.left - rectLeft) <= tolerance
        val topMatch = kotlin.math.abs(annotationRect.top - rectTop) <= tolerance
        val rightMatch = kotlin.math.abs(annotationRect.right - rectRight) <= tolerance
        val bottomMatch = kotlin.math.abs(annotationRect.bottom - rectBottom) <= tolerance
        
        return leftMatch && topMatch && rightMatch && bottomMatch
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as SegmentMapping
        
        if (!rect.contentEquals(other.rect)) return false
        if (startTime != other.startTime) return false
        if (endTime != other.endTime) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = rect.contentHashCode()
        result = 31 * result + startTime.hashCode()
        result = 31 * result + endTime.hashCode()
        return result
    }
}

