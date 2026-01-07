package com.documents.scanner.notes.pdf.maker.camera

import android.graphics.PointF

data class DetectedDocument(
    val corners: List<PointF>,
    val confidence: Float
) {
    init {
        require(corners.size == 4) { "Document must have exactly 4 corners" }
        require(confidence in 0f..1f) { "Confidence must be between 0 and 1" }
    }
    
    val topLeft: PointF get() = corners[0]
    val topRight: PointF get() = corners[1]
    val bottomRight: PointF get() = corners[2]
    val bottomLeft: PointF get() = corners[3]
    
    fun area(): Float {
        var sum = 0f
        for (i in corners.indices) {
            val j = (i + 1) % 4
            sum += corners[i].x * corners[j].y
            sum -= corners[j].x * corners[i].y
        }
        return kotlin.math.abs(sum) / 2f
    }
    
    fun isRectangular(tolerance: Float = 0.3f): Boolean {
        for (i in corners.indices) {
            val prev = corners[(i + 3) % 4]
            val curr = corners[i]
            val next = corners[(i + 1) % 4]
            
            val v1x = prev.x - curr.x
            val v1y = prev.y - curr.y
            val v2x = next.x - curr.x
            val v2y = next.y - curr.y
            
            val dot = v1x * v2x + v1y * v2y
            val mag1 = kotlin.math.sqrt(v1x * v1x + v1y * v1y)
            val mag2 = kotlin.math.sqrt(v2x * v2x + v2y * v2y)
            
            if (mag1 == 0f || mag2 == 0f) return false
            
            val cosAngle = dot / (mag1 * mag2)
            if (kotlin.math.abs(cosAngle) > tolerance) return false
        }
        return true
    }
    
    override fun toString(): String {
        return "DetectedDocument(TL=${topLeft}, TR=${topRight}, BR=${bottomRight}, BL=${bottomLeft}, conf=${"%.2f".format(confidence)})"
    }
}
