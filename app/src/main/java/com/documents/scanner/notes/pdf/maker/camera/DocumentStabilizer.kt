package com.documents.scanner.notes.pdf.maker.camera

import android.graphics.PointF
import kotlin.math.sqrt

/**
 * Phase 6: Temporal Stabilization
 * 
 * Implements Low-Pass Filter (EMA) based on StackOverflow research:
 * https://stackoverflow.com/questions/4611599/smoothing-data-from-a-sensor
 * 
 * Formula: output = output_prev + α * (input - output_prev)
 * 
 * Where α (ALPHA) is the smoothing factor:
 * - Lower α (0.1-0.2) = more smoothing, more lag
 * - Higher α (0.4-0.6) = less smoothing, more responsive
 * 
 * Also includes frame confirmation to reduce jitter from large jumps.
 */
class DocumentStabilizer {
    
    companion object {
        // Smoothing factor: 0.1 = very smooth, 0.5 = responsive
        // Based on SO recommendation for sensor smoothing (0.1-0.2 for smooth, 0.3-0.5 for responsive)
        private const val DEFAULT_ALPHA = 0.25f
        
        // Maximum distance (pixels) for corners to be considered "matching" for frame confirmation
        private const val CORNER_MATCH_THRESHOLD = 25f
        
        // Frames to hold position when detection is lost
        private const val HOLD_FRAMES_ON_LOSS = 5
    }
    
    // Configuration
    var alpha: Float = DEFAULT_ALPHA
    var cornerMatchThreshold: Float = CORNER_MATCH_THRESHOLD
    var holdFramesOnLoss: Int = HOLD_FRAMES_ON_LOSS
    var enabled: Boolean = true
    
    // State
    private var smoothedCorners: MutableList<PointF>? = null
    private var lostFrameCount: Int = 0
    
    /**
     * Apply low-pass filter to smooth corners
     * 
     * Based on SO answer: output[i] = output[i-1] + ALPHA * (input[i] - output[i-1])
     * 
     * @param rawCorners The raw detected corners from current frame (null if no detection)
     * @return Smoothed corners to display
     */
    fun stabilize(rawCorners: List<PointF>?): List<PointF>? {
        // Bypass if disabled
        if (!enabled) {
            return rawCorners
        }
        
        // Case 1: No detection this frame - hold position briefly then clear
        if (rawCorners == null || rawCorners.size != 4) {
            return handleNoDetection()
        }
        
        // Reset lost frame counter since we have detection
        lostFrameCount = 0
        
        // Case 2: First detection - initialize smoothed corners
        if (smoothedCorners == null || smoothedCorners!!.size != 4) {
            smoothedCorners = rawCorners.map { PointF(it.x, it.y) }.toMutableList()
            return smoothedCorners!!.toList()
        }
        
        // Case 3: Apply low-pass filter (EMA) to each corner
        // Formula from SO: output = output + ALPHA * (input - output)
        for (i in 0 until 4) {
            val rawPoint = rawCorners[i]
            val smoothedPoint = smoothedCorners!![i]
            
            // Low-pass filter for X
            smoothedPoint.x = smoothedPoint.x + alpha * (rawPoint.x - smoothedPoint.x)
            // Low-pass filter for Y
            smoothedPoint.y = smoothedPoint.y + alpha * (rawPoint.y - smoothedPoint.y)
        }
        
        return smoothedCorners!!.toList()
    }
    
    /**
     * Handle frames with no detection - hold position briefly then fade out
     */
    private fun handleNoDetection(): List<PointF>? {
        lostFrameCount++
        
        // Hold the last valid position for a few frames
        if (lostFrameCount <= holdFramesOnLoss && smoothedCorners != null) {
            return smoothedCorners!!.toList()
        }
        
        // After holdFramesOnLoss, clear the overlay
        reset()
        return null
    }
    
    /**
     * Reset all state - use when switching cameras or clearing overlay
     */
    fun reset() {
        smoothedCorners = null
        lostFrameCount = 0
    }
    
    /**
     * Get the current smoothed corners (for use in capture if needed)
     */
    fun getSmoothedCorners(): List<PointF>? {
        return smoothedCorners?.toList()
    }
}
