package com.documents.scanner.notes.pdf.maker.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.nio.ByteBuffer

private const val TAG = "ObjectDetectionAnalyzer"

data class DocumentDetectionResult(
    val document: DetectedDocument?,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val timestamp: Long = System.currentTimeMillis()
)

class ObjectDetectionAnalyzer(
    private val onDocumentDetected: ((DocumentDetectionResult) -> Unit)? = null
) : ImageAnalysis.Analyzer {
    
    private val documentDetector: DocumentDetector
    private val documentStabilizer: DocumentStabilizer
    
    private var lastDetectionTime = 0L
    private val detectionIntervalMs = 100L
    
    private var lastValidResult: DocumentDetectionResult? = null
    private var missedFrameCount = 0
    private val maxMissedFrames = 5
    
    init {
        documentDetector = DocumentDetector()
        documentStabilizer = DocumentStabilizer()
        DocumentDetector.initOpenCV()
        Log.d(TAG, "DocumentDetector initialized, mode: ${DetectionConfig.currentMode}")
    }
    
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastDetectionTime < detectionIntervalMs) {
            imageProxy.close()
            return
        }
        
        lastDetectionTime = currentTime
        
        try {
            when (DetectionConfig.currentMode) {
                DetectionConfig.DetectionMode.COMBINED_AUTO -> {
                    analyzeCombinedMode(imageProxy)
                }
                DetectionConfig.DetectionMode.BITMAP_BGR_3CHANNELS -> {
                    analyzeBitmapMode(imageProxy)
                }
                DetectionConfig.DetectionMode.HSV_SATURATION -> {
                    analyzeHSVMode(imageProxy)
                }
                DetectionConfig.DetectionMode.MORPH_GRADIENT -> {
                    analyzeMorphGradientMode(imageProxy)
                }
                DetectionConfig.DetectionMode.PLANES_GRAYSCALE -> {
                    analyzeGrayscaleMode(imageProxy)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Detection failed", e)
        } finally {
            imageProxy.close()
        }
    }
    
    /**
     * COMBINED detection - runs all methods and picks best
     */
    private fun analyzeCombinedMode(imageProxy: ImageProxy) {
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        
        val originalBitmap = imageProxy.toBitmap()
        
        val rotatedBitmap = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true).also {
                if (it != originalBitmap) originalBitmap.recycle()
            }
        } else {
            originalBitmap
        }
        
        val document = documentDetector.detectDocumentCombined(rotatedBitmap)
        
        val sourceWidth = rotatedBitmap.width
        val sourceHeight = rotatedBitmap.height
        
        handleDetectionResult(document, sourceWidth, sourceHeight)
        
        rotatedBitmap.recycle()
    }
    
    private fun analyzeBitmapMode(imageProxy: ImageProxy) {
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        
        val originalBitmap = imageProxy.toBitmap()
        
        val rotatedBitmap = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true).also {
                if (it != originalBitmap) originalBitmap.recycle()
            }
        } else {
            originalBitmap
        }
        
        val document = documentDetector.detectDocument(rotatedBitmap)
        
        val sourceWidth = rotatedBitmap.width
        val sourceHeight = rotatedBitmap.height
        
        handleDetectionResult(document, sourceWidth, sourceHeight)
        
        rotatedBitmap.recycle()
    }
    
    /**
     * HSV Saturation-based detection for colored documents
     */
    private fun analyzeHSVMode(imageProxy: ImageProxy) {
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        
        val originalBitmap = imageProxy.toBitmap()
        
        val rotatedBitmap = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true).also {
                if (it != originalBitmap) originalBitmap.recycle()
            }
        } else {
            originalBitmap
        }
        
        val document = documentDetector.detectDocumentHSV(rotatedBitmap)
        
        val sourceWidth = rotatedBitmap.width
        val sourceHeight = rotatedBitmap.height
        
        handleDetectionResult(document, sourceWidth, sourceHeight)
        
        rotatedBitmap.recycle()
    }
    
    /**
     * Morphological Gradient detection - color-independent
     */
    private fun analyzeMorphGradientMode(imageProxy: ImageProxy) {
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        
        val originalBitmap = imageProxy.toBitmap()
        
        val rotatedBitmap = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true).also {
                if (it != originalBitmap) originalBitmap.recycle()
            }
        } else {
            originalBitmap
        }
        
        val document = documentDetector.detectDocumentMorphGradient(rotatedBitmap)
        
        val sourceWidth = rotatedBitmap.width
        val sourceHeight = rotatedBitmap.height
        
        handleDetectionResult(document, sourceWidth, sourceHeight)
        
        rotatedBitmap.recycle()
    }
    
    @OptIn(ExperimentalGetImage::class)
    private fun analyzeGrayscaleMode(imageProxy: ImageProxy) {
        val image = imageProxy.image ?: return
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        
        val yPlane = image.planes[0]
        val yBuffer = yPlane.buffer
        
        val width = image.width
        val height = image.height
        val rowStride = yPlane.rowStride
        
        val grayMat = yPlaneToMat(yBuffer, width, height, rowStride)
        
        val (rotatedMat, rotatedWidth, rotatedHeight) = if (rotationDegrees != 0) {
            val rotated = rotateGrayscaleMat(grayMat, rotationDegrees)
            Triple(rotated, rotated.cols(), rotated.rows())
        } else {
            Triple(grayMat, width, height)
        }
        
        val document = documentDetector.detectDocumentFromGrayscale(rotatedMat)
        
        handleDetectionResult(document, rotatedWidth, rotatedHeight)
        
        grayMat.release()
        if (rotatedMat != grayMat) rotatedMat.release()
    }
    
    private fun yPlaneToMat(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int): Mat {
        val mat = Mat(height, width, CvType.CV_8UC1)
        
        if (rowStride == width) {
            val bytes = ByteArray(width * height)
            buffer.rewind()
            buffer.get(bytes)
            mat.put(0, 0, bytes)
        } else {
            val rowBytes = ByteArray(width)
            for (row in 0 until height) {
                buffer.position(row * rowStride)
                buffer.get(rowBytes, 0, width)
                mat.put(row, 0, rowBytes)
            }
        }
        
        return mat
    }
    
    private fun rotateGrayscaleMat(mat: Mat, degrees: Int): Mat {
        val rotated = Mat()
        when (degrees) {
            90 -> {
                org.opencv.core.Core.rotate(mat, rotated, org.opencv.core.Core.ROTATE_90_CLOCKWISE)
            }
            180 -> {
                org.opencv.core.Core.rotate(mat, rotated, org.opencv.core.Core.ROTATE_180)
            }
            270 -> {
                org.opencv.core.Core.rotate(mat, rotated, org.opencv.core.Core.ROTATE_90_COUNTERCLOCKWISE)
            }
            else -> {
                mat.copyTo(rotated)
            }
        }
        return rotated
    }
    
    private fun handleDetectionResult(document: DetectedDocument?, sourceWidth: Int, sourceHeight: Int) {
        // Update stabilizer config from DetectionConfig
        documentStabilizer.enabled = DetectionConfig.stabilizationEnabled
        documentStabilizer.alpha = DetectionConfig.stabilizationAlpha
        
        // Apply EMA stabilization to corners
        val rawCorners = document?.corners
        val stabilizedCorners = documentStabilizer.stabilize(rawCorners)
        
        if (stabilizedCorners != null && stabilizedCorners.size == 4) {
            missedFrameCount = 0
            val stabilizedDocument = DetectedDocument(
                corners = stabilizedCorners,
                confidence = document?.confidence ?: 0.85f
            )
            val result = DocumentDetectionResult(
                document = stabilizedDocument,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight
            )
            lastValidResult = result
            
            if (DetectionConfig.enableLogging) {
                Log.d(TAG, "Document detected (stabilized) in ${sourceWidth}x${sourceHeight}")
            }
            onDocumentDetected?.invoke(result)
        } else {
            missedFrameCount++
            if (missedFrameCount <= maxMissedFrames && lastValidResult != null) {
                onDocumentDetected?.invoke(lastValidResult!!)
            } else {
                lastValidResult = null
                onDocumentDetected?.invoke(
                    DocumentDetectionResult(null, sourceWidth, sourceHeight)
                )
            }
        }
    }
    
    fun close() {
        Log.d(TAG, "Analyzer closed")
    }
}
